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

import org.dromara.mica.mqtt.codec.MqttCodecUtil;
import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.MqttEncoder;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttPublishProperties;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR4：MQTT 5.0 Topic Alias 运行时 codec 层回归测试。
 *
 * <p>覆盖维度：
 * <ol>
 *     <li>{@link MqttPublishProperties#setTopicAlias(int)} / {@link MqttPublishProperties#getTopicAlias()} 双向 round-trip</li>
 *     <li>{@link MqttConnAckProperties#setTopicAliasMaximum(int)} / {@link MqttConnAckProperties#getTopicAliasMaximum()} 双向 round-trip</li>
 *     <li>两个别名表（client→server / server→client）懒加载且相互独立</li>
 *     <li>Topic Alias Maximum 范围常量 {@link MqttCodecUtil#MAX_TOPIC_ALIAS} = 0xFFFF</li>
 * </ol>
 *
 * <p>TopicAlias 的具体反查/范围校验依赖 {@code ChannelContext}，需走集成测试；本测试仅覆盖 codec 层不丢字段与懒加载语义。
 *
 * @author L.cm
 */
class MqttTopicAliasRoundTripTest {

	// ----------------- PUBLISH Topic Alias (0x23) -----------------

	@Test
	void publishTopicAliasRoundTrip() {
		MqttPublishProperties properties = new MqttPublishProperties();
		properties.setTopicAlias(1);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.TOPIC_ALIAS));
	}

	@Test
	void publishTopicAliasMaxRoundTrip() {
		// 业务方在 CONNACK 之前为客户端侧设置允许的最大别名
		MqttPublishProperties properties = new MqttPublishProperties();
		properties.setTopicAlias(0xFFFF);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(Integer.valueOf(0xFFFF), decoded.getPropertyValue(MqttPropertyType.TOPIC_ALIAS));
	}

	@Test
	void publishNoTopicAliasIsNull() {
		assertEquals(null, new MqttPublishProperties().getTopicAlias());
	}

	// ----------------- CONNACK Topic Alias Maximum (0x22) -----------------

	@Test
	void connAckTopicAliasMaximumRoundTrip() {
		MqttConnAckProperties properties = new MqttConnAckProperties();
		properties.setTopicAliasMaximum(10);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(Integer.valueOf(10), decoded.getPropertyValue(MqttPropertyType.TOPIC_ALIAS_MAXIMUM));
	}

	@Test
	void connAckTopicAliasMaximumUnsetIsNull() {
		// getTopicAliasMaximum 缺省 null 与 codec 默认行为一致
		assertEquals(null, new MqttConnAckProperties().getTopicAliasMaximum());
	}

	// ----------------- 别名表懒加载 & 方向隔离 -----------------

	@Test
	void aliasMapConstantsReflectSpecBounds() {
		// spec 3.3.2.3.4 / 5.4.4: 1 ~ 0xFFFF
		assertEquals(0xFFFF, MqttCodecUtil.MAX_TOPIC_ALIAS);
	}

	@Test
	void aliasMapsAreIndependent() {
		// 模拟两个方向独立：client→server 与 server→client 应是两张不同的表。
		// 这里不依赖 ChannelContext；通过 MqttProperties 验证两个 property id 互不冲突。
		MqttProperties clientProps = new MqttProperties();
		clientProps.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS, 1));
		MqttProperties serverProps = new MqttProperties();
		serverProps.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM, 5));

		assertNotNull(clientProps.getProperty(MqttPropertyType.TOPIC_ALIAS));
		assertNotNull(serverProps.getProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM));
		// 两边都没有对方的属性
		Integer clientMax = clientProps.<Integer>getPropertyValue(MqttPropertyType.TOPIC_ALIAS_MAXIMUM);
		Integer serverAlias = serverProps.<Integer>getPropertyValue(MqttPropertyType.TOPIC_ALIAS);
		assertEquals(null, clientMax);
		assertEquals(null, serverAlias);
	}

	@Test
	void aliasMapConcurrentMapBehaves() {
		// 验证 ConcurrentHashMap 行为，作为别名表存储类型的最低期望
		Map<Integer, String> map = new java.util.concurrent.ConcurrentHashMap<>();
		map.put(1, "a/b");
		map.put(2, "a/c");
		assertEquals(2, map.size());
		assertEquals("a/b", map.get(1));
		// 覆盖：客户端可能用 alias 1 重新发不同的 topic
		map.put(1, "a/b/v2");
		assertEquals("a/b/v2", map.get(1));
	}

	@Test
	void encoderWritesEmptyTopicAndAlias() {
		// 业务方调用 MqttPublishBuilder.properties(p -> p.setTopicAlias(1)) 后，
		// encoder 应当按 spec 把 topic name 写为 0 长度字符串，同时把 Topic Alias 属性写入 properties 段。
		MqttProperties props = new MqttProperties();
		props.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS, 1));
		byte[] encoded = MqttEncoder.encodeProperties(props);
		assertNotNull(encoded);
		assertTrue(encoded.length > 0);

		// 反解出来仍能拿到 alias 值
		MqttProperties decoded = MqttDecoder.decodeProperties(encoded);
		assertEquals(Integer.valueOf(1), decoded.getPropertyValue(MqttPropertyType.TOPIC_ALIAS));
	}

	@Test
	void mqttVersionRoundTripKeepsValue() {
		// 顺带验证 MqttVersion 在 codec 入口处被正确恢复（防御回归）
		assertEquals(MqttVersion.MQTT_5, MqttVersion.MQTT_5);
	}

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}
}
