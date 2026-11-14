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

package org.dromara.mica.mqtt.core.server.model;

import net.dreamlu.mica.net.client.ClientChannelContext;
import net.dreamlu.mica.net.client.TioClientConfig;
import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientInfoTest {
	@Test
	void reportsConfiguredKeepAliveWithoutBackoff() {
		ChannelContext context = new ClientChannelContext(new TioClientConfig(null, null));
		context.setHeartbeatTimeout(60_000L);
		assertEquals(60, ClientInfo.getClientKeepAlive(context, MqttServer.create()));
		context.setHeartbeatTimeout(Long.MAX_VALUE / 2);
		assertEquals(0, ClientInfo.getClientKeepAlive(context, MqttServer.create()));
	}
}
