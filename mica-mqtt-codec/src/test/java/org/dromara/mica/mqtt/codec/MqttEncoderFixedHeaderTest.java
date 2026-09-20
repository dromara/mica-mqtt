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

import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定头第一个字节的编码规则回归测试。
 *
 * <p>MQTT 规定 {@code DUP} 只对 {@code PUBLISH} 且 {@code qos > 0} 生效；
 * {@code SUBSCRIBE}、{@code UNSUBSCRIBE}、{@code PUBREL}、{@code PUBREC} 等报文的 bit3
 * 是保留位，必须为 0。置 1 后发出的 {@code 0x8A}、{@code 0xAA}、{@code 0x6A} 会被
 * mosquitto、netty-codec-mqtt 等严格校验的实现判定为 malformed 并断开连接。</p>
 *
 * <p>反馈：<a href="https://gitee.com/dromara/mica-mqtt/issues/IKH0V8">IKH0V8</a>。
 * 字节出口只有 {@link MqttEncoder#getFixedHeaderByte1(MqttFixedHeader)} 一处，
 * 因此断言点放在这里，任何调用方误置 dup 都会被兜住。</p>
 *
 * @author L.cm
 */
class MqttEncoderFixedHeaderTest {

	/**
	 * PUBLISH 是唯一 DUP 生效的报文类型，且必须 qos &gt; 0。
	 */
	@Test
	void publishEncodesDupOnlyForQosGtZero() {
		assertEquals((byte) 0x30, firstByte(MqttMessageType.PUBLISH, false, MqttQoS.QOS0, false));
		// qos0 没有重传语义，即使调用方置了 dup 也必须编码成 0
		assertEquals((byte) 0x30, firstByte(MqttMessageType.PUBLISH, true, MqttQoS.QOS0, false));

		assertEquals((byte) 0x32, firstByte(MqttMessageType.PUBLISH, false, MqttQoS.QOS1, false));
		assertEquals((byte) 0x3A, firstByte(MqttMessageType.PUBLISH, true, MqttQoS.QOS1, false));

		assertEquals((byte) 0x34, firstByte(MqttMessageType.PUBLISH, false, MqttQoS.QOS2, false));
		assertEquals((byte) 0x3C, firstByte(MqttMessageType.PUBLISH, true, MqttQoS.QOS2, false));
	}

	/**
	 * retain 在 bit0，不受 dup 判定影响。
	 */
	@Test
	void publishKeepsRetain() {
		assertEquals((byte) 0x31, firstByte(MqttMessageType.PUBLISH, false, MqttQoS.QOS0, true));
		assertEquals((byte) 0x3B, firstByte(MqttMessageType.PUBLISH, true, MqttQoS.QOS1, true));
	}

	/**
	 * bit3 为保留位的报文，重传时置了 dup 也不能编码出去。
	 */
	@Test
	void reservedBitMessagesNeverEncodeDup() {
		assertEquals((byte) 0x82, firstByte(MqttMessageType.SUBSCRIBE, true, MqttQoS.QOS1, false));
		assertEquals((byte) 0xA2, firstByte(MqttMessageType.UNSUBSCRIBE, true, MqttQoS.QOS1, false));
		assertEquals((byte) 0x62, firstByte(MqttMessageType.PUBREL, true, MqttQoS.QOS1, false));
		assertEquals((byte) 0x50, firstByte(MqttMessageType.PUBREC, true, MqttQoS.QOS0, false));
		// 未置 dup 时本来就是正确值，同样断言，防止以后改坏
		assertEquals((byte) 0x82, firstByte(MqttMessageType.SUBSCRIBE, false, MqttQoS.QOS1, false));
		assertEquals((byte) 0xA2, firstByte(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.QOS1, false));
		assertEquals((byte) 0x62, firstByte(MqttMessageType.PUBREL, false, MqttQoS.QOS1, false));
	}

	/**
	 * 其余类型 bit3 同为保留位，逐个兜住，避免新增报文时漏判。
	 */
	@Test
	void otherMessagesNeverEncodeDup() {
		assertEquals((byte) 0x10, firstByte(MqttMessageType.CONNECT, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0x20, firstByte(MqttMessageType.CONNACK, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0x40, firstByte(MqttMessageType.PUBACK, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0x70, firstByte(MqttMessageType.PUBCOMP, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0x90, firstByte(MqttMessageType.SUBACK, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0xB0, firstByte(MqttMessageType.UNSUBACK, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0xC0, firstByte(MqttMessageType.PINGREQ, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0xD0, firstByte(MqttMessageType.PINGRESP, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0xE0, firstByte(MqttMessageType.DISCONNECT, true, MqttQoS.QOS0, false));
		assertEquals((byte) 0xF0, firstByte(MqttMessageType.AUTH, true, MqttQoS.QOS0, false));
	}

	/**
	 * {@code isDup} 是调用方意图，{@code isDupEffected} 才是编码是否放行，两者不能混用。
	 */
	@Test
	void dupIntentIsSeparatedFromDupEffected() {
		MqttFixedHeader publish = fixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.QOS1, false);
		assertTrue(publish.isDup());
		assertTrue(publish.isDupEffected());

		MqttFixedHeader publishQos0 = fixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.QOS0, false);
		assertTrue(publishQos0.isDup());
		assertFalse(publishQos0.isDupEffected());

		MqttFixedHeader unSubscribe = fixedHeader(MqttMessageType.UNSUBSCRIBE, true, MqttQoS.QOS1, false);
		assertTrue(unSubscribe.isDup());
		assertFalse(unSubscribe.isDupEffected());
	}

	private static MqttFixedHeader fixedHeader(MqttMessageType messageType, boolean dup, MqttQoS qos, boolean retain) {
		return new MqttFixedHeader(messageType, dup, qos, retain, 0);
	}

	private static byte firstByte(MqttMessageType messageType, boolean dup, MqttQoS qos, boolean retain) {
		return MqttEncoder.getFixedHeaderByte1(fixedHeader(messageType, dup, qos, retain));
	}

}
