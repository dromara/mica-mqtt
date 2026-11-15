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
import org.dromara.mica.mqtt.codec.codes.MqttSubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttSubAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMqttClientSessionConcurrencyTest {
	@Test
	void cancellationPreservesSubAckReasonCodePositions() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		IMqttClientMessageListener listener = (context, topic, message, payload) -> { };
		MqttClientSubscription first = new MqttClientSubscription("first", MqttSubscriptionOption.from(MqttQoS.QOS0), listener);
		MqttClientSubscription second = new MqttClientSubscription("second", MqttSubscriptionOption.from(MqttQoS.QOS0), listener);
		MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
			.addSubscription("first", MqttQoS.QOS0).addSubscription("second", MqttQoS.QOS0).build();
		session.addPaddingSubscribe(1, new MqttPendingSubscription(Arrays.asList(first, second), subscribe));
		session.removePaddingSubscribes(Collections.singletonList("first"));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(
				MqttClient.create().clientSession(session).mqttExecutor(executor));
			MqttSubAckMessage ack = MqttSubAckMessage.builder().packetId(1)
				.addReasonCodes(MqttSubAckReasonCode.UNSPECIFIED_ERROR, MqttSubAckReasonCode.GRANTED_QOS0).build();
			processor.processSubAck(null, ack);
			assertEquals(Collections.singletonList(second), session.getSubscriptions());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentSubscriptionUpdatesAndMatching() throws Exception {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		MqttClientSubscription subscription = new MqttClientSubscription("a/+", MqttSubscriptionOption.from(MqttQoS.QOS0),
			(context, topic, message, payload) -> { });
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> writer = executor.submit(() -> {
				for (int i = 0; i < 10000; i++) {
					session.addSubscription(subscription);
					session.removeSubscriptions(Collections.singletonList("a/+"));
				}
			});
			Future<?> reader = executor.submit(() -> {
				for (int i = 0; i < 10000; i++) {
					session.getMatchedSubscription("a/b");
					session.getSubscriptions();
				}
			});
			writer.get();
			reader.get();
			session.addSubscription(subscription);
			assertEquals(1, session.getMatchedSubscription("a/b").size());
		} finally {
			executor.shutdownNow();
		}
	}
}
