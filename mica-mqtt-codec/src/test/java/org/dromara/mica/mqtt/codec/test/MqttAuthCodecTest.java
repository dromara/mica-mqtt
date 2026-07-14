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

package org.dromara.mica.mqtt.codec.test;

import org.dromara.mica.mqtt.codec.MqttMessageFactory;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttAuthReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttAuthBuilder;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttReasonCodeAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.properties.MqttAuthProperties;
import org.dromara.mica.mqtt.codec.properties.BinaryProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.StringProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PR8：MQTT 5.0 AUTH 报文 codec 层回归测试（spec 3.15 / 4.12）。
 *
 * <p>AUTH 是 MQTT 5 引入的扩展认证报文类型。固定 QoS=0、不带 packetId。携带
 * {@code Authentication Method (0x15)} / {@code Authentication Data (0x16)} / {@code Reason String} 等
 * 属性；reason code 包括 0x00 SUCCESS、0x18 CONTINUE_AUTHENTICATION、0x19 RE_AUTHENTICATE。
 *
 * <p>本测试覆盖：
 * <ol>
 *     <li>AUTH 报文结构（fixed header / variable header / reason code）</li>
 *     <li>通过 {@link MqttAuthBuilder} 构造 AUTH 报文</li>
 *     <li>properties 段 round-trip（method + data + reason string）</li>
 *     <li>3 种 reason code 编码正确</li>
 *     <li>AUTH 的 message type 标识</li>
 * </ol>
 *
 * @author L.cm
 */
class MqttAuthCodecTest {

	// ----------------- AUTH 报文结构 -----------------

	@Test
	void authMessageType() {
		// spec 3.15：AUTH 是 QoS0、no retain、不带 packetId
		MqttAuthBuilder builder = new MqttAuthBuilder()
			.reasonCode(MqttAuthReasonCode.SUCCESS);
		MqttMessage message = builder.build();
		MqttFixedHeader fixedHeader = message.fixedHeader();
		assertEquals(MqttMessageType.AUTH, fixedHeader.messageType());
		assertEquals(MqttQoS.QOS0, fixedHeader.qosLevel());
		assertEquals(false, fixedHeader.isRetain());
	}

	@Test
	void authMessageDefaultReasonCodeIsSuccess() {
		// 未指定 reason code 时默认为 SUCCESS（0x00）
		MqttMessage message = new MqttAuthBuilder().build();
		MqttReasonCodeAndPropertiesVariableHeader variableHeader =
			(MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();
		assertEquals(MqttAuthReasonCode.SUCCESS.value(), variableHeader.reasonCode());
	}

	@Test
	void authMessageReasonCodeContinueAuth() {
		MqttMessage message = new MqttAuthBuilder()
			.reasonCode(MqttAuthReasonCode.CONTINUE_AUTHENTICATION)
			.build();
		MqttReasonCodeAndPropertiesVariableHeader variableHeader =
			(MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();
		assertEquals(MqttAuthReasonCode.CONTINUE_AUTHENTICATION.value(), variableHeader.reasonCode());
	}

	@Test
	void authMessageReasonCodeReAuthenticate() {
		MqttMessage message = new MqttAuthBuilder()
			.reasonCode(MqttAuthReasonCode.RE_AUTHENTICATE)
			.build();
		MqttReasonCodeAndPropertiesVariableHeader variableHeader =
			(MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();
		assertEquals(MqttAuthReasonCode.RE_AUTHENTICATE.value(), variableHeader.reasonCode());
	}

	// ----------------- MqttAuthProperties round-trip -----------------

	@Test
	void authPropertiesMethodAndDataRoundTrip() {
		MqttAuthProperties holder = new MqttAuthProperties();
		holder.setAuthenticationMethod("GS2-KRB5")
			.setAuthenticationData("challenge-bytes".getBytes(StandardCharsets.UTF_8));

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals("GS2-KRB5", decoded.getPropertyValue(MqttPropertyType.AUTHENTICATION_METHOD));
		byte[] data = decoded.getPropertyValue(MqttPropertyType.AUTHENTICATION_DATA);
		assertNotNull(data);
		assertEquals("challenge-bytes", new String(data, StandardCharsets.UTF_8));
	}

	@Test
	void authPropertiesReasonStringRoundTrip() {
		MqttAuthProperties holder = new MqttAuthProperties();
		holder.setAuthenticationMethod("SCRAM-SHA-256")
			.setReasonString("step 1 done");

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals("SCRAM-SHA-256", decoded.getPropertyValue(MqttPropertyType.AUTHENTICATION_METHOD));
		assertEquals("step 1 done", decoded.getPropertyValue(MqttPropertyType.REASON_STRING));
	}

	@Test
	void authPropertiesUnsetMethodIsNull() {
		assertNull(new MqttAuthProperties().getAuthenticationMethod());
	}

	@Test
	void authPropertiesUnsetDataIsNull() {
		assertNull(new MqttAuthProperties().getAuthenticationData());
	}

	// ----------------- 直接构造 properties + AUTH 报文 -----------------

	@Test
	void buildAuthViaConsumer() {
		MqttMessage message = new MqttAuthBuilder()
			.reasonCode(MqttAuthReasonCode.CONTINUE_AUTHENTICATION)
			.properties(props -> props
				.setAuthenticationMethod("MyMethod")
				.setAuthenticationData("c1".getBytes(StandardCharsets.UTF_8)))
			.build();
		MqttReasonCodeAndPropertiesVariableHeader variableHeader =
			(MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();
		assertEquals(MqttAuthReasonCode.CONTINUE_AUTHENTICATION.value(), variableHeader.reasonCode());
		assertEquals("MyMethod", variableHeader.properties().<String>getPropertyValue(MqttPropertyType.AUTHENTICATION_METHOD));
		byte[] data = variableHeader.properties().<byte[]>getPropertyValue(MqttPropertyType.AUTHENTICATION_DATA);
		assertNotNull(data);
		assertEquals("c1", new String(data, StandardCharsets.UTF_8));
	}

	// ----------------- 通过 MqttMessageFactory 解码 -----------------

	@Test
	void mqttMessageTypeAuthIsRegistered() {
		// spec 0xF 是 AUTH
		assertEquals(0xF, MqttMessageType.AUTH.value());
	}

	// ----------------- 多 AUTH 报文 round-trip 一致性 -----------------

	@Test
	void authWithUserPropertyRoundTrip() {
		MqttAuthProperties holder = new MqttAuthProperties();
		holder.setAuthenticationMethod("SCRAM-SHA-256")
			.setAuthenticationData("nonce-1".getBytes(StandardCharsets.UTF_8))
			.addUserProperty("trace", "abc-123");

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals("SCRAM-SHA-256", decoded.getPropertyValue(MqttPropertyType.AUTHENTICATION_METHOD));
		assertEquals("nonce-1", new String(decoded.getPropertyValue(MqttPropertyType.AUTHENTICATION_DATA), StandardCharsets.UTF_8));
		org.dromara.mica.mqtt.codec.properties.UserProperty up = decoded.getProperties(MqttPropertyType.USER_PROPERTY.value())
			.stream()
			.map(p -> (org.dromara.mica.mqtt.codec.properties.UserProperty) p)
			.findFirst()
			.orElse(null);
		assertNotNull(up);
		assertEquals("abc-123", up.value().value);
	}

	@Test
	void rawIntegerAndBinaryPropertiesPreserveType() {
		// 通过原始 StringProperty/BinaryProperty 构造，验证 propertyId 0x15 / 0x16 正确
		MqttProperties props = new MqttProperties();
		props.add(new StringProperty(MqttPropertyType.AUTHENTICATION_METHOD, "X-Method"));
		props.add(new BinaryProperty(MqttPropertyType.AUTHENTICATION_DATA, "X-Data".getBytes(StandardCharsets.UTF_8)));

		MqttProperties decoded = roundTrip(props);
		assertEquals("X-Method", decoded.getPropertyValue(MqttPropertyType.AUTHENTICATION_METHOD));
		byte[] data = decoded.getPropertyValue(MqttPropertyType.AUTHENTICATION_DATA);
		assertNotNull(data);
		assertEquals("X-Data", new String(data, StandardCharsets.UTF_8));
	}

	// ----------------- helper -----------------

	private static MqttProperties roundTrip(MqttProperties source) {
		// 通过 BinaryEncoder/Decoder（不依赖网络）做 codec round-trip
		return org.dromara.mica.mqtt.codec.MqttDecoder.decodeProperties(org.dromara.mica.mqtt.codec.MqttEncoder.encodeProperties(source));
	}
}
