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

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.utils.collection.MultiValueMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 客户端 session 管理，包括 sub 和 pub
 *
 * @author L.cm
 */
public class DefaultMqttClientSession implements IMqttClientSession {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttClientSession.class);
	/**
	 * 订阅的数据承载
	 */
	private final MultiValueMap<String, MqttClientSubscription> subscriptions = new MultiValueMap<>(new ConcurrentHashMap<>());
	private final ConcurrentMap<Integer, MqttPendingSubscription> pendingSubscriptions = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, MqttPendingUnSubscription> pendingUnSubscriptions = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, MqttPendingPublish> pendingPublishData = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, MqttPendingQos2Publish> pendingQos2PublishData = new ConcurrentHashMap<>();

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
		Set<Integer> needToRemove = new HashSet<>();
		pendingSubscriptions.forEach((messageId, pendingSubscription) -> {
			List<MqttClientSubscription> subscriptionList = pendingSubscription.getSubscriptionList();
			if (subscriptionList != null) {
				subscriptionList.removeIf(subscription -> topicFilters.contains(subscription.getTopicFilter()));
			}
			// 如果已经被删到为空
			if (subscriptionList == null || subscriptionList.isEmpty()) {
				// 停止线程
				pendingSubscription.onSubAckReceived();
				needToRemove.add(messageId);
			}
		});
		// 清除 messageId 的过程订阅
		needToRemove.forEach(pendingSubscriptions::remove);
	}

	@Override
	public MqttPendingSubscription removePaddingSubscribe(int messageId) {
		return pendingSubscriptions.remove(messageId);
	}

	@Override
	public void addSubscription(MqttClientSubscription subscription) {
		subscriptions.add(subscription.getTopicFilter(), subscription);
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
					subscriptions.add(topicFilter, clientSubscription);
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
	public void clean() {
		subscriptions.clear();
		pendingSubscriptions.clear();
		pendingUnSubscriptions.clear();
		pendingPublishData.clear();
		pendingQos2PublishData.clear();
	}
}
