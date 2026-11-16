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

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.dromara.mica.mqtt.codec.codes.MqttSubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttSubAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class DefaultMqttClientProcessorSubAckTest {

	private static MqttClientSubscription subscription(String topic) {
		return new MqttClientSubscription(topic, MqttSubscriptionOption.from(MqttQoS.QOS0),
			(context, t, message, payload) -> {
			});
	}

	/**
	 * Broker 返回的 reason code 少于订阅数量时不能抛数组越界；
	 * 缺失的条目按失败处理，只有拿到成功码的 topic 才写入本地订阅表。
	 */
	@Test
	void fewerReasonCodesThanSubscriptionsMustNotThrow() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttClientCreator creator = MqttClient.create().clientSession(session).mqttExecutor(executor);
			List<MqttClientSubscription> subscriptions = Arrays.asList(subscription("a/1"), subscription("b/2"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Arrays.asList(subscriptions.get(0).toTopicSubscription(),
					subscriptions.get(1).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			session.addPaddingSubscribe(1, pending);

			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator);
			MqttSubAckMessage subAck = MqttSubAckMessage.builder().packetId(1).addGrantedQos(MqttQoS.QOS0).build();
			processor.processSubAck(null, subAck);

			assertTrue(session.isSubscribed(subscriptions.get(0)));
			assertFalse(session.isSubscribed(subscriptions.get(1)));
		} finally {
			executor.shutdownNow();
			session.clean();
		}
	}

	/**
	 * MQTT 5 下单个 reason code 不能当成“全部成功”，否则批量订阅会被误判。
	 */
	@Test
	void singleReasonCodeMustNotMarkAllSubscriptionsSuccessfulInMqtt5() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttClientCreator creator = MqttClient.create().clientSession(session).mqttExecutor(executor)
				.version(MqttVersion.MQTT_5);
			List<MqttClientSubscription> subscriptions = Arrays.asList(subscription("a/1"), subscription("b/2"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Arrays.asList(subscriptions.get(0).toTopicSubscription(),
					subscriptions.get(1).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			session.addPaddingSubscribe(1, pending);

			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator);
			MqttSubAckMessage subAck = MqttSubAckMessage.builder().packetId(1).addGrantedQos(MqttQoS.QOS0).build();
			processor.processSubAck(null, subAck);

			assertTrue(session.isSubscribed(subscriptions.get(0)));
			assertFalse(session.isSubscribed(subscriptions.get(1)));
		} finally {
			executor.shutdownNow();
			session.clean();
		}
	}

	/**
	 * 部分退订后，SUBACK 仍按原始 topic 顺序返回；已取消的 topic 只过滤本地订阅，
	 * 不能把后面的 reason code 错位到别的 topic。
	 */
	@Test
	void cancelledTopicDoesNotShiftSubAckReasonCodes() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttClientCreator creator = MqttClient.create().clientSession(session).mqttExecutor(executor);
			List<MqttClientSubscription> subscriptions = Arrays.asList(subscription("a/1"), subscription("b/2"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Arrays.asList(subscriptions.get(0).toTopicSubscription(),
					subscriptions.get(1).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			pending.cancelSubscriptions(java.util.Collections.singletonList("a/1"));
			session.addPaddingSubscribe(1, pending);

			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator);
			MqttSubAckMessage subAck = MqttSubAckMessage.builder().packetId(1)
				.addGrantedQos(MqttQoS.QOS1).addGrantedQos(MqttQoS.QOS1).build();
			processor.processSubAck(null, subAck);

			assertFalse(session.isSubscribed(subscriptions.get(0)));
			assertTrue(session.isSubscribed(subscriptions.get(1)));
		} finally {
			executor.shutdownNow();
			session.clean();
		}
	}

	/**
	 * 剩余 topic 全部被拒绝（0x80）时，补偿退订仍必须发出：
	 * 重试的原始 SUBSCRIBE 可能已经把取消的 topic 重新订阅回来。
	 */
	@Test
	void compensationIsSentEvenWhenAllRemainingTopicsAreDenied() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttClientCreator creator = MqttClient.create().clientSession(session).mqttExecutor(executor);
			List<MqttClientSubscription> subscriptions = Arrays.asList(subscription("a/1"), subscription("b/2"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Arrays.asList(subscriptions.get(0).toTopicSubscription(),
					subscriptions.get(1).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			pending.cancelSubscriptions(Collections.singletonList("a/1"));
			session.addPaddingSubscribe(1, pending);

			List<String> compensated = new ArrayList<>();
			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator) {
				@Override
				void sendCompensatingUnsubscribe(ChannelContext context, List<String> cancelledTopicFilters) {
					compensated.addAll(cancelledTopicFilters);
				}
			};
			MqttSubAckMessage subAck = MqttSubAckMessage.builder().packetId(1)
				.addReasonCode(MqttSubAckReasonCode.UNSPECIFIED_ERROR)
				.addReasonCode(MqttSubAckReasonCode.UNSPECIFIED_ERROR)
				.build();
			processor.processSubAck(null, subAck);

			assertEquals(Collections.singletonList("a/1"), compensated);
			assertFalse(session.isSubscribed(subscriptions.get(0)));
			assertFalse(session.isSubscribed(subscriptions.get(1)));
		} finally {
			executor.shutdownNow();
			session.clean();
		}
	}

	/**
	 * 服务端明确拒绝全部 topic 时，pending 必须结束，
	 * 否则条目会永久滞留在 session 中并继续触发重试。
	 */
	@Test
	void allTopicsDeniedRemovesPendingSubscription() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttClientCreator creator = MqttClient.create().clientSession(session).mqttExecutor(executor);
			List<MqttClientSubscription> subscriptions =
				Collections.singletonList(subscription("denied/topic"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Collections.singletonList(subscriptions.get(0).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			session.addPaddingSubscribe(1, pending);

			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator);
			MqttSubAckMessage subAck = MqttSubAckMessage.builder().packetId(1)
				.addReasonCode(MqttSubAckReasonCode.UNSPECIFIED_ERROR)
				.build();
			processor.processSubAck(null, subAck);

			assertFalse(session.isSubscribed(subscriptions.get(0)));
			assertNull(session.getPaddingSubscribe(1));
		} finally {
			executor.shutdownNow();
			session.clean();
		}
	}

	/**
	 * 应用在旧 SUBACK 到达前重新订阅了已取消的 topic 时，
	 * 补偿退订不能再包含它，否则会把新的有效订阅退掉。
	 */
	@Test
	void restoredTopicIsExcludedFromCompensation() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttClientCreator creator = MqttClient.create().clientSession(session).mqttExecutor(executor);
			List<MqttClientSubscription> subscriptions = Arrays.asList(subscription("a/1"), subscription("b/2"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Arrays.asList(subscriptions.get(0).toTopicSubscription(),
					subscriptions.get(1).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			pending.cancelSubscriptions(Collections.singletonList("a/1"));
			// 应用层重新订阅 a/1：撤销取消标记
			session.addPaddingSubscribe(1, pending);
			session.restoreCancelledSubscriptions(Collections.singletonList("a/1"));

			List<String> compensated = new ArrayList<>();
			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator) {
				@Override
				void sendCompensatingUnsubscribe(ChannelContext context, List<String> cancelledTopicFilters) {
					compensated.addAll(cancelledTopicFilters);
				}
			};
			MqttSubAckMessage subAck = MqttSubAckMessage.builder().packetId(1)
				.addGrantedQos(MqttQoS.QOS1).addGrantedQos(MqttQoS.QOS1)
				.build();
			processor.processSubAck(null, subAck);

			assertTrue(compensated.isEmpty());
			assertTrue(session.isSubscribed(subscriptions.get(0)));
			assertTrue(session.isSubscribed(subscriptions.get(1)));
		} finally {
			executor.shutdownNow();
			session.clean();
		}
	}

	/**
	 * 全部取消后 pending 不立即移除：在途 SUBSCRIBE 的 SUBACK 到达时，
	 * 必须仍能补发补偿退订，否则 broker 端会残留重新建立的订阅。
	 */
	@Test
	void fullCancellationKeepsPendingUntilSubAckArrives() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttClientCreator creator = MqttClient.create().clientSession(session).mqttExecutor(executor);
			List<MqttClientSubscription> subscriptions = Collections.singletonList(subscription("a/1"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Collections.singletonList(subscriptions.get(0).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			session.addPaddingSubscribe(1, pending);

			// 全部取消
			session.removePaddingSubscribes(Collections.singletonList("a/1"));

			// pending 必须仍然存在（等 SUBACK 补发补偿退订）
			assertNotNull(session.getPaddingSubscribe(1));
			assertTrue(pending.isFullyCancelled());

			List<String> compensated = new ArrayList<>();
			DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator) {
				@Override
				void sendCompensatingUnsubscribe(ChannelContext context, List<String> cancelledTopicFilters) {
					compensated.addAll(cancelledTopicFilters);
				}
			};
			MqttSubAckMessage subAck = MqttSubAckMessage.builder().packetId(1)
				.addGrantedQos(MqttQoS.QOS0).build();
			processor.processSubAck(null, subAck);

			// 补偿退订已发出
			assertEquals(Collections.singletonList("a/1"), compensated);
			// pending 已清除
			assertNull(session.getPaddingSubscribe(1));
			// 本地订阅表中没有残留
			assertFalse(session.isSubscribed(subscriptions.get(0)));
		} finally {
			executor.shutdownNow();
			session.clean();
		}
	}

	/**
	 * 未收到 SUBACK 时保留 packetId，连接关闭后清理已取消的记录。
	 */
	@Test
	void cancelledPendingReservesPacketIdUntilDisconnect() throws Exception {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		try {
			List<MqttClientSubscription> subscriptions = Collections.singletonList(subscription("stale/topic"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Collections.singletonList(subscriptions.get(0).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			session.addPaddingSubscribe(1, pending);
			session.removePaddingSubscribes(Collections.singletonList("stale/topic"));

			// pending 仍然存在
			assertNotNull(session.getPaddingSubscribe(1));

			// 通过反射将 packetIdGen 推进到 0xFFFF，使下一次 getPacketId 返回 0xFFFF，
			// 再下一次返回 1（回绕），触发陈旧 pending 清理
			java.lang.reflect.Field f = DefaultMqttClientSession.class.getDeclaredField("packetIdGen");
			f.setAccessible(true);
			java.util.concurrent.atomic.AtomicInteger gen =
				(java.util.concurrent.atomic.AtomicInteger) f.get(session);
			gen.set(0xFFFF);

			// 返回 0xFFFF，正常
			assertEquals(0xFFFF, session.getPacketId());
			assertEquals(2, session.getPacketId());
			assertNotNull(session.getPaddingSubscribe(1));
			session.onDisconnected();
			assertNull(session.getPaddingSubscribe(1));
		} finally {
			session.clean();
		}
	}

	/**
	 * 全部取消后应用层重新订阅（restore）时，fullyCancelled 必须复位，
	 * packetId 回绕不应清除已恢复的 pending。
	 */
	@Test
	void restoredPendingIsNotCleanedOnPacketIdWrap() throws Exception {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		try {
			List<MqttClientSubscription> subscriptions = Collections.singletonList(subscription("restore/topic"));
			MqttSubscribeMessage subscribe = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Collections.singletonList(subscriptions.get(0).toTopicSubscription()))
				.build();
			MqttPendingSubscription pending = new MqttPendingSubscription(subscriptions, subscribe);
			session.addPaddingSubscribe(1, pending);
			// 全部取消
			session.removePaddingSubscribes(Collections.singletonList("restore/topic"));
			assertTrue(pending.isFullyCancelled());

			// 应用层重新订阅：撤销取消标记，复位 fullyCancelled
			session.restoreCancelledSubscriptions(Collections.singletonList("restore/topic"));
			assertFalse(pending.isFullyCancelled());

			// 推进 packetId 到回绕
			java.lang.reflect.Field f = DefaultMqttClientSession.class.getDeclaredField("packetIdGen");
			f.setAccessible(true);
			java.util.concurrent.atomic.AtomicInteger gen =
				(java.util.concurrent.atomic.AtomicInteger) f.get(session);
			gen.set(0xFFFF);
			assertEquals(0xFFFF, session.getPacketId());
			assertEquals(2, session.getPacketId());

			// 已恢复的 pending 不应被清除
			assertNotNull(session.getPaddingSubscribe(1));
		} finally {
			session.clean();
		}
	}

	@Test
	void unrelatedSubscriptionDoesNotRestoreCancelledPending() {
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		try {
			MqttClientSubscription subscription = subscription("a/1");
			MqttSubscribeMessage message = MqttSubscribeMessage.builder().messageId(1)
				.addSubscriptions(Collections.singletonList(subscription.toTopicSubscription())).build();
			MqttPendingSubscription pending = new MqttPendingSubscription(Collections.singletonList(subscription), message);
			session.addPaddingSubscribe(1, pending);
			session.removePaddingSubscribes(Collections.singletonList("a/1"));
			session.restoreCancelledSubscriptions(Collections.singletonList("b/1"));
			assertTrue(pending.isFullyCancelled());
			session.onDisconnected();
			assertNull(session.getPaddingSubscribe(1));
		} finally {
			session.clean();
		}
	}
}
