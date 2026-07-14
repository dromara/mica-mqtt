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

import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.MqttEncoder;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnectProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR3：MQTT 5.0 请求/响应模式 codec 层回归测试。
 *
 * <p>覆盖属性：
 * <ul>
 *     <li>CONNECT 0x19 {@code Request Response Information} (1 字节布尔) — 客户端声明是否希望服务器下发 Response Information</li>
 *     <li>CONNACK 0x1A {@code Response Information} (UTF-8 字符串) — 服务端在客户端请求时下发</li>
 * </ul>
 *
 * <p>这些测试通过 {@code set -> encodeProperties -> decodeProperties -> get} 链路验证两个属性在 codec 层不丢值，
 * 防御 {@code BooleanProperty} / {@code StringProperty} 派生类型与 getter 期望不一致可能引发的 ClassCastException。
 *
 * <p>spec 3.1.2.3.10：Request Response Information 缺省为 0（false），客户端必须显式置 1 才会被服务端识别。
 *
 * @author L.cm
 */
class MqttResponseInformationRoundTripTest {

	// ----------------- Request Response Information (CONNECT 0x19) -----------------

	@Test
	void requestResponseInformationTrueRoundTrip() {
		MqttConnectProperties properties = new MqttConnectProperties();
		properties.setRequestResponseInformation(true);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(Boolean.TRUE, decoded.getBooleanPropertyValue(MqttPropertyType.REQUEST_RESPONSE_INFORMATION));
	}

	@Test
	void requestResponseInformationFalseRoundTrip() {
		MqttConnectProperties properties = new MqttConnectProperties();
		properties.setRequestResponseInformation(false);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(Boolean.FALSE, decoded.getBooleanPropertyValue(MqttPropertyType.REQUEST_RESPONSE_INFORMATION));
	}

	@Test
	void requestResponseInformationUnsetIsNull() {
		// spec 3.1.2.3.10: 缺省值语义由 getter 解析；codec 层属性本身未设置时返回 null。
		assertNull(new MqttConnectProperties().getRequestResponseInformation());
	}

	// ----------------- Response Information (CONNACK 0x1A) -----------------

	@Test
	void responseInformationRoundTrip() {
		MqttConnAckProperties properties = new MqttConnAckProperties();
		String expected = "extra/response/topic/abc";
		properties.setResponseInformation(expected);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(expected, decoded.getPropertyValue(MqttPropertyType.RESPONSE_INFORMATION));
	}

	@Test
	void responseInformationUnsetIsNull() {
		assertNull(new MqttConnAckProperties().getResponseInformation());
	}

	@Test
	void responseInformationEmptyStringRoundTrip() {
		// 规范允许空字符串；服务端一般不下发，但 codec 层应当原样保留。
		MqttConnAckProperties properties = new MqttConnAckProperties();
		properties.setResponseInformation("");

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertNotNull(decoded);
		assertEquals("", decoded.getPropertyValue(MqttPropertyType.RESPONSE_INFORMATION));
	}

	@Test
	void responseInformationWithSpecialCharsRoundTrip() {
		String value = "回复主题/{var}?q=1#frag";
		MqttConnAckProperties properties = new MqttConnAckProperties();
		properties.setResponseInformation(value);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(value, decoded.getPropertyValue(MqttPropertyType.RESPONSE_INFORMATION));
	}

	@Test
	void requestAndResponseInformationAreIndependent() {
		// CONNECT 与 CONNACK 是不同报文方向，应使用不同 propertyId；本测试保证不会混用。
		MqttConnectProperties connectProperties = new MqttConnectProperties();
		connectProperties.setRequestResponseInformation(true);

		MqttConnAckProperties connAckProperties = new MqttConnAckProperties();
		connAckProperties.setResponseInformation("broker/inbox/123");

		MqttProperties decodedConnect = roundTrip(connectProperties.getProperties());
		MqttProperties decodedConnAck = roundTrip(connAckProperties.getProperties());

		// CONNECT 一侧不应有 Response Information
		assertNull(decodedConnect.getPropertyValue(MqttPropertyType.RESPONSE_INFORMATION));
		// CONNACK 一侧不应有 Request Response Information
		assertNull(decodedConnAck.getBooleanPropertyValue(MqttPropertyType.REQUEST_RESPONSE_INFORMATION));
		// 各自属性应正确恢复
		assertEquals(Boolean.TRUE, decodedConnect.getBooleanPropertyValue(MqttPropertyType.REQUEST_RESPONSE_INFORMATION));
		assertEquals("broker/inbox/123", decodedConnAck.getPropertyValue(MqttPropertyType.RESPONSE_INFORMATION));
	}

	@Test
	void connAckWithResponseInformationIsNotEmpty() {
		MqttConnAckProperties properties = new MqttConnAckProperties();
		properties.setResponseInformation("broker/inbox");

		assertFalse(properties.getProperties().isEmpty());
		assertTrue(properties.getProperties().getProperty(MqttPropertyType.RESPONSE_INFORMATION) != null);
	}

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}
}
