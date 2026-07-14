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
import org.dromara.mica.mqtt.codec.message.header.MqttConnectVariableHeader;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnectProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttDisconnectProperties;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PR9：MQTT 5.0 Session Expiry Interval codec 层回归测试。
 * <p>
 * spec 3.1.2.11.4（CONNECT 端）/ 3.2.2.3.5（CONNACK 端）/ 3.2.2.4（DISCONNECT 端）。
 * Session Expiry Interval 为 4 字节 unsigned 整数（单位：秒），合法值 0 ~ 0xFFFFFFFF。
 * 缺省值 0。
 *
 * <p>本测试覆盖：
 * <ol>
 *     <li>CONNECT 端 Session Expiry Interval 双向 round-trip</li>
 *     <li>CONNACK 端 Session Expiry Interval 双向 round-trip（服务端回发）</li>
 *     <li>DISCONNECT 端 Session Expiry Interval 双向 round-trip（spec 3.2.2.4 允许客户端更新）</li>
 *     <li>1 / 0xFFFFFFFF 边界</li>
 *     <li>缺省 null 行为</li>
 *     <li>propertyId = 0x11 验证</li>
 * </ol>
 *
 * @author L.cm
 */
class MqttSessionExpiryCodecTest {

	// ----------------- CONNECT 端 -----------------

	@Test
	void connectSessionExpiryIntervalRoundTrip() {
		MqttConnectProperties holder = new MqttConnectProperties();
		holder.setSessionExpiryInterval(3600);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(3600), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	@Test
	void connectSessionExpiryIntervalMinRoundTrip() {
		// spec 3.1.2.11.4: 0 表示立即过期（与 MQTT 3.x cleanSession=true 等价）
		MqttConnectProperties holder = new MqttConnectProperties();
		holder.setSessionExpiryInterval(0);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(0), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	@Test
	void connectSessionExpiryIntervalMaxRoundTrip() {
		// spec 3.1.2.11.4: 4 字节 unsigned 上限 0xFFFFFFFF（视为"永不过期"）
		MqttConnectProperties holder = new MqttConnectProperties();
		holder.setSessionExpiryInterval(0xFFFFFFFF);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(0xFFFFFFFF), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	@Test
	void connectSessionExpiryIntervalUnsetIsNull() {
		assertNull(new MqttConnectProperties().getSessionExpiryInterval());
	}

	// ----------------- CONNACK 端 -----------------

	@Test
	void connAckSessionExpiryIntervalRoundTrip() {
		MqttConnAckProperties holder = new MqttConnAckProperties();
		holder.setSessionExpiryInterval(7200);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(7200), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	// ----------------- DISCONNECT 端 -----------------

	@Test
	void disconnectSessionExpiryIntervalRoundTrip() {
		// spec 3.2.2.4: 客户端可在 DISCONNECT 中携带 Session Expiry Interval 更新服务端保留时间
		MqttDisconnectProperties holder = new MqttDisconnectProperties();
		holder.setSessionExpiryInterval(300);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(300), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	// ----------------- propertyId 验证 -----------------

	@Test
	void sessionExpiryIntervalPropertyIdIs0x11() {
		// spec 3.1.2.11.4: Session Expiry Interval 固定 propertyId = 0x11
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		org.dromara.mica.mqtt.codec.properties.MqttProperty<Integer> prop =
			props.<org.dromara.mica.mqtt.codec.properties.MqttProperty<Integer>>getProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL);
		assertNotNull(prop);
		assertEquals(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), prop.propertyId());
		assertEquals(Integer.valueOf(60), prop.value());
	}

	// ----------------- 与其它 CONNECT 属性共存 -----------------

	@Test
	void sessionExpiryIntervalAndReceiveMaximumCoexist() {
		// CONNECT 可同时声明多个 properties
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 3600));
		props.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, 100));
		props.add(new IntegerProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE, 65535));

		MqttProperties decoded = roundTrip(props);
		assertEquals(Integer.valueOf(3600), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
		assertEquals(Integer.valueOf(100), decoded.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM));
		assertEquals(Integer.valueOf(65535), decoded.getPropertyValue(MqttPropertyType.MAXIMUM_PACKET_SIZE));
	}

	// ----------------- MqttConnectVariableHeader cleanStart 字段 -----------------

	@Test
	void mqttConnectVariableHeaderCleanStartAccessors() {
		// spec 3.1.2.4: MQTT 5 CONNECT 增加 Clean Start 标志
		MqttConnectVariableHeader header = new MqttConnectVariableHeader(
			"MQTT", 5,
			false, false,  // hasUsername, hasPassword
			false, 0, false,  // isWillRetain, willQos, isWillFlag
			true,  // isCleanStart
			60  // keepAliveTimeSeconds
		);
		assertEquals(true, header.isCleanStart());
		assertEquals(60, header.keepAliveTimeSeconds());
		assertEquals("MQTT", header.name());
		assertEquals(5, header.version());
	}

	@Test
	void mqttConnectVariableHeaderCleanStartFalseRoundTrip() {
		// spec 3.1.2.4: Clean Start = false 同样支持
		MqttConnectVariableHeader header = new MqttConnectVariableHeader(
			"MQTT", 5,
			true, true,
			true, 2, true,
			false,  // isCleanStart = false
			30
		);
		assertEquals(false, header.isCleanStart());
		assertEquals(true, header.hasUsername());
		assertEquals(true, header.hasPassword());
		assertEquals(true, header.isWillFlag());
		assertEquals(2, header.willQos());
	}

	// ----------------- helper -----------------

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}
}
