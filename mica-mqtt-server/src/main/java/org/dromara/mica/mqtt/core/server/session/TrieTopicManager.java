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

import org.dromara.mica.mqtt.codec.MqttCodecUtil;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.tio.utils.hutool.CollUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * 前缀树
 *
 * @author L.cm
 */
public class TrieTopicManager {

	/**
	 * 较大的 qos
	 */
	public static final BinaryOperator<Byte> MAX_QOS = (a, b) -> (a > b) ? a : b;
	/**
	 * root 节点
	 */
	private final Node root = Node.getRoot("root");
	/**
	 * share 分组
	 */
	private final Map<String, Node> share = new ConcurrentHashMap<>();
	/**
	 * queue 分组
	 */
	private final Node queue = Node.getRoot("$queue");

	private static class Node {
		/**
		 * topic 片段
		 */
		private final String part;
		/**
		 * 订阅的数据存储 {clientId: qos}
		 */
		private final Map<String, Byte> subscriptions;
		/**
		 * 子节点
		 */
		private final Map<String, Node> children;

		private Node(String part, Map<String, Byte> subscriptions, Map<String, Node> children) {
			this.part = part;
			this.subscriptions = subscriptions;
			this.children = children;
		}

		/**
		 * 获取 root node
		 *
		 * @param name name
		 * @return root node
		 */
		protected static Node getRoot(String name) {
			return new Node(name, null, new ConcurrentHashMap<>(8));
		}

		/**
		 * 用于存储数据的节点
		 *
		 * @param part part
		 * @return node
		 */
		protected static Node getNode(String part) {
			return new Node(part, new ConcurrentHashMap<>(16), new ConcurrentHashMap<>(16));
		}

		/**
		 * 获取或者添加节点
		 *
		 * @param nodePart nodePart
		 * @return Node
		 */
		protected Node addChildIfAbsent(String nodePart) {
			assert children != null;
			return CollUtil.computeIfAbsent(this.children, nodePart, Node::getNode);
		}

		protected Node findNodeByPart(String nodePart) {
			assert children != null;
			return children.get(nodePart);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || Node.class != o.getClass() || part == null) {
				return false;
			}
			return part.equals(((Node) o).part);
		}

		@Override
		public int hashCode() {
			return part == null ? 0 : part.hashCode();
		}

		@Override
		public String toString() {
			return "Node{part='" + part + "'}";
		}
	}

	/**
	 * 添加订阅
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    clientId
	 * @param mqttQoS     mqttQoS
	 */
	public void addSubscribe(String topicFilter, String clientId, int mqttQoS) {
		addSubscribe(new TopicFilter(topicFilter), clientId, (short) mqttQoS);
	}

	/**
	 * 添加订阅
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    clientId
	 * @param mqttQoS     mqttQoS
	 */
	public void addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS) {
		String topic = topicFilter.getTopic();
		TopicFilterType topicFilterType = topicFilter.getType();
		if (TopicFilterType.NONE == topicFilterType) {
			addSubscribe(root, topic, clientId, (byte) mqttQoS);
		} else if (TopicFilterType.QUEUE == topicFilterType) {
			int prefixLen = TopicFilterType.SHARE_QUEUE_PREFIX.length();
			addSubscribe(queue, topic.substring(prefixLen), clientId, (byte) mqttQoS);
		} else if (TopicFilterType.SHARE == topicFilterType) {
			int prefixLen = TopicFilterType.SHARE_GROUP_PREFIX.length();
			String groupName = TopicFilterType.getShareGroupName(topic);
			Node groupNode = share.computeIfAbsent(groupName, Node::getNode);
			prefixLen = prefixLen + groupName.length() + 1;
			addSubscribe(groupNode, topic.substring(prefixLen), clientId, (byte) mqttQoS);
		}
	}

	/**
	 * 添加订阅
	 *
	 * @param node        node
	 * @param topicFilter topicFilter
	 * @param clientId    clientId
	 * @param mqttQoS     mqttQoS
	 */
	private static void addSubscribe(Node node, String topicFilter, String clientId, byte mqttQoS) {
		Node prev = node;
		String[] topicParts = TopicUtil.getTopicParts(topicFilter);
		int partLength = topicParts.length - 1;
		for (int i = 0; i < topicParts.length; i++) {
			prev = prev.addChildIfAbsent(topicParts[i]);
			// 判断是否结尾，添加订阅数据
			boolean isEnd = i == partLength;
			if (isEnd) {
				// 如果不存在或者老的订阅 qos 比较小也重新设置
				assert prev.subscriptions != null;
				Byte existingQos = prev.subscriptions.get(clientId);
				if (existingQos == null || existingQos < mqttQoS) {
					prev.subscriptions.put(clientId, mqttQoS);
				}
			}
		}
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
			removeSubscribe(root, topic, clientId);
		} else if (TopicFilterType.QUEUE == topicFilterType) {
			int prefixLen = TopicFilterType.SHARE_QUEUE_PREFIX.length();
			removeSubscribe(queue, topic.substring(prefixLen), clientId);
		} else if (TopicFilterType.SHARE == topicFilterType) {
			int prefixLen = TopicFilterType.SHARE_GROUP_PREFIX.length();
			String groupName = TopicFilterType.getShareGroupName(topic);
			Node groupNode = share.computeIfAbsent(groupName, Node::getNode);
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
	private static void removeSubscribe(Node node, String topicFilter, String clientId) {
		Node prev = node;
		String[] topicParts = TopicUtil.getTopicParts(topicFilter);
		for (String part : topicParts) {
			Node nodePart = prev.findNodeByPart(part);
			if (nodePart != null) {
				prev = nodePart;
			} else {
				prev = null;
				break;
			}
		}
		// 找到则取消订阅
		if (prev != null) {
			assert prev.subscriptions != null;
			prev.subscriptions.remove(clientId);
		}
	}

	/**
	 * 根据 clientId 删除客户端的所以订阅
	 *
	 * @param clientId clientId
	 */
	public void removeSubscribe(String clientId) {
		removeSubscribe(root, clientId);
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
		// 删除订阅
		assert child.subscriptions != null;
		child.subscriptions.remove(clientId);
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
		List<Subscribe> subscribeList = getSubscriptions(root, null, clientId);
		subscribeList.addAll(getSubscriptions(queue, TopicFilterType.SHARE_QUEUE_PREFIX, clientId));
		for (Map.Entry<String, Node> entry : share.entrySet()) {
			String prefix = TopicFilterType.SHARE_GROUP_PREFIX + entry.getKey() + TopicUtil.TOPIC_LAYER;
			subscribeList.addAll(getSubscriptions(entry.getValue(), prefix, clientId));
		}
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
		for (Node child : node.children.values()) {
			String topicPrefix = prefix == null ? child.part : prefix + child.part;
			getSubscribeRecursively(subscribeList, child, topicPrefix, clientId);
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
		// 删除订阅
		assert child.subscriptions != null;
		Byte qos = child.subscriptions.get(clientId);
		if (qos != null) {
			subscribeList.add(new Subscribe(childPart, clientId, qos));
		}
		assert child.children != null;
		for (Node node : child.children.values()) {
			// 拼接订阅的 topic，存储时没存，可以减少内存占用。
			String topicPrefix = isNotNeedAppendTopicLayer(childPart, node.part) ?
				childPart + node.part : childPart + MqttCodecUtil.TOPIC_LAYER + node.part;
			getSubscribeRecursively(subscribeList, node, topicPrefix, clientId);
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
		Map<String, Byte> subscribeMap = new HashMap<>(32);
		searchSubscribeRecursively(root, subscribeMap, topicParts, 0);
		Byte qos = subscribeMap.get(clientId);
		if (qos != null) {
			return qos;
		}
		searchSubscribeRecursively(queue, subscribeMap, topicParts, 0);
		qos = subscribeMap.get(clientId);
		if (qos != null) {
			return qos;
		}
		// 共享订阅
		for (Node node : share.values()) {
			searchSubscribeRecursively(node, subscribeMap, topicParts, 0);
		}
		return subscribeMap.get(clientId);
	}

	/**
	 * 查找订阅信息
	 *
	 * @param topicName topicName
	 * @return 订阅存储列表
	 */
	public List<Subscribe> searchSubscribe(String topicName) {
		String[] topicParts = TopicUtil.getTopicParts(topicName);
		Map<String, Byte> subscribeMap = new HashMap<>(32);
		searchSubscribeRecursively(root, subscribeMap, topicParts, 0);
		// 共享订阅
		Map<String, Byte> queueSubscribeMap = new HashMap<>(8);
		searchSubscribeRecursively(queue, queueSubscribeMap, topicParts, 0);
		if (!queueSubscribeMap.isEmpty()) {
			randomStrategy(subscribeMap, queueSubscribeMap);
		}
		// 分组订阅
		for (Node node : share.values()) {
			Map<String, Byte> shareSubscribeMap = new HashMap<>(8);
			searchSubscribeRecursively(node, shareSubscribeMap, topicParts, 0);
			if (!shareSubscribeMap.isEmpty()) {
				randomStrategy(subscribeMap, shareSubscribeMap);
			}
		}
		// 转换，排重
		List<Subscribe> subscribeList = new ArrayList<>();
		subscribeMap.forEach((clientId, qos) -> subscribeList.add(new Subscribe(clientId, qos)));
		subscribeMap.clear();
		return subscribeList;
	}

	/**
	 * 递归查找
	 *
	 * @param node         node
	 * @param subscribeMap subscribeMap
	 * @param topicParts   topicParts
	 * @param index        index
	 */
	private static void searchSubscribeRecursively(Node node, Map<String, Byte> subscribeMap, String[] topicParts, int index) {
		// 层级已经超过，跳出
		if (index >= topicParts.length) {
			return;
		}
		// # 单独处理
		Node nodeMore = node.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_MORE);
		if (nodeMore != null) {
			for (Map.Entry<String, Byte> entry : nodeMore.subscriptions.entrySet()) {
				subscribeMap.merge(entry.getKey(), entry.getValue(), MAX_QOS);
			}
		}
		int topicPartLen = topicParts.length - 1;
		// + 处理
		Node nodeOne = node.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_ONE);
		if (nodeOne != null) {
			// 最后一位为 +
			if (index == topicPartLen) {
				for (Map.Entry<String, Byte> entry : nodeOne.subscriptions.entrySet()) {
					subscribeMap.merge(entry.getKey(), entry.getValue(), MAX_QOS);
				}
			} else {
				searchSubscribeRecursively(nodeOne, subscribeMap, topicParts, index + 1);
			}
		}
		String topicPart = topicParts[index];
		Node nodePart = node.findNodeByPart(topicPart);
		if (nodePart != null) {
			// 跳出循环
			if (index == topicPartLen) {
				for (Map.Entry<String, Byte> entry : nodePart.subscriptions.entrySet()) {
					subscribeMap.merge(entry.getKey(), entry.getValue(), MAX_QOS);
				}
				// 判断是否还有 #
				Node nodePartMore = nodePart.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_MORE);
				if (nodePartMore != null) {
					for (Map.Entry<String, Byte> entry : nodePartMore.subscriptions.entrySet()) {
						subscribeMap.merge(entry.getKey(), entry.getValue(), MAX_QOS);
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
		// 清理普通订阅
		root.children.clear();
		// 清理共享订阅
		queue.children.clear();
		// 清理分组共享订阅
		share.clear();
	}

	@Override
	public String toString() {
		return "TrieTopicManager{" +
			"root=" + root +
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
	private static void randomStrategy(Map<String, Byte> subscribeMap, Map<String, Byte> randomSubscribeMap) {
		String[] keys = randomSubscribeMap.keySet().toArray(new String[0]);
		int keyLength = keys.length;
		// 大于 1 随机
		String key = keyLength > 1 ? keys[ThreadLocalRandom.current().nextInt(keyLength)] : keys[0];
		subscribeMap.merge(key, randomSubscribeMap.get(key), MAX_QOS);
	}

}
