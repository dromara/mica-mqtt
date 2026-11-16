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

package org.dromara.mica.mqtt.core.client;

import net.dreamlu.mica.net.utils.collection.IntObjectHashMap;
import net.dreamlu.mica.net.utils.collection.IntObjectMap;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 客户端 session 管理，包括 sub 和 pub
 *
 * @author L.cm
 */
public class DefaultMqttClientSession implements IMqttClientSession {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttClientSession.class);
	/**
	 * packetId 递增生成器
	 */
	private final AtomicInteger packetIdGen = new AtomicInteger(1);
	/**
	 * 订阅的数据承载
	 */
	private final Map<String, Set<MqttClientSubscription>> subscriptions = new ConcurrentHashMap<>();
	private final Map<Integer, MqttPendingSubscription> pendingSubscriptions = new ConcurrentHashMap<>();
	private final Map<Integer, MqttPendingUnSubscription> pendingUnSubscriptions = new ConcurrentHashMap<>();
	private final Map<Integer, MqttPendingPublish> pendingPublishData = new ConcurrentHashMap<>();
	private final IntObjectMap<MqttPendingQos2Publish> pendingQos2PublishData = new IntObjectHashMap<>();
	/**
	 * MQTT 连接成功前的待发送发布消息队列
	 */
	private LinkedBlockingQueue<MqttPublishMessage> pendingPublishQueue;

	@Override
	public int getPacketId() {
		for (int i = 0; i < 0xffff; i++) {
			int packetId = packetIdGen.getAndUpdate(current -> (current % 0xffff) == 0 ? 1 : current + 1);
			if (!pendingSubscriptions.containsKey(packetId) && !pendingUnSubscriptions.containsKey(packetId)
				&& !pendingPublishData.containsKey(packetId)) {
				return packetId;
			}
		}
		throw new IllegalStateException("No MQTT packet identifier available");
	}

	@Override
	public void addPaddingSubscribe(int messageId, MqttPendingSubscription pendingSubscription) {
		pendingSubscriptions.put(messageId, pendingSubscription);
	}

	@Override
	public MqttPendingSubscription getPaddingSubscribe(int messageId) {
		return pendingSubscriptions.get(messageId);
	}

	@Override
	public void removePaddingSubscribes(List<String> topicFilters) {
		pendingSubscriptions.forEach((messageId, pendingSubscription) -> {
			synchronized (pendingSubscription) {
				if (pendingSubscription.cancelSubscriptions(topicFilters)) {
					// 全部取消时只停止重试定时器，不立即移除 pending 记录：
					// 在途的 SUBSCRIBE（首次发送或已触发的重试）可能尚未被 broker
					// 处理，broker 仍可能据此重新建立订阅。如果这里移除 pending，
					// 随后到达的 SUBACK 将找不到记录，无法补发补偿退订。
					// 保留 pending 让 processPendingSubAck 在 SUBACK 到达时
					// 补发 UNSUBSCRIBE 并清除记录。
					// 连接关闭后由 onDisconnected 清除。
					pendingSubscription.onSubAckReceived();
					pendingSubscription.markFullyCancelled();
				}
			}
		});
	}

	@Override
	public void onDisconnected() {
		pendingSubscriptions.forEach((messageId, pending) -> {
			synchronized (pending) {
				if (pending.isFullyCancelled()) {
					pendingSubscriptions.remove(messageId, pending);
				}
			}
		});
	}

	@Override
	public void restoreCancelledSubscriptions(List<String> topicFilters) {
		// 必须与 processSubAck 持有同一把 pending 锁：否则 restore 可能落在
		// 「读取取消集」与「发送补偿退订」之间，把新订阅意图退掉。
		pendingSubscriptions.values().forEach(pendingSubscription -> {
			synchronized (pendingSubscription) {
				pendingSubscription.restoreCancelledSubscriptions(topicFilters);
			}
		});
	}

	@Override
	public MqttPendingSubscription removePaddingSubscribe(int messageId) {
		return pendingSubscriptions.remove(messageId);
	}

	@Override
	public void addSubscription(MqttClientSubscription subscription) {
		subscriptions.computeIfAbsent(subscription.getTopicFilter(), key -> new CopyOnWriteArraySet<>()).add(subscription);
	}

	@Override
	public boolean isSubscribed(MqttClientSubscription clientSubscription) {
		// 1. 判断是否已经存在订阅关系
		String topicFilter = clientSubscription.getTopicFilter();
		Set<MqttClientSubscription> subscriptionSet = this.subscriptions.get(topicFilter);
		if (subscriptionSet == null || subscriptionSet.isEmpty()) {
			return false;
		}
		// 2. 存在时的逻辑
		MqttQoS mqttQoS = clientSubscription.getMqttQoS();
		IMqttClientMessageListener listener = clientSubscription.getListener();
		for (MqttClientSubscription subscription : subscriptionSet) {
			// 3. 已经存在订阅
			if (clientSubscription.equals(subscription)) {
				logger.error("MQTT Topic:{} mqttQoS:{} listener:{} duplicate subscription.", topicFilter, mqttQoS, listener);
				return true;
			}
			MqttQoS subQos = subscription.getMqttQoS();
			IMqttClientMessageListener subListener = subscription.getListener();
			// 4. 如果已经存在更高或同级别 qos
			if (subQos.value() >= mqttQoS.value()) {
				// 5. 监听器不相同则直接添加
				if (subListener != listener) {
					addSubscription(clientSubscription);
					logger.warn("MQTT Topic:{} mqttQoS:{} listener:{} has a higher level qos, added directly.", topicFilter, mqttQoS, listener);
				} else {
					logger.error("MQTT Topic:{} mqttQoS:{} listener:{} has a higher level qos, duplicate subscription.", topicFilter, mqttQoS, listener);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public List<MqttClientSubscription> getSubscriptions() {
		List<MqttClientSubscription> subscriptionList = new ArrayList<>();
		for (Set<MqttClientSubscription> mqttSubscriptions : subscriptions.values()) {
			subscriptionList.addAll(mqttSubscriptions);
		}
		return Collections.unmodifiableList(subscriptionList);
	}

	@Override
	public List<MqttClientSubscription> getMatchedSubscription(String topicName) {
		return subscriptions.values().stream()
			.flatMap(Collection::stream)
			.filter(subscription -> subscription.matches(topicName))
			.collect(Collectors.toList());
	}

	@Override
	public void removeSubscriptions(List<String> topicFilters) {
		topicFilters.forEach(subscriptions::remove);
	}

	@Override
	public void addPaddingUnSubscribe(int messageId, MqttPendingUnSubscription pendingUnSubscription) {
		pendingUnSubscriptions.put(messageId, pendingUnSubscription);
	}

	@Override
	public MqttPendingUnSubscription getPaddingUnSubscribe(int messageId) {
		return pendingUnSubscriptions.get(messageId);
	}

	@Override
	public MqttPendingUnSubscription removePaddingUnSubscribe(int messageId) {
		return pendingUnSubscriptions.remove(messageId);
	}

	@Override
	public void addPendingPublish(int messageId, MqttPendingPublish pendingPublish) {
		pendingPublishData.put(messageId, pendingPublish);
	}

	@Override
	public MqttPendingPublish getPendingPublish(int messageId) {
		return pendingPublishData.get(messageId);
	}

	@Override
	public MqttPendingPublish removePendingPublish(int messageId) {
		return pendingPublishData.remove(messageId);
	}

	@Override
	public List<MqttPendingPublish> getPendingPublishes() {
		return new ArrayList<>(pendingPublishData.values());
	}

	@Override
	public void addPendingQos2Publish(int messageId, MqttPendingQos2Publish pendingQos2Publish) {
		pendingQos2PublishData.put(messageId, pendingQos2Publish);
	}

	@Override
	public MqttPendingQos2Publish getPendingQos2Publish(int messageId) {
		return pendingQos2PublishData.get(messageId);
	}

	@Override
	public MqttPendingQos2Publish removePendingQos2Publish(int messageId) {
		return pendingQos2PublishData.remove(messageId);
	}

	@Override
	public void addPendingPublishMessage(MqttPublishMessage message) {
		offerPendingPublishMessage(message);
	}

	@Override
	public boolean offerPendingPublishMessage(MqttPublishMessage message) {
		return pendingPublishQueue != null && pendingPublishQueue.offer(message);
	}

	@Override
	public List<MqttPublishMessage> getAndClearPendingPublishMessages() {
		if (pendingPublishQueue == null) {
			return Collections.emptyList();
		}
		List<MqttPublishMessage> messages = new ArrayList<>();
		pendingPublishQueue.drainTo(messages);
		return messages;
	}

	@Override
	public int getPendingPublishMessageCount() {
		return pendingPublishQueue == null ? 0 : pendingPublishQueue.size();
	}

	@Override
	public void initPendingPublishQueue(int queueSize) {
		if (queueSize > 0) {
			this.pendingPublishQueue = new LinkedBlockingQueue<>(queueSize);
		}
	}

	@Override
	public void clean() {
		subscriptions.clear();
		pendingSubscriptions.clear();
		pendingUnSubscriptions.clear();
		pendingPublishData.clear();
		pendingQos2PublishData.clear();
		if (pendingPublishQueue != null) {
			pendingPublishQueue.clear();
		}
	}
}
