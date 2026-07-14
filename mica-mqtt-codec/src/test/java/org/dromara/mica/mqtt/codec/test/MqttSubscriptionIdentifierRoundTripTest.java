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
import org.dromara.mica.mqtt.codec.message.properties.MqttPublishProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttSubscribeProperties;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PR6：MQTT 5.0 Subscription Identifier codec 层回归测试。
 *
 * <p>spec 3.3.2.3.5：Subscription Identifier 在 SUBSCRIBE 与 PUBLISH 报文中均以 varint 形式出现，
 * 合法值 1 ~ 268,435,455（4 字节 varint 允许的 31 位正数最大值）。
 *
 * <p>本测试覆盖：
 * <ol>
 *     <li>SUBSCRIBE 端：通过 {@link MqttSubscribeProperties} 设置并保留值</li>
 *     <li>PUBLISH 端：通过 {@link MqttPublishProperties} 设置并保留值</li>
 *     <li>跨端互通：两端使用同一 propertyId 0x0B（SUBSCRIPTION_IDENTIFIER），编码后 byte 数组一致</li>
 *     <li>边界：缺省（null）/ 1 / 268,435,455</li>
 * </ol>
 *
 * @author L.cm
 */
class MqttSubscriptionIdentifierRoundTripTest {

	// ----------------- SUBSCRIBE 端 (MqttSubscribeProperties) -----------------

	@Test
	void subscribeSubscriptionIdentifierRoundTrip() {
		MqttSubscribeProperties holder = new MqttSubscribeProperties();
		holder.setSubscriptionIdentifier(1);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER));
	}

	@Test
	void subscribeSubscriptionIdentifierMaxRoundTrip() {
		MqttSubscribeProperties holder = new MqttSubscribeProperties();
		holder.setSubscriptionIdentifier(0x0FFFFFFF);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(0x0FFFFFFF), decoded.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER));
	}

	@Test
	void subscribeSubscriptionIdentifierUnsetIsNull() {
		assertNull(new MqttSubscribeProperties().getSubscriptionIdentifier());
	}

	// ----------------- PUBLISH 端 (MqttPublishProperties) -----------------

	@Test
	void publishSubscriptionIdentifierRoundTrip() {
		MqttPublishProperties holder = new MqttPublishProperties();
		holder.setSubscriptionIdentifier(42);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(42), decoded.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER));
	}

	@Test
	void publishSubscriptionIdentifierUnsetIsNull() {
		assertNull(new MqttPublishProperties().getSubscriptionIdentifier());
	}

	// ----------------- 跨端互通 -----------------

	@Test
	void subscribeAndPublishUseSamePropertyId() {
		// spec 3.3.2.3.5：两端都是 propertyId 0x0B，编码字节应该一致。
		MqttSubscribeProperties subHolder = new MqttSubscribeProperties();
		subHolder.setSubscriptionIdentifier(7);
		MqttPublishProperties pubHolder = new MqttPublishProperties();
		pubHolder.setSubscriptionIdentifier(7);

		MqttProperties subDecoded = roundTrip(subHolder.getProperties());
		MqttProperties pubDecoded = roundTrip(pubHolder.getProperties());

		Integer subValue = subDecoded.<Integer>getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER);
		Integer pubValue = pubDecoded.<Integer>getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER);
		assertNotNull(subValue);
		assertNotNull(pubValue);
		assertEquals(subValue, pubValue);
		assertEquals(Integer.valueOf(7), subValue);
	}

	@Test
	void subscriptionIdentifierCoexistsWithOtherProperties() {
		// 一个完整 PUBLISH 的 properties 段：Subscription Identifier + Response Topic + Content Type
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 100));
		props.add(new org.dromara.mica.mqtt.codec.properties.StringProperty(MqttPropertyType.RESPONSE_TOPIC, "a/b/resp"));
		props.add(new org.dromara.mica.mqtt.codec.properties.StringProperty(MqttPropertyType.CONTENT_TYPE, "text/plain"));

		MqttProperties decoded = roundTrip(props);
		assertEquals(Integer.valueOf(100), decoded.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER));
		assertEquals("a/b/resp", decoded.getPropertyValue(MqttPropertyType.RESPONSE_TOPIC));
		assertEquals("text/plain", decoded.getPropertyValue(MqttPropertyType.CONTENT_TYPE));
	}

	@Test
	void subscriptionIdentifierWithUserProperty() {
		// spec 3.3.2.3.5：PUBLISH 端 subscriptionId 通常伴随 0~N 个 User Property
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 5));
		props.add(new org.dromara.mica.mqtt.codec.properties.UserProperty("traceId", "abc-123"));

		MqttProperties decoded = roundTrip(props);
		assertEquals(Integer.valueOf(5), decoded.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER));
		org.dromara.mica.mqtt.codec.properties.UserProperty up = decoded.getProperties(MqttPropertyType.USER_PROPERTY.value())
			.stream()
			.map(p -> (org.dromara.mica.mqtt.codec.properties.UserProperty) p)
			.findFirst()
			.orElse(null);
		assertNotNull(up);
		assertEquals("abc-123", up.value().value);
		assertEquals("traceId", up.value().key);
	}

	// ----------------- 与现有 PUBLISH 属性集成 -----------------

	@Test
	void publishPropertiesSubscriptionIdentifierViaBuilder() {
		// 业务方通过链式调用设置
		MqttPublishProperties holder = new MqttPublishProperties();
		holder.setSubscriptionIdentifier(123);

		// 显式取 properties（与 publish 链中的 .properties(p -> p.setSubscriptionIdentifier(...)) 等价）
		MqttProperties properties = holder.getProperties();
		assertNotNull(properties);
		assertEquals(Integer.valueOf(123), properties.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER));
	}

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}
}
