/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.core.server.session;

import net.dreamlu.mica.net.utils.hutool.CollUtil;
import org.dromara.mica.mqtt.codec.MqttCodecUtil;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.util.TopicUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * 混合订阅管理：非通配 topic 使用 Map 直存，通配 topic 与共享订阅使用前缀树。
 *
 * @author L.cm
 */
public class TrieTopicManager {
	/**
	 * 前缀树 children 初始容量：每层 literal + + + # 分支有限
	 */
	private static final int CHILDREN_CAPACITY = 4;
	/**
	 * 通配订阅叶子：同一 filter 通常仅少数 client
	 */
	private static final int WILDCARD_SUBSCRIPTIONS_CAPACITY = 4;
	/**
	 * 共享订阅叶子：同组同 topic 可挂载大量 client
	 */
	private static final int SHARE_SUBSCRIPTIONS_CAPACITY = 16;
	/**
	 * 较大的 qos
	 */
	public static final BinaryOperator<Byte> MAX_QOS = (a, b) -> (a > b) ? a : b;
	/**
	 * 非通配普通订阅：topicFilter -> {clientId: encoded_byte}
	 * 生产环境精确 topic 占绝大多数，Map 直存避免前缀树每层 Node 的双 Map 开销
	 */
	private final Map<String, Map<String, Byte>> exactSubscriptions = new ConcurrentHashMap<>();
	/**
	 * 含 + / # 的普通订阅前缀树（exactSubscriptions 放不下的才进这里）
	 */
	private final Node wildcardRoot = Node.getRoot();
	/**
	 * $share/{group}/ 分组共享订阅，每组独立一棵前缀树
	 */
	private final Map<String, Node> share = new ConcurrentHashMap<>();
	/**
	 * $queue/ 无分组共享订阅前缀树
	 */
	private final Node queue = Node.getRoot();

	private static class Node {
		/**
		 * 订阅的数据存储 {clientId: encoded_byte}
		 * 编码格式: bit 0-1: qos，bit 2: noLocal，bit 3: retainAsPublished，bit 4-5: retainHandling
		 * 优化说明：
		 * 1. 将全部订阅选项合并到一个 byte，避免为每个订阅创建额外选项对象
		 * 2. byte 值使用 JVM Byte 缓存池，无额外对象创建
		 * 3. 单次 Map 查找代替双次查找，性能提升约 50%
		 */
		private final Map<String, Byte> subscriptions;
		/**
		 * 子节点，key 即为该层 topic 片段（与原 part 字段相同信息）
		 */
		private final Map<String, Node> children;

		private Node(Map<String, Byte> subscriptions, Map<String, Node> children) {
			this.subscriptions = subscriptions;
			this.children = children;
		}

		/**
		 * 获取 root node
		 *
		 * @return root node
		 */
		protected static Node getRoot() {
			// 根节点只作路由入口，不挂载 subscriptions
			return new Node(null, new ConcurrentHashMap<>(CHILDREN_CAPACITY));
		}

		/**
		 * 通配订阅前缀树节点（+ / #）
		 */
		protected static Node getWildcardNode() {
			return new Node(new ConcurrentHashMap<>(WILDCARD_SUBSCRIPTIONS_CAPACITY), new ConcurrentHashMap<>(CHILDREN_CAPACITY));
		}

		/**
		 * $queue / $share 共享订阅前缀树节点
		 */
		protected static Node getShareNode() {
			return new Node(new ConcurrentHashMap<>(SHARE_SUBSCRIPTIONS_CAPACITY), new ConcurrentHashMap<>(CHILDREN_CAPACITY));
		}

		/**
		 * 通配树：逐层创建子节点
		 */
		protected Node addChildIfAbsent(String nodePart) {
			assert children != null;
			return CollUtil.computeIfAbsent(this.children, nodePart, k -> getWildcardNode());
		}

		/**
		 * 共享订阅树：逐层创建子节点（叶子 subscriptions 预留更大容量）
		 */
		protected Node addShareChildIfAbsent(String nodePart) {
			assert children != null;
			return CollUtil.computeIfAbsent(this.children, nodePart, k -> getShareNode());
		}

		protected Node findNodeByPart(String nodePart) {
			assert children != null;
			return children.get(nodePart);
		}
	}

	/**
	 * 添加订阅
	 *
	 * @param topicFilter       topicFilter
	 * @param clientId          clientId
	 * @param mqttQoS           mqttQoS
	 * @param noLocal           MQTT 5.0 No Local 标志
	 * @param retainAsPublished Retain As Published 标志
	 * @param retainHandling    Retain Handling，取值 0、1、2
	 * @return true 表示新增订阅，false 表示替换已有订阅
	 */
	public boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal,
								boolean retainAsPublished, int retainHandling) {
		String topic = topicFilter.getTopic();
		TopicFilterType topicFilterType = topicFilter.getType();
		if (TopicFilterType.NONE == topicFilterType) {
			// 普通订阅按是否含通配符分流：精确 -> Map，通配 -> 前缀树
			if (MqttCodecUtil.isTopicFilter(topic)) {
				return addTrieSubscribe(wildcardRoot, topic, clientId, (byte) mqttQoS, noLocal,
					retainAsPublished, (byte) retainHandling, false);
			}
			Map<String, Byte> subscriptions = CollUtil.computeIfAbsent(this.exactSubscriptions, topic,
				k -> new ConcurrentHashMap<>(CHILDREN_CAPACITY));
			return putSubscription(subscriptions, clientId, (byte) mqttQoS, noLocal,
				retainAsPublished, (byte) retainHandling);
		}
		if (TopicFilterType.QUEUE == topicFilterType) {
			// 共享订阅（含精确后缀）统一走前缀树，便于组内随机负载均衡
			int prefixLen = TopicFilterType.SHARE_QUEUE_PREFIX.length();
			// 去掉 $queue/ 前缀后再写入前缀树
			return addTrieSubscribe(queue, topic.substring(prefixLen), clientId, (byte) mqttQoS,
				noLocal, retainAsPublished, (byte) retainHandling, true);
		}
		if (TopicFilterType.SHARE == topicFilterType) {
			String groupName = TopicFilterType.getShareGroupName(topic);
			Node groupNode = share.computeIfAbsent(groupName, k -> Node.getRoot());
			int prefixLen = TopicFilterType.SHARE_GROUP_PREFIX.length() + groupName.length() + 1;
			// 去掉 $share/{group}/ 前缀后再写入该组的前缀树
			return addTrieSubscribe(groupNode, topic.substring(prefixLen), clientId, (byte) mqttQoS,
				noLocal, retainAsPublished, (byte) retainHandling, true);
		}
		return false;
	}

	/**
	 * 添加订阅到指定前缀树
	 *
	 * @return true 表示新增订阅，false 表示替换已有订阅
	 */
	private static boolean addTrieSubscribe(Node node, String topicFilter, String clientId, byte mqttQoS,
										   boolean noLocal, boolean retainAsPublished, byte retainHandling,
										   boolean shareTree) {
		Node prev = node;
		// 按 / 层级拆分为 part 数组，如 "/a/b" -> ["/", "a", "b"]
		String[] topicParts = TopicUtil.getTopicParts(topicFilter);
		int partLength = topicParts.length - 1;
		for (int i = 0; i < topicParts.length; i++) {
			// 逐层创建或查找子节点，+ / # 也作为普通 part 存储
			prev = shareTree ? prev.addShareChildIfAbsent(topicParts[i]) : prev.addChildIfAbsent(topicParts[i]);
			if (i == partLength) {
				assert prev.subscriptions != null;
				return putSubscription(prev.subscriptions, clientId, mqttQoS, noLocal,
					retainAsPublished, retainHandling);
			}
		}
		return false;
	}

	private static boolean putSubscription(Map<String, Byte> subscriptions, String clientId, byte mqttQoS,
										   boolean noLocal, boolean retainAsPublished, byte retainHandling) {
		SubscribeData data = SubscribeData.of(mqttQoS, noLocal, retainAsPublished, retainHandling);
		return subscriptions.put(clientId, data.encoded) == null;
	}

	/**
	 * 移除订阅
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    clientId
	 */
	public void removeSubscribe(String topicFilter, String clientId) {
		removeSubscribe(new TopicFilter(topicFilter), clientId);
	}

	/**
	 * 移除订阅
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    clientId
	 */
	private void removeSubscribe(TopicFilter topicFilter, String clientId) {
		String topic = topicFilter.getTopic();
		TopicFilterType topicFilterType = topicFilter.getType();
		if (TopicFilterType.NONE == topicFilterType) {
			if (MqttCodecUtil.isTopicFilter(topic)) {
				removeSubscribe(wildcardRoot, topic, clientId);
			} else {
				removeExactSubscribe(topic, clientId);
			}
		} else if (TopicFilterType.QUEUE == topicFilterType) {
			int prefixLen = TopicFilterType.SHARE_QUEUE_PREFIX.length();
			removeSubscribe(queue, topic.substring(prefixLen), clientId);
		} else if (TopicFilterType.SHARE == topicFilterType) {
			int prefixLen = TopicFilterType.SHARE_GROUP_PREFIX.length();
			String groupName = TopicFilterType.getShareGroupName(topic);
			Node groupNode = share.computeIfAbsent(groupName, k -> Node.getRoot());
			prefixLen = prefixLen + groupName.length() + 1;
			removeSubscribe(groupNode, topic.substring(prefixLen), clientId);
		}
	}

	/**
	 * 移除订阅
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    clientId
	 */
	private void removeExactSubscribe(String topicFilter, String clientId) {
		Map<String, Byte> subscriptions = exactSubscriptions.get(topicFilter);
		if (subscriptions == null) {
			return;
		}
		subscriptions.remove(clientId);
		if (subscriptions.isEmpty()) {
			// remove(key, value) 避免并发下误删新写入的同名 topic
			exactSubscriptions.remove(topicFilter, subscriptions);
		}
	}

	private static void removeSubscribe(Node node, String topicFilter, String clientId) {
		Node prev = node;
		String[] topicParts = TopicUtil.getTopicParts(topicFilter);
		// 沿 part 路径定位到叶子节点
		for (String part : topicParts) {
			Node nodePart = prev.findNodeByPart(part);
			if (nodePart != null) {
				prev = nodePart;
			} else {
				prev = null;
				break;
			}
		}
		if (prev != null) {
			assert prev.subscriptions != null;
			prev.subscriptions.remove(clientId);
			// 注意：此处不回收空节点，避免并发订阅时误删仍在使用的路径
		}
	}

	/**
	 * 根据 clientId 删除客户端的所以订阅
	 *
	 * @param clientId clientId
	 */
	public void removeSubscribe(String clientId) {
		// 精确订阅无反向索引，断开连接时扫描 exactMap（冷路径，可接受）
		exactSubscriptions.entrySet().removeIf(entry -> {
			entry.getValue().remove(clientId);
			return entry.getValue().isEmpty();
		});
		removeSubscribe(wildcardRoot, clientId);
		removeSubscribe(queue, clientId);
		for (Node node : share.values()) {
			removeSubscribe(node, clientId);
		}
	}

	/**
	 * 根据 clientId 删除客户端的所以订阅
	 *
	 * @param clientId clientId
	 */
	private static void removeSubscribe(Node node, String clientId) {
		assert node.children != null;
		// 断开连接时遍历整棵子树，清除该 client 在所有节点的订阅
		for (Node child : node.children.values()) {
			removeSubscribeRecursively(child, clientId);
		}
	}

	/**
	 * 递归删除订阅
	 *
	 * @param child    child
	 * @param clientId clientId
	 */
	private static void removeSubscribeRecursively(Node child, String clientId) {
		if (child.subscriptions != null) {
			child.subscriptions.remove(clientId);
		}
		assert child.children != null;
		for (Node node : child.children.values()) {
			removeSubscribeRecursively(node, clientId);
		}
	}

	/**
	 * 获取客户端所以订阅
	 *
	 * @param clientId clientId
	 * @return 订阅集合
	 */
	public List<Subscribe> getSubscriptions(String clientId) {
		List<Subscribe> subscribeList = new ArrayList<>();
		// 精确订阅：遍历 exactMap，key 即 topicFilter，无需前缀树拼接
		for (Map.Entry<String, Map<String, Byte>> entry : exactSubscriptions.entrySet()) {
			Byte encoded = entry.getValue().get(clientId);
			if (encoded != null) {
				SubscribeData data = SubscribeData.decode(encoded);
				subscribeList.add(new Subscribe(entry.getKey(), clientId, data.qos, data.noLocal,
					data.retainAsPublished, data.retainHandling));
			}
		}
		subscribeList.addAll(getSubscriptions(wildcardRoot, null, clientId));
		subscribeList.addAll(getSubscriptions(queue, TopicFilterType.SHARE_QUEUE_PREFIX, clientId));
		for (Map.Entry<String, Node> entry : share.entrySet()) {
			// 还原 $share/{group}/ 前缀，拼接完整 topicFilter
			String prefix = TopicFilterType.SHARE_GROUP_PREFIX + entry.getKey() + TopicUtil.TOPIC_LAYER;
			subscribeList.addAll(getSubscriptions(entry.getValue(), prefix, clientId));
		}
		// 通配与共享路径可能产生重复，去重后返回
		return subscribeList.stream().distinct().collect(Collectors.toList());
	}

	/**
	 * 获取客户端所以订阅
	 *
	 * @param clientId clientId
	 * @return 订阅集合
	 */
	private static List<Subscribe> getSubscriptions(Node node, String prefix, String clientId) {
		List<Subscribe> subscribeList = new ArrayList<>();
		for (Map.Entry<String, Node> entry : node.children.entrySet()) {
			String childPart = entry.getKey();
			// prefix 为 null 表示普通通配树根，首层 part 即为 topic 起始片段
			String topicPrefix = prefix == null ? childPart : prefix + childPart;
			getSubscribeRecursively(subscribeList, entry.getValue(), topicPrefix, clientId);
		}
		return subscribeList;
	}

	/**
	 * 递归获取订阅
	 *
	 * @param child    child
	 * @param clientId clientId
	 */
	private static void getSubscribeRecursively(List<Subscribe> subscribeList, Node child, String childPart, String clientId) {
		if (child.subscriptions != null) {
			Byte encoded = child.subscriptions.get(clientId);
			if (encoded != null) {
				SubscribeData data = SubscribeData.decode(encoded);
				// childPart 为递归拼接的 topicFilter，存储时未冗余保存完整字符串
				subscribeList.add(new Subscribe(childPart, clientId, data.qos, data.noLocal,
					data.retainAsPublished, data.retainHandling));
			}
		}
		assert child.children != null;
		for (Map.Entry<String, Node> entry : child.children.entrySet()) {
			String nodePartStr = entry.getKey();
			// 处理 leading/trailing / 等边界，避免拼接出错误 topic
			String topicPrefix = isNotNeedAppendTopicLayer(childPart, nodePartStr) ?
				childPart + nodePartStr : childPart + MqttCodecUtil.TOPIC_LAYER + nodePartStr;
			getSubscribeRecursively(subscribeList, entry.getValue(), topicPrefix, clientId);
		}
	}

	/**
	 * 判断是否需要添加层级
	 *
	 * @param prefix prefix
	 * @param suffix suffix
	 * @return 是否需要添加层级
	 */
	private static boolean isNotNeedAppendTopicLayer(String prefix, String suffix) {
		// 如 prefix="/" 或 suffix="/" 时，相邻层级本身已含 /，无需再插入分隔符
		return TopicUtil.TOPIC_LAYER.equals(prefix) || prefix.endsWith("//") || TopicUtil.TOPIC_LAYER.equals(suffix);
	}

	/**
	 * 查找订阅 qos 信息
	 *
	 * @param topicName topicName
	 * @param clientId  客户端 Id
	 * @return 订阅存储列表
	 */
	public Byte searchSubscribe(String topicName, String clientId) {
		String[] topicParts = TopicUtil.getTopicParts(topicName);
		Map<String, SubscribeData> subscribeMap = new HashMap<>(32);
		// 发布热路径：先 O(1) 查精确订阅，再递归匹配通配与共享
		mergeExactSubscriptions(topicName, subscribeMap);
		searchSubscribeRecursively(wildcardRoot, subscribeMap, topicParts, 0);
		SubscribeData data = subscribeMap.get(clientId);
		if (data != null) {
			return data.qos;
		}
		// 依次查找 $queue 与各 $share 组，命中即返回（用于 PUBACK QoS 确认）
		searchSubscribeRecursively(queue, subscribeMap, topicParts, 0);
		data = subscribeMap.get(clientId);
		if (data != null) {
			return data.qos;
		}
		for (Node node : share.values()) {
			searchSubscribeRecursively(node, subscribeMap, topicParts, 0);
		}
		data = subscribeMap.get(clientId);
		return data != null ? data.qos : null;
	}

	/**
	 * 查找订阅信息
	 *
	 * @param topicName topicName
	 * @return 订阅存储列表
	 */
	public List<Subscribe> searchSubscribe(String topicName) {
		String[] topicParts = TopicUtil.getTopicParts(topicName);
		Map<String, SubscribeData> subscribeMap = new HashMap<>(32);
		mergeExactSubscriptions(topicName, subscribeMap);
		searchSubscribeRecursively(wildcardRoot, subscribeMap, topicParts, 0);
		// $queue 共享：组内多 client 随机选一个
		Map<String, SubscribeData> queueSubscribeMap = new HashMap<>(8);
		searchSubscribeRecursively(queue, queueSubscribeMap, topicParts, 0);
		if (!queueSubscribeMap.isEmpty()) {
			randomStrategy(subscribeMap, queueSubscribeMap);
		}
		// 分组订阅：每组独立随机选一个 client
		for (Node node : share.values()) {
			Map<String, SubscribeData> shareSubscribeMap = new HashMap<>(8);
			searchSubscribeRecursively(node, shareSubscribeMap, topicParts, 0);
			if (!shareSubscribeMap.isEmpty()) {
				randomStrategy(subscribeMap, shareSubscribeMap);
			}
		}
		List<Subscribe> subscribeList = new ArrayList<>();
		subscribeMap.forEach((clientId, data) -> subscribeList.add(new Subscribe(clientId, data.qos, data.noLocal,
			data.retainAsPublished, data.retainHandling)));
		subscribeMap.clear();
		return subscribeList;
	}

	/**
	 * 合并精确订阅到结果集（topicName 与 topicFilter 完全一致时才命中）
	 *
	 * @param topicName    发布的 topic，不含通配符
	 * @param subscribeMap 待合并的结果集
	 */
	private void mergeExactSubscriptions(String topicName, Map<String, SubscribeData> subscribeMap) {
		Map<String, Byte> subscriptions = exactSubscriptions.get(topicName);
		if (subscriptions == null || subscriptions.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Byte> entry : subscriptions.entrySet()) {
			SubscribeData data = SubscribeData.decode(entry.getValue());
			subscribeMap.merge(entry.getKey(), data, TrieTopicManager::mergeSubscribeData);
		}
	}

	/**
	 * 合并同一 client 的多条匹配订阅（如同时命中精确 topic 与通配 filter）
	 */
	private static SubscribeData mergeSubscribeData(SubscribeData old, SubscribeData val) {
		byte maxQos = MAX_QOS.apply(old.qos, val.qos);
		// 任一匹配订阅允许本地消息时就必须投递；任一要求 RAP 时保留原 RETAIN 标志。
		boolean mergedNoLocal = old.noLocal && val.noLocal;
		boolean mergedRetainAsPublished = old.retainAsPublished || val.retainAsPublished;
		return SubscribeData.of(maxQos, mergedNoLocal, mergedRetainAsPublished, old.retainHandling);
	}

	private static void searchSubscribeRecursively(Node node, Map<String, SubscribeData> subscribeMap, String[] topicParts, int index) {
		if (index >= topicParts.length) {
			return;
		}
		// # 匹配当前层级及后续所有层级，无需继续向下递归
		Node nodeMore = node.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_MORE);
		if (nodeMore != null && nodeMore.subscriptions != null) {
			for (Map.Entry<String, Byte> entry : nodeMore.subscriptions.entrySet()) {
				SubscribeData data = SubscribeData.decode(entry.getValue());
				subscribeMap.merge(entry.getKey(), data, TrieTopicManager::mergeSubscribeData);
			}
		}
		int topicPartLen = topicParts.length - 1;
		// + 匹配当前层级任意一个 part
		Node nodeOne = node.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_ONE);
		if (nodeOne != null) {
			if (index == topicPartLen) {
				// + 在 filter 末尾：匹配 topic 的最后一个 part
				if (nodeOne.subscriptions != null) {
					for (Map.Entry<String, Byte> entry : nodeOne.subscriptions.entrySet()) {
						SubscribeData data = SubscribeData.decode(entry.getValue());
						subscribeMap.merge(entry.getKey(), data, TrieTopicManager::mergeSubscribeData);
					}
				}
			} else {
				// + 在中间：跳过当前 part，继续匹配下一层
				searchSubscribeRecursively(nodeOne, subscribeMap, topicParts, index + 1);
			}
		}
		String topicPart = topicParts[index];
		Node nodePart = node.findNodeByPart(topicPart);
		if (nodePart != null) {
			if (index == topicPartLen) {
				// 精确 part 匹配到 topic 末尾，收集该节点上的订阅
				if (nodePart.subscriptions != null) {
					for (Map.Entry<String, Byte> entry : nodePart.subscriptions.entrySet()) {
						SubscribeData data = SubscribeData.decode(entry.getValue());
						subscribeMap.merge(entry.getKey(), data, TrieTopicManager::mergeSubscribeData);
					}
				}
				// 同时检查末尾 # 子节点，如 filter "a/b/#" 匹配 topic "a/b"
				Node nodePartMore = nodePart.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_MORE);
				if (nodePartMore != null && nodePartMore.subscriptions != null) {
					for (Map.Entry<String, Byte> entry : nodePartMore.subscriptions.entrySet()) {
						SubscribeData data = SubscribeData.decode(entry.getValue());
						subscribeMap.merge(entry.getKey(), data, TrieTopicManager::mergeSubscribeData);
					}
				}
			} else {
				searchSubscribeRecursively(nodePart, subscribeMap, topicParts, index + 1);
			}
		}
	}

	/**
	 * 清理
	 */
	public void clear() {
		exactSubscriptions.clear();
		wildcardRoot.children.clear();
		queue.children.clear();
		share.clear();
	}

	@Override
	public String toString() {
		return "TrieTopicManager{" +
			"exactSubscriptions=" + exactSubscriptions.size() +
			", wildcardRoot=" + wildcardRoot +
			", share=" + share +
			", queue=" + queue +
			'}';
	}

	/**
	 * 负载均衡策略：随机方式
	 *
	 * @param subscribeMap       订阅的 map
	 * @param randomSubscribeMap 分组订阅的 map
	 */
	private static void randomStrategy(Map<String, SubscribeData> subscribeMap, Map<String, SubscribeData> randomSubscribeMap) {
		String[] keys = randomSubscribeMap.keySet().toArray(new String[0]);
		int keyLength = keys.length;
		// 共享订阅语义：同一组内每条消息只投递给一个 client
		String key = keyLength > 1 ? keys[ThreadLocalRandom.current().nextInt(keyLength)] : keys[0];
		SubscribeData data = randomSubscribeMap.get(key);
		// 若该 client 同时有普通订阅，merge 取较大 QoS
		subscribeMap.merge(key, data, TrieTopicManager::mergeSubscribeData);
	}

}
