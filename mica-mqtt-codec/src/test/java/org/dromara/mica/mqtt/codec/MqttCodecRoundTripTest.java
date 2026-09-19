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

package org.dromara.mica.mqtt.codec;

import net.dreamlu.mica.net.client.ClientChannelContext;
import net.dreamlu.mica.net.client.TioClientConfig;
import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.message.MqttConnectMessage;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.MqttUnSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttTopicSubscription;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编解码往返测试：{@link MqttEncoder#doEncode} 编码后由 {@link MqttDecoder#doDecode} 解回，
 * 断言解出的内容与原始报文一致。
 *
 * <p>每次用例都用新的 {@link ChannelContext}：CONNECT 的编解码都会把协议版本写进 ctx
 * （{@code MqttCodecUtil#setMqttVersion}），复用 ctx 会让后面的用例串味。</p>
 *
 * @author L.cm
 */
class MqttCodecRoundTripTest {
	private static final TioClientConfig TIO_CONFIG = new TioClientConfig(null, null);

	@Test
	void connectRoundTrip() {
		MqttConnectMessage message = MqttConnectMessage.builder()
			.clientId("mica-test")
			.cleanStart(true)
			.keepAlive(60)
			.build();

		MqttConnectMessage decoded = (MqttConnectMessage) roundTrip(message);

		assertEquals(MqttMessageType.CONNECT, decoded.fixedHeader().messageType());
		assertEquals("mica-test", decoded.payload().clientIdentifier());
		assertEquals(60, decoded.variableHeader().keepAliveTimeSeconds());
		assertTrue(decoded.variableHeader().isCleanStart());
		assertFalse(decoded.variableHeader().isWillFlag());
	}

	@Test
	void publishQos0RoundTrip() {
		byte[] payload = "hello mica".getBytes(StandardCharsets.UTF_8);
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/round-trip")
			.payload(payload)
			.qos(MqttQoS.QOS0)
			.build();

		MqttPublishMessage decoded = (MqttPublishMessage) roundTrip(message);

		assertEquals(MqttMessageType.PUBLISH, decoded.fixedHeader().messageType());
		assertEquals(MqttQoS.QOS0, decoded.fixedHeader().qosLevel());
		assertFalse(decoded.fixedHeader().isDup());
		assertEquals("/test/round-trip", decoded.variableHeader().topicName());
		assertArrayEquals(payload, decoded.payload());
	}

	@Test
	void publishQos1RoundTrip() {
		byte[] payload = "qos1".getBytes(StandardCharsets.UTF_8);
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/round-trip")
			.payload(payload)
			.qos(MqttQoS.QOS1)
			.messageId(0x1234)
			.build();

		MqttPublishMessage decoded = (MqttPublishMessage) roundTrip(message);

		assertEquals(MqttQoS.QOS1, decoded.fixedHeader().qosLevel());
		assertEquals(0x1234, decoded.variableHeader().packetId());
		assertEquals("/test/round-trip", decoded.variableHeader().topicName());
		assertArrayEquals(payload, decoded.payload());
	}

	/**
	 * QoS1 重传时置了 dup，往返后 dup 必须还在，否则重传语义丢失。
	 */
	@Test
	void publishRetransmitKeepsDupRoundTrip() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/round-trip")
			.payload(new byte[]{1, 2, 3})
			.qos(MqttQoS.QOS1)
			.messageId(2)
			.build();
		// 模拟 RetryProcessor 重传
		message.fixedHeader().setDup(true);

		MqttPublishMessage decoded = (MqttPublishMessage) roundTrip(message);

		assertTrue(decoded.fixedHeader().isDup());
		assertEquals(MqttQoS.QOS1, decoded.fixedHeader().qosLevel());
		assertEquals(2, decoded.variableHeader().packetId());
	}

	@Test
	void publishQos2RoundTrip() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/round-trip")
			.payload(new byte[]{9})
			.qos(MqttQoS.QOS2)
			.messageId(3)
			.build();

		MqttPublishMessage decoded = (MqttPublishMessage) roundTrip(message);

		assertEquals(MqttQoS.QOS2, decoded.fixedHeader().qosLevel());
		assertEquals(3, decoded.variableHeader().packetId());
	}

	@Test
	void subscribeRoundTrip() {
		MqttSubscribeMessage message = MqttSubscribeMessage.builder()
			.addSubscription("/test/a", MqttQoS.QOS0)
			.addSubscription("/test/b/#", MqttQoS.QOS2)
			.messageId(0x0A0B)
			.build();

		MqttSubscribeMessage decoded = (MqttSubscribeMessage) roundTrip(message);

		assertEquals(MqttMessageType.SUBSCRIBE, decoded.fixedHeader().messageType());
		assertEquals(0x0A0B, decoded.variableHeader().messageId());
		// SUBSCRIBE 的 bit3 是保留位，往返后必须仍是 0
		assertFalse(decoded.fixedHeader().isDup());
		List<MqttTopicSubscription> subscriptions = decoded.payload().topicSubscriptions();
		assertEquals(2, subscriptions.size());
		assertEquals("/test/a", subscriptions.get(0).topicFilter());
		assertEquals(MqttQoS.QOS0, subscriptions.get(0).qualityOfService());
		assertEquals("/test/b/#", subscriptions.get(1).topicFilter());
		assertEquals(MqttQoS.QOS2, subscriptions.get(1).qualityOfService());
	}

	@Test
	void unSubscribeRoundTrip() {
		MqttUnSubscribeMessage message = MqttUnSubscribeMessage.builder()
			.addTopicFilter("/test/a")
			.addTopicFilter("/test/b/#")
			.messageId(0x0C0D)
			.build();

		MqttUnSubscribeMessage decoded = (MqttUnSubscribeMessage) roundTrip(message);

		assertEquals(MqttMessageType.UNSUBSCRIBE, decoded.fixedHeader().messageType());
		assertEquals(0x0C0D, decoded.variableHeader().messageId());
		assertFalse(decoded.fixedHeader().isDup());
		assertEquals(java.util.Arrays.asList("/test/a", "/test/b/#"), decoded.payload().topics());
	}

	@Test
	void pingReqRoundTrip() {
		// PINGREQ 只有固定头，解码后返回共享常量
		assertSame(MqttMessage.PINGREQ, roundTrip(MqttMessage.PINGREQ));
	}

	/**
	 * 编码 → 解码。
	 *
	 * @param message MqttMessage
	 * @return 解码结果
	 */
	private static MqttMessage roundTrip(MqttMessage message) {
		ChannelContext ctx = new ClientChannelContext(TIO_CONFIG);
		ByteBuffer buffer = MqttEncoder.INSTANCE.doEncode(ctx, message);
		// doEncode 返回的 buffer 已写满，position 停在末尾，先翻到可读再解码
		int readable = buffer.position();
		buffer.flip();
		return (MqttMessage) new MqttDecoder().doDecode(ctx, buffer, readable);
	}

}
