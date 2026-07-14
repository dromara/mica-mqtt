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
import org.dromara.mica.mqtt.codec.properties.BinaryProperty;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.StringProperty;
import org.dromara.mica.mqtt.codec.properties.UserProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR2 透传保证：PUBLISH 消息级别的四个常用属性
 * <ul>
 *     <li>Payload Format Indicator (0x01)</li>
 *     <li>Message Expiry Interval (0x02)</li>
 *     <li>Content Type (0x03)</li>
 *     <li>Response Topic (0x08)</li>
 *     <li>Correlation Data (0x09)</li>
 * </ul>
 * 在 codec 层 encode -> decode 应当完整保留，确保上层 broker / client 不会因反序列化丢字段。
 *
 * <p>这是轻量级单测，避免对 {@code ChannelContext} 的依赖（无 mockito）。
 */
class MqttPublishPropertiesRoundTripTest {

	@Test
	void payloadFormatIndicatorRoundTrip() {
		MqttPublishProperties holder = new MqttPublishProperties();
		holder.setPayloadFormatIndicator(1);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR));
	}

	@Test
	void messageExpiryIntervalRoundTrip() {
		MqttPublishProperties holder = new MqttPublishProperties();
		holder.setMessageExpiryInterval(60);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(60), decoded.getPropertyValue(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL));
	}

	@Test
	void contentTypeRoundTrip() {
		MqttPublishProperties holder = new MqttPublishProperties();
		holder.setContentType("application/json");

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals("application/json", decoded.getPropertyValue(MqttPropertyType.CONTENT_TYPE));
	}

	@Test
	void responseTopicRoundTrip() {
		MqttPublishProperties holder = new MqttPublishProperties();
		holder.setResponseTopic("a/b/response");

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals("a/b/response", decoded.getPropertyValue(MqttPropertyType.RESPONSE_TOPIC));
	}

	@Test
	void correlationDataRoundTrip() {
		MqttPublishProperties holder = new MqttPublishProperties();
		byte[] payload = "request-12345".getBytes(StandardCharsets.UTF_8);
		holder.setCorrelationData(payload);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertArrayEquals(payload, decoded.getPropertyValue(MqttPropertyType.CORRELATION_DATA));
	}

	@Test
	void allFourPropertiesTogetherRoundTrip() {
		MqttPublishProperties holder = new MqttPublishProperties();
		holder.setPayloadFormatIndicator(1)
			.setContentType("text/plain")
			.setResponseTopic("a/b/resp")
			.setCorrelationData("cid-001".getBytes(StandardCharsets.UTF_8));

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR));
		assertEquals("text/plain", decoded.getPropertyValue(MqttPropertyType.CONTENT_TYPE));
		assertEquals("a/b/resp", decoded.getPropertyValue(MqttPropertyType.RESPONSE_TOPIC));
		assertArrayEquals("cid-001".getBytes(StandardCharsets.UTF_8),
			decoded.getPropertyValue(MqttPropertyType.CORRELATION_DATA));
	}

	@Test
	void emptyPropertiesRoundTripStaysEmpty() {
		MqttPublishProperties holder = new MqttPublishProperties();
		assertTrue(holder.getProperties().isEmpty());

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertNotNull(decoded);
		assertTrue(decoded.isEmpty());
	}

	@Test
	void userPropertyWithAllFourPayloadPropertiesRoundTrip() {
		MqttProperties source = new MqttProperties();
		source.add(new IntegerProperty(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR, 1));
		source.add(new StringProperty(MqttPropertyType.CONTENT_TYPE, "text/plain"));
		source.add(new StringProperty(MqttPropertyType.RESPONSE_TOPIC, "a/b/resp"));
		source.add(new BinaryProperty(MqttPropertyType.CORRELATION_DATA, "cid-1".getBytes(StandardCharsets.UTF_8)));
		source.add(new UserProperty("traceId", "abc-123"));
		source.add(new UserProperty("sender", "broker"));

		MqttProperties decoded = roundTrip(source);
		assertFalse(decoded.isEmpty());
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR));
		assertEquals("text/plain", decoded.getPropertyValue(MqttPropertyType.CONTENT_TYPE));
		assertEquals("a/b/resp", decoded.getPropertyValue(MqttPropertyType.RESPONSE_TOPIC));
		assertArrayEquals("cid-1".getBytes(StandardCharsets.UTF_8),
			decoded.getPropertyValue(MqttPropertyType.CORRELATION_DATA));
		List<UserProperty> userProperties = decoded.getProperties(MqttPropertyType.USER_PROPERTY.value())
			.stream()
			.map(p -> (UserProperty) p)
			.collect(java.util.stream.Collectors.toList());
		assertEquals(2, userProperties.size());
		assertTrue(userProperties.contains(new UserProperty("traceId", "abc-123")));
		assertTrue(userProperties.contains(new UserProperty("sender", "broker")));
	}

	@Test
	void gettersReturnNullWhenUnset() {
		MqttPublishProperties holder = new MqttPublishProperties();
		assertNull(holder.getPayloadFormatIndicator());
		assertNull(holder.getMessageExpiryInterval());
		assertNull(holder.getContentType());
		assertNull(holder.getResponseTopic());
		assertNull(holder.getCorrelationData());
	}

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}
}
