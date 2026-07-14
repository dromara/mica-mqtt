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
import org.dromara.mica.mqtt.codec.message.properties.MqttWillPublishProperties;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PR5：MQTT 5.0 Will Delay Interval codec 层回归测试。
 *
 * <p>覆盖维度：
 * <ol>
 *     <li>{@link MqttWillPublishProperties#setWillDelayInterval(int)} / {@link MqttWillPublishProperties#getWillDelayInterval()} 双向 round-trip</li>
 *     <li>CONNECT 端 Session Expiry Interval 属性 round-trip（与 Will Delay 联合判定）</li>
 *     <li>Will Properties 中其他常用字段（Payload Format Indicator / Content Type / Response Topic / Correlation Data / User Property）一并保留</li>
 * </ol>
 *
 * <p>实际的"延迟调度 + 取消"逻辑在 {@code MqttServerAioListener} / {@code MqttDisConnectHandler}，
 * 需要完整的 ChannelContext 依赖；本测试仅覆盖 codec 层不丢字段。
 *
 * @author L.cm
 */
class MqttWillDelayIntervalRoundTripTest {

	// ----------------- Will Delay Interval (0x18) -----------------

	@Test
	void willDelayIntervalRoundTrip() {
		MqttWillPublishProperties holder = new MqttWillPublishProperties();
		holder.setWillDelayInterval(60);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(60), decoded.getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL));
	}

	@Test
	void willDelayIntervalZeroRoundTrip() {
		MqttWillPublishProperties holder = new MqttWillPublishProperties();
		holder.setWillDelayInterval(0);

		MqttProperties decoded = roundTrip(holder.getProperties());
		// spec 3.1.3.5.1: 0 表示服务端必须立即发送 Will
		assertEquals(Integer.valueOf(0), decoded.getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL));
	}

	@Test
	void willDelayIntervalMaxRoundTrip() {
		// spec 3.1.3.5.1: 4 字节 unsigned 上限 0xFFFFFFFF
		MqttWillPublishProperties holder = new MqttWillPublishProperties();
		holder.setWillDelayInterval(0xFFFFFFFF);

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(0xFFFFFFFF), decoded.getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL));
	}

	@Test
	void willDelayIntervalUnsetIsNull() {
		assertNull(new MqttWillPublishProperties().getWillDelayInterval());
	}

	// ----------------- Session Expiry Interval (0x11) 联合校验 -----------------

	@Test
	void sessionExpiryIntervalRoundTrip() {
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 3600));

		MqttProperties decoded = roundTrip(props);
		assertEquals(Integer.valueOf(3600), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	@Test
	void willDelayAndSessionExpiryTogetherRoundTrip() {
		// spec 3.1.3.5: server 判定 willDelay 是否生效，需要 sessionExpiryInterval >= willDelayInterval
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 120));
		props.add(new IntegerProperty(MqttPropertyType.WILL_DELAY_INTERVAL, 60));

		MqttProperties decoded = roundTrip(props);
		Integer sessExp = decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL);
		Integer willDelay = decoded.getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL);
		assertEquals(Integer.valueOf(120), sessExp);
		assertEquals(Integer.valueOf(60), willDelay);
		// 二者方向独立：缺一个不影响另一个
		assertNotNull(sessExp);
		assertNotNull(willDelay);
	}

	// ----------------- Will Properties 多字段 -----------------

	@Test
	void willPropertiesAllFieldsRoundTrip() {
		MqttWillPublishProperties holder = new MqttWillPublishProperties();
		holder.setWillDelayInterval(30)
			.setPayloadFormatIndicator(1)
			.setMessageExpiryInterval(120)
			.setContentType("application/json")
			.setResponseTopic("a/b/resp")
			.setCorrelationData("cid-1".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		MqttProperties decoded = roundTrip(holder.getProperties());
		assertEquals(Integer.valueOf(30), decoded.getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL));
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR));
		assertEquals(Integer.valueOf(120), decoded.getPropertyValue(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL));
		assertEquals("application/json", decoded.getPropertyValue(MqttPropertyType.CONTENT_TYPE));
		assertEquals("a/b/resp", decoded.getPropertyValue(MqttPropertyType.RESPONSE_TOPIC));
		assertEquals("cid-1", new String(decoded.getPropertyValue(MqttPropertyType.CORRELATION_DATA), java.nio.charset.StandardCharsets.UTF_8));
	}

	@Test
	void willPropertiesWithUserPropertyRoundTrip() {
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.WILL_DELAY_INTERVAL, 30));
		props.add(new org.dromara.mica.mqtt.codec.properties.UserProperty("traceId", "abc"));

		MqttProperties decoded = roundTrip(props);
		assertEquals(Integer.valueOf(30), decoded.getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL));
		// 字符串型 UserProperty
		org.dromara.mica.mqtt.codec.properties.UserProperty up = decoded.getProperties(MqttPropertyType.USER_PROPERTY.value())
			.stream()
			.map(p -> (org.dromara.mica.mqtt.codec.properties.UserProperty) p)
			.findFirst()
			.orElse(null);
		assertNotNull(up);
		assertEquals("abc", up.value().value);
		assertEquals("traceId", up.value().key);
	}

	// ----------------- willDelay 与 sessionExpiry 的语义关联（方向互不干扰） -----------------

	@Test
	void willPropertiesAndConnectPropertiesAreIndependent() {
		// Will Properties 携带 WillDelayInterval，CONNECT Properties 携带 SessionExpiryInterval：
		// 两侧独立编码，本测试保证解码时互不串味。
		MqttProperties willProps = new MqttProperties();
		willProps.add(new IntegerProperty(MqttPropertyType.WILL_DELAY_INTERVAL, 30));

		MqttProperties connProps = new MqttProperties();
		connProps.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 30));

		MqttProperties decodedWill = roundTrip(willProps);
		MqttProperties decodedConn = roundTrip(connProps);

		// Will 端不应有 Session Expiry
		Integer sessExpInWill = decodedWill.<Integer>getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL);
		assertNull(sessExpInWill);
		// CONNECT 端不应有 Will Delay
		Integer willDelayInConn = decodedConn.<Integer>getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL);
		assertNull(willDelayInConn);
	}

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}
}
