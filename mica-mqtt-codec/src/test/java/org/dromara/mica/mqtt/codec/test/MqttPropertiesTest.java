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

import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperty;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.StringProperty;
import org.dromara.mica.mqtt.codec.properties.UserProperties;
import org.dromara.mica.mqtt.codec.properties.UserProperty;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 MqttProperties 行为的回归测试。
 * <p>
 * 这些测试同时覆盖三类行为：
 * <ul>
 *     <li>三个内部容器（props / subscriptionIds / userProperties）的写入与读取</li>
 *     <li>{@code listAll} 在 8 种 null/非空组合下的返回值</li>
 *     <li>{@code NO_PROPERTIES} 不可写、{@code withEmptyDefaults} 兜底</li>
 * </ul>
 */
class MqttPropertiesTest {

	private static final int UNKNOWN_PROPERTY_ID = 0xFF;

	// ----------------- 构造器 / 静态工厂 -----------------

	@Test
	void noPropertiesIsUnmodifiable() {
		assertTrue(MqttProperties.NO_PROPERTIES.isEmpty(),
			"NO_PROPERTIES 用作空容器，isEmpty 必须为 true");
		assertThrows(UnsupportedOperationException.class,
			() -> MqttProperties.NO_PROPERTIES.add(
				new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60)));
	}

	@Test
	void withEmptyDefaultsReturnsNoPropertiesForNull() {
		assertSame(MqttProperties.NO_PROPERTIES, MqttProperties.withEmptyDefaults(null));
	}

	@Test
	void withEmptyDefaultsReturnsInputForNonNull() {
		MqttProperties input = new MqttProperties();
		assertSame(input, MqttProperties.withEmptyDefaults(input));
	}

	// ----------------- isEmpty -----------------

	@Test
	void emptyOnFreshInstance() {
		assertTrue(new MqttProperties().isEmpty());
	}

	@Test
	void notEmptyAfterAddingIntegerProperty() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		assertFalse(properties.isEmpty());
	}

	// ----------------- add：常规 props -----------------

	@Test
	void addIntegerPropertyStoresInPropsMap() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));

		assertEquals(Integer.valueOf(60),
			properties.<Integer>getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	@Test
	void addSamePropertyIdTwiceReplaces() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 120));

		assertEquals(Integer.valueOf(120),
			properties.<Integer>getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	@Test
	void addStringPropertyStoresInPropsMap() {
		MqttProperties properties = new MqttProperties();
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_INFORMATION, "info"));

		assertEquals("info",
			properties.<String>getPropertyValue(MqttPropertyType.RESPONSE_INFORMATION));
	}

	// ----------------- add：Subscription ID -----------------

	@Test
	void addSubscriptionIdentifierStoresInList() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 42));
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 43));

		// getProperty 返回第一个
		assertEquals(42, properties.getProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER).value());
		// getProperties 返回全部
		List<? extends MqttProperty> list = properties.getProperties(MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value());
		assertEquals(2, list.size());
	}

	@Test
	void addSubscriptionIdentifierWithWrongTypeRejected() {
		MqttProperties properties = new MqttProperties();
		assertThrows(IllegalArgumentException.class,
			() -> properties.add(new StringProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, "oops")));
	}

	// ----------------- add：User Property 单个 -----------------

	@Test
	void addSingleUserPropertyStoresInUserList() {
		MqttProperties properties = new MqttProperties();
		properties.add(new UserProperty("k1", "v1"));
		properties.add(new UserProperty("k2", "v2"));

		UserProperties userProperties = (UserProperties) properties.getProperty(MqttPropertyType.USER_PROPERTY);
		assertNotNull(userProperties);
		assertEquals(2, userProperties.value().size());
	}

	@Test
	void addUserPropertiesBatchFlattensIntoIndividualEntries() {
		MqttProperties properties = new MqttProperties();
		UserProperties batch = new UserProperties();
		batch.add("k1", "v1");
		batch.add("k2", "v2");
		properties.add(batch);

		// getProperties 返回底层 List<UserProperty>，应当是 2 个独立的 UserProperty 而非一个 UserProperties
		List<? extends MqttProperty> list = properties.getProperties(MqttPropertyType.USER_PROPERTY.value());
		assertEquals(2, list.size());
		assertTrue(list.get(0) instanceof UserProperty);
		assertTrue(list.get(1) instanceof UserProperty);
	}

	@Test
	void addInvalidUserPropertyTypeRejected() {
		MqttProperties properties = new MqttProperties();
		assertThrows(IllegalArgumentException.class,
			() -> properties.add(new IntegerProperty(MqttPropertyType.USER_PROPERTY, 1)));
	}

	// ----------------- getProperty / getProperties 不存在时的兜底 -----------------

	@Test
	void getPropertyReturnsNullForMissingProperty() {
		MqttProperties properties = new MqttProperties();
		assertNull(properties.getProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
		assertNull(properties.getProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value()));
	}

	@Test
	void getPropertiesReturnsEmptyListForMissingProperty() {
		MqttProperties properties = new MqttProperties();
		assertTrue(properties.getProperties(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value()).isEmpty());
	}

	@Test
	void getPropertiesWrapsSinglePropertyInSingletonList() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		List<? extends MqttProperty> list = properties.getProperties(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
		assertEquals(1, list.size());
	}

	// ----------------- getBooleanPropertyValue -----------------

	@Test
	void getBooleanPropertyValueReturnsNullForMissing() {
		MqttProperties properties = new MqttProperties();
		assertNull(properties.getBooleanPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR));
	}

	@Test
	void getBooleanPropertyValueZeroIsFalse() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR, 0));
		assertEquals(Boolean.FALSE, properties.getBooleanPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR));
	}

	@Test
	void getBooleanPropertyValueNonZeroIsTrue() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR, 1));
		assertEquals(Boolean.TRUE, properties.getBooleanPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR));
	}

	// ----------------- listAll：8 种 null 组合 -----------------

	@Test
	void listAllOnEmptyReturnsEmptyList() {
		MqttProperties properties = new MqttProperties();
		Collection<? extends MqttProperty> all = properties.listAll();
		assertTrue(all.isEmpty());
	}

	@Test
	void listAllWithOnlySubscriptionIdsReturnsSubscriptionIdsDirectly() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 7));
		Collection<? extends MqttProperty> all = properties.listAll();
		// 当前实现：只 subscriptionIds 时直接返回内部 list（fast path）
		assertEquals(1, all.size());
	}

	@Test
	void listAllWithOnlyPropsReturnsPropsValuesDirectly() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		Collection<? extends MqttProperty> all = properties.listAll();
		assertEquals(1, all.size());
	}

	@Test
	void listAllMergesPropsSubscriptionIdsUserPropertiesInOrder() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 7));
		properties.add(new UserProperty("k1", "v1"));

		List<? extends MqttProperty> all = (List<? extends MqttProperty>) properties.listAll();
		// 顺序：props → subscriptionIds → userProperties(合并为一个)
		assertEquals(3, all.size());
		assertEquals(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), all.get(0).propertyId());
		assertEquals(MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value(), all.get(1).propertyId());
		assertEquals(MqttPropertyType.USER_PROPERTY.value(), all.get(2).propertyId());
		assertTrue(all.get(2) instanceof UserProperties);
	}

	@Test
	void listAllMergesMultipleUserPropertiesIntoOneUserPropertiesEntry() {
		MqttProperties properties = new MqttProperties();
		properties.add(new UserProperty("k1", "v1"));
		properties.add(new UserProperty("k2", "v2"));
		properties.add(new UserProperty("k3", "v3"));

		List<? extends MqttProperty> all = (List<? extends MqttProperty>) properties.listAll();
		assertEquals(1, all.size(),
			"多个 UserProperty 必须合并为单个 UserProperties（编码器要求）");
		assertTrue(all.get(0) instanceof UserProperties);
		UserProperties merged = (UserProperties) all.get(0);
		assertEquals(3, merged.value().size());
		assertEquals(Arrays.asList("k1", "k2", "v3"),
			Arrays.asList(merged.value().get(0).key, merged.value().get(1).key, merged.value().get(2).value));
	}

	// ----------------- getProperty / getProperties 类型兼容性 -----------------

	@Test
	void getPropertyByTypeOverloadDelegatesToIntVersion() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		assertEquals(60, properties.getProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL).value());
		assertEquals(60, properties.getProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value()).value());
	}

	@Test
	void getPropertyValueReturnsRawValue() {
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));
		Integer value = properties.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL);
		assertEquals(Integer.valueOf(60), value);
	}

	@Test
	void unknownPropertyIdReturnsNull() {
		MqttProperties properties = new MqttProperties();
		assertNull(properties.getProperty(UNKNOWN_PROPERTY_ID));
		assertTrue(properties.getProperties(UNKNOWN_PROPERTY_ID).isEmpty());
	}
}