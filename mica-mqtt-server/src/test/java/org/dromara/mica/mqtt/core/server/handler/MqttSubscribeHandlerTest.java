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
}
