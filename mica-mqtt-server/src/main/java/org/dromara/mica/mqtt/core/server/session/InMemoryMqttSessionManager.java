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

import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.tio.utils.collection.IntObjectHashMap;
import org.tio.utils.collection.IntObjectMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存 session 管理
 *
 * @author L.cm
 */
public class InMemoryMqttSessionManager implements IMqttSessionManager {
	/**
	 * messageId 存储 clientId: messageId
	 */
	private final ConcurrentMap<String, AtomicInteger> messageIdStore = new ConcurrentHashMap<>();
	/**
	 * 订阅存储，支持共享订阅
	 */
	private final TrieTopicManager topicManager = new TrieTopicManager();
	/**
	 * qos1 消息过程存储 clientId: {msgId: Object}
	 */
	private final ConcurrentMap<String, IntObjectMap<MqttPendingPublish>> pendingPublishStore = new ConcurrentHashMap<>();
	/**
	 * qos2 消息过程存储 clientId: {msgId: Object}
	 */
	private final ConcurrentMap<String, IntObjectMap<MqttPendingQos2Publish>> pendingQos2PublishStore = new ConcurrentHashMap<>();

	@Override
	public void addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS) {
		topicManager.addSubscribe(topicFilter, clientId, (short) mqttQoS);
	}

	@Override
	public void removeSubscribe(String topicFilter, String clientId) {
		topicManager.removeSubscribe(topicFilter, clientId);
	}

	public void removeSubscribe(String clientId) {
		topicManager.removeSubscribe(clientId);
	}

	@Override
	public Byte searchSubscribe(String topicName, String clientId) {
		return topicManager.searchSubscribe(topicName, clientId);
	}

	@Override
	public List<Subscribe> searchSubscribe(String topicName) {
		return topicManager.searchSubscribe(topicName);
	}

	@Override
	public List<Subscribe> getSubscriptions(String clientId) {
		return topicManager.getSubscriptions(clientId);
	}

	@Override
	public void addPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.computeIfAbsent(clientId, (key) -> new IntObjectHashMap<>(16));
		data.put(messageId, pendingPublish);
	}

	@Override
	public MqttPendingPublish getPendingPublish(String clientId, int messageId) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.get(clientId);
		if (data == null) {
			return null;
		}
		return data.get(messageId);
	}

	@Override
	public void removePendingPublish(String clientId, int messageId) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.get(clientId);
		if (data != null) {
			data.remove(messageId);
		}
	}

	@Override
	public void addPendingQos2Publish(String clientId, int messageId, MqttPendingQos2Publish pendingQos2Publish) {
		Map<Integer, MqttPendingQos2Publish> data = pendingQos2PublishStore.computeIfAbsent(clientId, (key) -> new IntObjectHashMap<>());
		data.put(messageId, pendingQos2Publish);
	}

	@Override
	public MqttPendingQos2Publish getPendingQos2Publish(String clientId, int messageId) {
		Map<Integer, MqttPendingQos2Publish> data = pendingQos2PublishStore.get(clientId);
		if (data == null) {
			return null;
		}
		return data.get(messageId);
	}

	@Override
	public void removePendingQos2Publish(String clientId, int messageId) {
		Map<Integer, MqttPendingQos2Publish> data = pendingQos2PublishStore.get(clientId);
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
			|| !topicManager.getSubscriptions(clientId).isEmpty();
	}

	@Override
	public boolean expire(String clientId, int sessionExpirySeconds) {
		return false;
	}

	@Override
	public boolean active(String clientId) {
		return false;
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
		topicManager.clear();
		pendingPublishStore.clear();
		pendingQos2PublishStore.clear();
		messageIdStore.clear();
	}

}
