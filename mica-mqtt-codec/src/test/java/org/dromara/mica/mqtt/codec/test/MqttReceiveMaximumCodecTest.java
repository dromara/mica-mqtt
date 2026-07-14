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
import org.dromara.mica.mqtt.codec.message.properties.MqttConnectProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PR7：MQTT 5.0 Receive Maximum codec 层回归测试。
 * <p>
 * spec 3.1.2.11.4：客户端在 CONNECT 中携带 Receive Maximum（0x21，2 字节 unsigned），
 * 告知服务端"客户端允许同时在途的 QoS>0 PUBLISH 数量"。1 ~ 65535，缺省 65535。
 * spec 3.2.2.3.4：服务端在 CONNACK 中携带 Server Reference + Receive Maximum 告知客户端。
 *
 * <p>本测试覆盖：
 * <ol>
 *     <li>CONNECT 端 Receive Maximum round-trip</li>
 *     <li>CONNACK 端 Receive Maximum round-trip</li>
 *     <li>1 / 65535 边界</li>
 *     <li>缺省 null 行为</li>
 * </ol>
 *
 * @author L.cm
 */
class MqttReceiveMaximumCodecTest {

	// ----------------- CONNECT 端 -----------------

	@Test
	void connectReceiveMaximumRoundTrip() {
		MqttConnectProperties holder = new MqttConnectProperties();
		holder.setReceiveMaximum(100);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(100), decoded.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM));
	}

	@Test
	void connectReceiveMaximumMinRoundTrip() {
		// spec 3.1.2.11.4: 最小值 1（不允许 0）
		MqttConnectProperties holder = new MqttConnectProperties();
		holder.setReceiveMaximum(1);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM));
	}

	@Test
	void connectReceiveMaximumMaxRoundTrip() {
		// spec 3.1.2.11.4: 2 字节 unsigned 上限 65535（缺省值）
		MqttConnectProperties holder = new MqttConnectProperties();
		holder.setReceiveMaximum(65535);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(65535), decoded.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM));
	}

	@Test
	void connectReceiveMaximumUnsetIsNull() {
		assertEquals(null, new MqttConnectProperties().getReceiveMaximum());
	}

	// ----------------- CONNACK 端 -----------------

	@Test
	void connAckReceiveMaximumRoundTrip() {
		MqttConnAckProperties holder = new MqttConnAckProperties();
		holder.setReceiveMaximum(50);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(50), decoded.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM));
	}

	// ----------------- 通过原始 IntegerProperty 验证 0x21 propertyId -----------------

	@Test
	void receiveMaximumPropertyIdIs0x21() {
		// spec 3.1.2.11.4: Receive Maximum 固定 propertyId = 0x21
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, 5));
		org.dromara.mica.mqtt.codec.properties.MqttProperty<Integer> prop =
			props.<org.dromara.mica.mqtt.codec.properties.MqttProperty<Integer>>getProperty(MqttPropertyType.RECEIVE_MAXIMUM);
		assertNotNull(prop);
		assertEquals(MqttPropertyType.RECEIVE_MAXIMUM.value(), prop.propertyId());
		assertEquals(Integer.valueOf(5), prop.value());
	}

	// ----------------- Receive Maximum 与 Session Expiry Interval 共同存在 -----------------

	@Test
	void receiveMaximumAndSessionExpiryCoexist() {
		// 客户端 CONNECT 可同时声明这两个
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, 10));
		props.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 3600));

		MqttProperties decoded = roundTrip(props);
		assertEquals(Integer.valueOf(10), decoded.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM));
		assertEquals(Integer.valueOf(3600), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}
}
