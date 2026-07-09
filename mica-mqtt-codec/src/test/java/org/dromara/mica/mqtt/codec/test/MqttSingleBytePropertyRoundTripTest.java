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

import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MQTT 5.0 single-byte 属性往返测试。
 *
 * <p>覆盖 6 个 single-byte 布尔属性：
 * <ul>
 *     <li>CONNECT：REQUEST_PROBLEM_INFORMATION、REQUEST_RESPONSE_INFORMATION</li>
 *     <li>CONNACK：RETAIN_AVAILABLE、WILDCARD_SUBSCRIPTION_AVAILABLE、
 *         SUBSCRIPTION_IDENTIFIER_AVAILABLE、SHARED_SUBSCRIPTION_AVAILABLE</li>
 * </ul>
 *
 * <p>测试链路：{@code set boolean -> encodeProperties -> decodeProperties -> get Boolean}。
 * 防御 BooleanProperty 派生 / 存储类型与 getter 期望不一致可能再次引发的
 * {@link ClassCastException}，作为 6 个属性的防回归网。
 *
 * @author L.cm
 */
class MqttSingleBytePropertyRoundTripTest {

	@Test
	void requestProblemInformationRoundTrip() {
		assertConnect(MqttPropertyType.REQUEST_PROBLEM_INFORMATION,
			connectProperties -> connectProperties.setRequestProblemInformation(true),
			connectProperties -> connectProperties.setRequestProblemInformation(false),
			MqttConnectProperties::getRequestProblemInformation);
	}

	@Test
	void requestResponseInformationRoundTrip() {
		assertConnect(MqttPropertyType.REQUEST_RESPONSE_INFORMATION,
			connectProperties -> connectProperties.setRequestResponseInformation(true),
			connectProperties -> connectProperties.setRequestResponseInformation(false),
			MqttConnectProperties::getRequestResponseInformation);
	}

	@Test
	void retainAvailableRoundTrip() {
		assertConnAck(MqttPropertyType.RETAIN_AVAILABLE,
			connAckProperties -> connAckProperties.setRetainAvailable(true),
			connAckProperties -> connAckProperties.setRetainAvailable(false),
			MqttConnAckProperties::getRetainAvailable);
	}

	@Test
	void wildcardSubscriptionAvailableRoundTrip() {
		assertConnAck(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE,
			connAckProperties -> connAckProperties.setWildcardSubscriptionAvailable(true),
			connAckProperties -> connAckProperties.setWildcardSubscriptionAvailable(false),
			MqttConnAckProperties::getWildcardSubscriptionAvailable);
	}

	@Test
	void subscriptionIdentifiersAvailableRoundTrip() {
		assertConnAck(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE,
			connAckProperties -> connAckProperties.setSubscriptionIdentifiersAvailable(true),
			connAckProperties -> connAckProperties.setSubscriptionIdentifiersAvailable(false),
			MqttConnAckProperties::getSubscriptionIdentifiersAvailable);
	}

	@Test
	void sharedSubscriptionAvailableRoundTrip() {
		assertConnAck(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE,
			connAckProperties -> connAckProperties.setSharedSubscriptionAvailable(true),
			connAckProperties -> connAckProperties.setSharedSubscriptionAvailable(false),
			MqttConnAckProperties::getSharedSubscriptionAvailable);
	}

	@Test
	void unsetBooleanGettersReturnNull() {
		assertNull(new MqttConnectProperties().getRequestProblemInformation());
		assertNull(new MqttConnectProperties().getRequestResponseInformation());
		assertNull(new MqttConnAckProperties().getRetainAvailable());
		assertNull(new MqttConnAckProperties().getWildcardSubscriptionAvailable());
		assertNull(new MqttConnAckProperties().getSubscriptionIdentifiersAvailable());
		assertNull(new MqttConnAckProperties().getSharedSubscriptionAvailable());
	}

	@Test
	void reAddSameConnectPropertyShouldOverride() {
		MqttConnectProperties properties = new MqttConnectProperties();
		properties.setRequestProblemInformation(true);
		properties.setRequestProblemInformation(false);
		properties.setRequestResponseInformation(false);
		properties.setRequestResponseInformation(true);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(Boolean.FALSE, decoded.getBooleanPropertyValue(MqttPropertyType.REQUEST_PROBLEM_INFORMATION));
		assertEquals(Boolean.TRUE, decoded.getBooleanPropertyValue(MqttPropertyType.REQUEST_RESPONSE_INFORMATION));
	}

	@Test
	void reAddSameConnAckPropertyShouldOverride() {
		MqttConnAckProperties properties = new MqttConnAckProperties();
		properties.setRetainAvailable(false);
		properties.setRetainAvailable(true);
		properties.setWildcardSubscriptionAvailable(true);
		properties.setWildcardSubscriptionAvailable(false);
		properties.setSubscriptionIdentifiersAvailable(false);
		properties.setSubscriptionIdentifiersAvailable(true);
		properties.setSharedSubscriptionAvailable(true);
		properties.setSharedSubscriptionAvailable(false);

		MqttProperties decoded = roundTrip(properties.getProperties());
		assertEquals(Boolean.TRUE, decoded.getBooleanPropertyValue(MqttPropertyType.RETAIN_AVAILABLE));
		assertEquals(Boolean.FALSE, decoded.getBooleanPropertyValue(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE));
		assertEquals(Boolean.TRUE, decoded.getBooleanPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE));
		assertEquals(Boolean.FALSE, decoded.getBooleanPropertyValue(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE));
	}

	@Test
	void encodeDecodeEmptyDoesNotThrow() {
		byte[] bytes = MqttEncoder.encodeProperties(new MqttConnectProperties().getProperties());
		assertNotNull(bytes);
		MqttProperties decoded = MqttDecoder.decodeProperties(bytes);
		assertNotNull(decoded);
	}

	private static MqttProperties roundTrip(MqttProperties source) {
		return MqttDecoder.decodeProperties(MqttEncoder.encodeProperties(source));
	}

	/**
	 * 通用 single-byte 布尔属性的往返断言：
	 * true 写一次 → 编/解后应得 Boolean.TRUE，false 同理，未设置时 getter 返回 null。
	 *
	 * @param propertyType   MQTT 5.0 属性 id
	 * @param setTrue        设置 true 时调用的 setter lambda
	 * @param setFalse       设置 false 时调用的 setter lambda
	 * @param getter         用于验证未设置时返回 null 的 getter
	 */
	private static void assertConnect(MqttPropertyType propertyType,
										   Consumer<MqttConnectProperties> setTrue,
										   Consumer<MqttConnectProperties> setFalse,
										   Function<MqttConnectProperties, Boolean> getter) {
		MqttConnectProperties trueHolder = new MqttConnectProperties();
		setTrue.accept(trueHolder);
		MqttProperties decodedTrue = roundTrip(trueHolder.getProperties());
		assertEquals(Boolean.TRUE, decodedTrue.getBooleanPropertyValue(propertyType),
			"set true round-trip for " + propertyType);

		MqttConnectProperties falseHolder = new MqttConnectProperties();
		setFalse.accept(falseHolder);
		MqttProperties decodedFalse = roundTrip(falseHolder.getProperties());
		assertEquals(Boolean.FALSE, decodedFalse.getBooleanPropertyValue(propertyType),
			"set false round-trip for " + propertyType);

		assertNull(getter.apply(new MqttConnectProperties()),
			"unset getter for " + propertyType);
	}

	/**
	 * 同上，但是针对 CONNACK 属性的 MqttConnAckProperties。
	 */
	private static void assertConnAck(MqttPropertyType propertyType,
									   Consumer<MqttConnAckProperties> setTrue,
									   Consumer<MqttConnAckProperties> setFalse,
									   Function<MqttConnAckProperties, Boolean> getter) {
		MqttConnAckProperties trueHolder = new MqttConnAckProperties();
		setTrue.accept(trueHolder);
		MqttProperties decodedTrue = roundTrip(trueHolder.getProperties());
		assertEquals(Boolean.TRUE, decodedTrue.getBooleanPropertyValue(propertyType),
			"set true round-trip for " + propertyType);

		MqttConnAckProperties falseHolder = new MqttConnAckProperties();
		setFalse.accept(falseHolder);
		MqttProperties decodedFalse = roundTrip(falseHolder.getProperties());
		assertEquals(Boolean.FALSE, decodedFalse.getBooleanPropertyValue(propertyType),
			"set false round-trip for " + propertyType);

		assertNull(getter.apply(new MqttConnAckProperties()),
			"unset getter for " + propertyType);
	}
}
