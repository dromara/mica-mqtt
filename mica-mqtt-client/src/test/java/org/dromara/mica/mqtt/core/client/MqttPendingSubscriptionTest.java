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
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption;
import org.dromara.mica.mqtt.codec.message.builder.MqttTopicSubscription;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttPendingSubscriptionTest {

	private static MqttClientSubscription subscription(String topic) {
		return new MqttClientSubscription(topic, MqttSubscriptionOption.from(MqttQoS.QOS0),
			(context, t, message, payload) -> {
			});
	}

	private static MqttSubscribeMessage subscribeMessage(List<MqttTopicSubscription> subscriptions) {
		return MqttSubscribeMessage.builder().messageId(1).addSubscriptions(subscriptions).build();
	}

	/**
	 * 重试必须保持与首次发送完全相同的 topic 顺序：SUBACK 不携带 topic，
	 * 只有保持顺序才能让早到/晚到的两份 SUBACK 都被正确解释。
	 * 已取消的 topic 单独记录，由客户端在 SUBACK 后补发 UNSUBSCRIBE。
	 */
	@Test
	void retryMessageKeepsOriginalOrderAndTracksCancelledTopics() {
		List<MqttClientSubscription> subscriptions = Arrays.asList(subscription("a/1"), subscription("b/2"));
		MqttSubscribeMessage original = subscribeMessage(Arrays.asList(
			subscriptions.get(0).toTopicSubscription(), subscriptions.get(1).toTopicSubscription()));
		MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, original);

		pending.cancelSubscriptions(Collections.singletonList("a/1"));
		MqttMessage retry = pending.buildRetryMessage(original.fixedHeader(), original);

		List<MqttTopicSubscription> retried = ((MqttSubscribeMessage) retry).payload().topicSubscriptions();
		assertEquals(2, retried.size());
		assertEquals("a/1", retried.get(0).topicFilter());
		assertEquals("b/2", retried.get(1).topicFilter());
		assertEquals(Collections.singletonList("a/1"), pending.getCancelledTopicFilters());
	}

	@Test
	void retryMessageIsIdenticalToOriginalPayload() {
		List<MqttClientSubscription> subscriptions = Collections.singletonList(subscription("a/1"));
		MqttSubscribeMessage original = subscribeMessage(
			Collections.singletonList(subscriptions.get(0).toTopicSubscription()));
		MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, original);

		pending.cancelSubscriptions(Collections.singletonList("a/1"));
		MqttMessage retry = pending.buildRetryMessage(original.fixedHeader(), original);
		assertEquals(original.payload().topicSubscriptions(), ((MqttSubscribeMessage) retry).payload().topicSubscriptions());
	}

	/**
	 * 应用在旧 SUBACK 前重新订阅同一 topic 时，撤销取消标记，
	 * 避免后续补偿 UNSUBSCRIBE 把新订阅误删。
	 */
	@Test
	void restoringTopicRemovesCancellation() {
		List<MqttClientSubscription> subscriptions = Collections.singletonList(subscription("a/1"));
		MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions,
			subscribeMessage(Collections.singletonList(subscriptions.get(0).toTopicSubscription())));

		pending.cancelSubscriptions(Collections.singletonList("a/1"));
		pending.restoreCancelledSubscriptions(Collections.singletonList("a/1"));

		assertTrue(pending.getCancelledTopicFilters().isEmpty());
		assertFalse(pending.isCancelled("a/1"));
	}
}
