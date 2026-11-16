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

package org.dromara.mica.mqtt.core.server;

import net.dreamlu.mica.net.core.ChannelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttServerAioListenerWillTest {

	@Test
	void abnormalClosePublishesWill() {
		assertTrue(MqttServerAioListener.shouldPublishWillOnClose(false, null));
	}

	@Test
	void normalDisconnectDoesNotPublishWill() {
		assertFalse(MqttServerAioListener.shouldPublishWillOnClose(true, null));
	}

	/**
	 * 同 clientId 新连接接管旧连接时没有 DISCONNECT，按 MQTT-3.1.4-3 发布旧遗嘱。
	 */
	@Test
	void takeoverPublishesOldWill() {
		assertTrue(MqttServerAioListener.shouldPublishWillOnClose(
			true, ChannelContext.CloseCode.KICK_EACH_OTHER));
	}
}
