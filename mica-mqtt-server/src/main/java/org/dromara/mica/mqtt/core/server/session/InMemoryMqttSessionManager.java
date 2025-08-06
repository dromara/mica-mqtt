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

import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.tio.utils.collection.IntObjectHashMap;
import org.tio.utils.collection.IntObjectMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;

/**
 * 内存 session 管理
 *
 * @author L.cm
 */
public class InMemoryMqttSessionManager implements IMqttSessionManager {
	/**
	 * 较大的 qos
	 */
	public static final BinaryOperator<Integer> MAX_QOS = Math::max;
	/**
	 * messageId 存储 clientId: messageId
	 */
	private final Map<String, AtomicInteger> messageIdStore = new ConcurrentHashMap<>();
	/**
	 * 订阅存储 topicFilter: {clientId: qos}
	 */
	private final Map<String, Map<String, Integer>> subscribeStore = new ConcurrentHashMap<>();
	/**
	 * 共享订阅存储 queueTopicFilter: {clientId: qos}
	 */
	private final Map<String, Map<String, Integer>> queueSubscribeStore = new ConcurrentHashMap<>();
	/**
	 * 分组订阅存储 groupName : {shareTopicFilter : {clientId: qos}}
	 */
	private final Map<String, Map<String, Map<String, Integer>>> shareSubscribeStore = new ConcurrentHashMap<>();
	/**
	 * qos1 消息过程存储 clientId: {msgId: Object}
	 */
	private final Map<String, IntObjectMap<MqttPendingPublish>> pendingPublishStore = new ConcurrentHashMap<>();
	/**
	 * qos2 消息过程存储 clientId: {msgId: Object}
	 */
	private final Map<String, IntObjectMap<MqttPendingQos2Publish>> pendingQos2PublishStore = new ConcurrentHashMap<>();

	@Override
	public void addSubscribe(String topicFilter, String clientId, int mqttQoS) {
		Map<String, Integer> data;
		TopicFilterType filterType = TopicFilterType.getType(topicFilter);
		if (TopicFilterType.QUEUE == filterType) {
			data = queueSubscribeStore.computeIfAbsent(topicFilter, (key) -> new ConcurrentHashMap<>(16));
		} else if (TopicFilterType.SHARE == filterType) {
			String name = TopicFilterType.getShareGroupName(topicFilter);
			Map<String, Map<String, Integer>> shareSubscribeMap = shareSubscribeStore.computeIfAbsent(name, (key) -> new ConcurrentHashMap<>(16));
			data = shareSubscribeMap.computeIfAbsent(topicFilter, (key) -> new ConcurrentHashMap<>(16));
		} else {
			data = subscribeStore.computeIfAbsent(topicFilter, (key) -> new ConcurrentHashMap<>(32));
		}
		// 如果不存在或者老的订阅 qos 比较小也重新设置
		Integer existingQos = data.get(clientId);
		if (existingQos == null || existingQos < mqttQoS) {
			data.put(clientId, mqttQoS);
		}
	}

	@Override
	public void removeSubscribe(String topicFilter, String clientId) {
		Map<String, Integer> map;
		TopicFilterType filterType = TopicFilterType.getType(topicFilter);
		if (filterType == TopicFilterType.NONE) {
			map = subscribeStore.get(topicFilter);
		} else if (filterType == TopicFilterType.QUEUE) {
			map = queueSubscribeStore.get(topicFilter);
		} else {
			String groupName = TopicFilterType.getShareGroupName(topicFilter);
			Map<String, Map<String, Integer>> groupMap = shareSubscribeStore.get(groupName);
			if (groupMap == null) {
				return;
			}
			map = groupMap.get(topicFilter);
		}
		if (map != null) {
			map.remove(clientId);
		}
	}

	@Override
	public Integer searchSubscribe(String topicName, String clientId) {
		// 服务端发布时查找是否有订阅，只要证明有订阅即可
		// 1. 如果订阅的就是普通的 topic
		Map<String, Integer> subscribeData = subscribeStore.get(topicName);
		if (subscribeData != null && !subscribeData.isEmpty()) {
			Integer qos = subscribeData.get(clientId);
			if (qos != null) {
				return qos;
			}
		}
		// 2. 如果订阅的是通配符
		Integer subscribeQos = searchSubscribeQos(topicName, clientId, subscribeStore, TopicFilterType.NONE);
		if (subscribeQos != null) {
			return subscribeQos;
		}
		// 3. 共享订阅
		subscribeQos = searchSubscribeQos(topicName, clientId, queueSubscribeStore, TopicFilterType.QUEUE);
		if (subscribeQos != null) {
			return subscribeQos;
		}
		// 4. 分组订阅
		for (Map<String, Map<String, Integer>> shareSubscribeStoreMap : shareSubscribeStore.values()) {
			subscribeQos = searchSubscribeQos(topicName, clientId, shareSubscribeStoreMap, TopicFilterType.SHARE);
			if (subscribeQos != null) {
				return subscribeQos;
			}
		}
		return null;
	}

	private static Integer searchSubscribeQos(String topicName, String clientId,
											  Map<String, Map<String, Integer>> subscribeStoreMap,
											  TopicFilterType filterType) {
		Integer qosValue = null;
		Set<String> topicFilterSet = subscribeStoreMap.keySet();
		for (String topicFilter : topicFilterSet) {
			if (filterType.match(topicFilter, topicName)) {
				Map<String, Integer> data = subscribeStoreMap.get(topicFilter);
				if (data != null && !data.isEmpty()) {
					Integer mqttQoS = data.get(clientId);
					if (mqttQoS != null) {
						if (qosValue == null) {
							qosValue = mqttQoS;
						} else {
							qosValue = MAX_QOS.apply(qosValue, mqttQoS);
						}
					}
				}
			}
		}
		return qosValue;
	}

	/**
	 * 获取订阅列表  共享订阅
	 */
	private static Map<String, Integer> getQueueSubscribeMap(Map<String, Map<String, Integer>> subscribeStore, TopicFilterType filterType, String topicName) {
		// 排除重复订阅，例如： /test/# 和 /# 只发一份
		Map<String, Integer> subscribeMap = new HashMap<>(32);
		Set<String> topicFilterSet = subscribeStore.keySet();
		for (String topicFilter : topicFilterSet) {
			if (filterType.match(topicFilter, topicName)) {
				Map<String, Integer> data = subscribeStore.get(topicFilter);
				if (data != null && !data.isEmpty()) {
					data.forEach((clientId, qos) -> subscribeMap.merge(clientId, qos, MAX_QOS));
				}
			}
		}
		return subscribeMap;
	}

	/**
	 * 获取订阅列表  分组订阅
	 */
	private static Map<String, Map<String, Integer>> getShareSubscribeMap(Map<String, Map<String, Map<String, Integer>>> shareSubscribeStore, TopicFilterType filterType, String topicName) {
		// 排除重复订阅，例如： /test/# 和 /# 只发一份
		Map<String, Map<String, Integer>> shareSubscribeMap = new HashMap<>(32);
		for (Map.Entry<String, Map<String, Map<String, Integer>>> entry : shareSubscribeStore.entrySet()) {
			String entryKey = entry.getKey();
			Map<String, Map<String, Integer>> entryValue = entry.getValue();
			Map<String, Integer> map = getQueueSubscribeMap(entryValue, filterType, topicName);
			if (map != null && !map.isEmpty()) {
				shareSubscribeMap.put(entryKey, map);
			}
		}
		return shareSubscribeMap;
	}

	/**
	 * 负载均衡策略：随机方式
	 */
	private static void randomStrategy(Map<String, Integer> subscribeMap, Map<String, Integer> randomSubscribeMap) {
		String[] keys = randomSubscribeMap.keySet().toArray(new String[0]);
		Random random = ThreadLocalRandom.current();
		String key = keys[random.nextInt(keys.length)];
		subscribeMap.merge(key, randomSubscribeMap.get(key), MAX_QOS);
	}

	@Override
	public List<Subscribe> searchSubscribe(String topicName) {
		// 排除重复订阅，例如： /test/# 和 /# 只发一份
		Map<String, Integer> subscribeMap = new HashMap<>(32);
		Set<String> topicFilterSet = subscribeStore.keySet();
		for (String topicFilter : topicFilterSet) {
			if (TopicUtil.match(topicFilter, topicName)) {
				Map<String, Integer> data = subscribeStore.get(topicFilter);
				if (data != null && !data.isEmpty()) {
					data.forEach((clientId, qos) -> subscribeMap.merge(clientId, qos, MAX_QOS));
				}
			}
		}
		//获取共享订阅列表 queueSubscribeMap
		Map<String, Integer> queueSubscribeMap = getQueueSubscribeMap(queueSubscribeStore, TopicFilterType.QUEUE, topicName);
		//将依据负载均衡策略选出的客户端放到 subscribeMap
		if (!queueSubscribeMap.isEmpty()) {
			randomStrategy(subscribeMap, queueSubscribeMap);
		}
		//获取分组订阅列表 shareSubscribeMap 并与subscribeMap合并
		Map<String, Map<String, Integer>> shareSubscribeMap = getShareSubscribeMap(shareSubscribeStore, TopicFilterType.SHARE, topicName);
		if (!shareSubscribeMap.isEmpty()) {
			for (Map<String, Integer> value : shareSubscribeMap.values()) {
				randomStrategy(subscribeMap, value);
			}
		}
		List<Subscribe> subscribeList = new ArrayList<>();
		subscribeMap.forEach((clientId, qos) -> subscribeList.add(new Subscribe(clientId, qos)));
		subscribeMap.clear();
		return subscribeList;
	}

	@Override
	public List<Subscribe> getSubscriptions(String clientId) {
		List<Subscribe> subscribeList = new ArrayList<>();
		// 普通订阅
		getSubscriptions(subscribeList, subscribeStore, clientId);
		// 共享订阅 queueSubscribeStore
		getSubscriptions(subscribeList, queueSubscribeStore, clientId);
		// 分组订阅 shareSubscribeStore
		for (Map<String, Map<String, Integer>> shareSubscribeMap : shareSubscribeStore.values()) {
			getSubscriptions(subscribeList, shareSubscribeMap, clientId);
		}
		return subscribeList;
	}

	private static void getSubscriptions(List<Subscribe> subscribeList,
										 Map<String, Map<String, Integer>> subscribeStoreMap,
										 String clientId) {
		Set<Map.Entry<String, Map<String, Integer>>> entrySet = subscribeStoreMap.entrySet();
		for (Map.Entry<String, Map<String, Integer>> mapEntry : entrySet) {
			Map<String, Integer> mapEntryValue = mapEntry.getValue();
			if (mapEntryValue == null || mapEntryValue.isEmpty()) {
				continue;
			}
			Integer qos = mapEntryValue.get(clientId);
			if (qos != null) {
				String topicFilter = mapEntry.getKey();
				subscribeList.add(new Subscribe(topicFilter, clientId, qos));
			}
		}
	}

	@Override
	public void addPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish) {
		IntObjectMap<MqttPendingPublish> data = pendingPublishStore.computeIfAbsent(clientId, (key) -> new IntObjectHashMap<>(16));
		data.put(messageId, pendingPublish);
	}

	@Override
	public MqttPendingPublish getPendingPublish(String clientId, int messageId) {
		IntObjectMap<MqttPendingPublish> data = pendingPublishStore.get(clientId);
		if (data == null) {
			return null;
		}
		return data.get(messageId);
	}

	@Override
	public void removePendingPublish(String clientId, int messageId) {
		IntObjectMap<MqttPendingPublish> data = pendingPublishStore.get(clientId);
		if (data != null) {
			data.remove(messageId);
		}
	}

	@Override
	public void addPendingQos2Publish(String clientId, int messageId, MqttPendingQos2Publish pendingQos2Publish) {
		IntObjectMap<MqttPendingQos2Publish> data = pendingQos2PublishStore.computeIfAbsent(clientId, (key) -> new IntObjectHashMap<>());
		data.put(messageId, pendingQos2Publish);
	}

	@Override
	public MqttPendingQos2Publish getPendingQos2Publish(String clientId, int messageId) {
		IntObjectMap<MqttPendingQos2Publish> data = pendingQos2PublishStore.get(clientId);
		if (data == null) {
			return null;
		}
		return data.get(messageId);
	}

	@Override
	public void removePendingQos2Publish(String clientId, int messageId) {
		IntObjectMap<MqttPendingQos2Publish> data = pendingQos2PublishStore.get(clientId);
		if (data != null) {
			data.remove(messageId);
		}
	}

	@Override
	public int getPacketId(String clientId) {
		AtomicInteger packetIdGen = messageIdStore.computeIfAbsent(clientId, (key) -> new AtomicInteger(1));
		return packetIdGen.getAndUpdate(current -> (current % 0xffff) == 0 ? 1 : current + 1);
	}

	@Override
	public boolean hasSession(String clientId) {
		return pendingQos2PublishStore.containsKey(clientId)
			|| pendingPublishStore.containsKey(clientId)
			|| messageIdStore.containsKey(clientId)
			|| subscribeStore.values().stream().anyMatch(data -> data.containsKey(clientId))
			|| queueSubscribeStore.values().stream().anyMatch(data -> data.containsKey(clientId))
			|| shareSubscribeStore.values().stream().flatMap(map -> map.values().stream()).anyMatch(data -> data.containsKey(clientId));
	}

	@Override
	public boolean expire(String clientId, int sessionExpirySeconds) {
		return false;
	}

	@Override
	public boolean active(String clientId) {
		return false;
	}

	public void removeSubscribe(String clientId) {
		subscribeStore.forEach((key, value) -> value.remove(clientId));
		queueSubscribeStore.forEach((key, value) -> value.remove(clientId));
		shareSubscribeStore.forEach((group, groupValue) -> groupValue.forEach((key, value) -> value.remove(clientId)));
	}

	@Override
	public void remove(String clientId) {
		removeSubscribe(clientId);
		pendingPublishStore.remove(clientId);
		pendingQos2PublishStore.remove(clientId);
		messageIdStore.remove(clientId);
	}

	@Override
	public void clean() {
		subscribeStore.clear();
		queueSubscribeStore.clear();
		shareSubscribeStore.clear();
		pendingPublishStore.clear();
		pendingQos2PublishStore.clear();
		messageIdStore.clear();
	}

}
