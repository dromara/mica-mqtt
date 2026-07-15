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

package org.dromara.mica.mqtt.core.server.handler;

import org.dromara.mica.mqtt.codec.codes.MqttSubAckReasonCode;
import org.dromara.mica.mqtt.core.server.MqttServerProperties;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption.RetainedHandlingPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MqttSubscribeHandlerTest {

	@Test
	void testRetainHandlingPolicy() {
		Assertions.assertTrue(MqttSubscribeHandler.shouldSendRetainedMessage(
			RetainedHandlingPolicy.SEND_AT_SUBSCRIBE, true));
		Assertions.assertTrue(MqttSubscribeHandler.shouldSendRetainedMessage(
			RetainedHandlingPolicy.SEND_AT_SUBSCRIBE, false));
		Assertions.assertTrue(MqttSubscribeHandler.shouldSendRetainedMessage(
			RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS, true));
		Assertions.assertFalse(MqttSubscribeHandler.shouldSendRetainedMessage(
			RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS, false));
		Assertions.assertFalse(MqttSubscribeHandler.shouldSendRetainedMessage(
			RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE, true));
		Assertions.assertFalse(MqttSubscribeHandler.shouldSendRetainedMessage(
			RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE, false));
	}

	@Test
	void testDefaultSubscriptionIdentifierCapabilityEnabled() {
		Assertions.assertTrue(new MqttServerProperties().isSubscriptionIdentifierAvailable());
	}

	@Test
	void testResolveCapabilityReasonCodeForSubscriptionIdentifier() {
		MqttServerProperties serverProperties = new MqttServerProperties()
			.subscriptionIdentifierAvailable(false);
		Assertions.assertEquals(MqttSubAckReasonCode.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED,
			MqttSubscribeHandler.resolveCapabilityReasonCode(serverProperties, "test/topic", 7, true));
	}

	@Test
	void testResolveCapabilityReasonCodeForSharedSubscription() {
		MqttServerProperties serverProperties = new MqttServerProperties()
			.sharedSubscriptionAvailable(false);
		Assertions.assertEquals(MqttSubAckReasonCode.SHARED_SUBSCRIPTIONS_NOT_SUPPORTED,
			MqttSubscribeHandler.resolveCapabilityReasonCode(serverProperties, "$share/group/test/topic", 0, true));
		Assertions.assertEquals(MqttSubAckReasonCode.SHARED_SUBSCRIPTIONS_NOT_SUPPORTED,
			MqttSubscribeHandler.resolveCapabilityReasonCode(serverProperties, "$queue/test/topic", 0, true));
	}

	@Test
	void testResolveCapabilityReasonCodeForWildcardSubscription() {
		MqttServerProperties serverProperties = new MqttServerProperties()
			.wildcardSubscriptionAvailable(false);
		Assertions.assertEquals(MqttSubAckReasonCode.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED,
			MqttSubscribeHandler.resolveCapabilityReasonCode(serverProperties, "test/+/topic", 0, true));
		Assertions.assertEquals(MqttSubAckReasonCode.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED,
			MqttSubscribeHandler.resolveCapabilityReasonCode(serverProperties, "$share/group/test/#", 0, true));
	}
	@Test
	void testResolveCapabilityReasonCodeForMalformedSharedSubscription() {
		MqttServerProperties serverProperties = new MqttServerProperties();
		Assertions.assertEquals(MqttSubAckReasonCode.TOPIC_FILTER_INVALID,
			MqttSubscribeHandler.resolveCapabilityReasonCode(serverProperties, "$share/group", 0, true));
	}

	@Test
	void testResolveCapabilityReasonCodeForNonMqtt5Client() {
		MqttServerProperties serverProperties = new MqttServerProperties()
			.sharedSubscriptionAvailable(false)
			.wildcardSubscriptionAvailable(false)
			.subscriptionIdentifierAvailable(false);
		Assertions.assertNull(MqttSubscribeHandler.resolveCapabilityReasonCode(serverProperties, "$share/group/test/#", 9, false));
	}
}
