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

package org.dromara.mica.mqtt.core.server.support;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.server.ServerChannelContext;
import net.dreamlu.mica.net.server.TioServerConfig;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * uniqueId 服务测试：验证「是否在 uniqueId 后追加来源地址」开关的行为。
 *
 * @author L.cm
 */
class DefaultMqttServerUniqueIdServiceImplTest {

	@Test
	void shouldReturnClientIdWhenAppendSourceAddressDisabled() {
		IMqttServerUniqueIdService service = new DefaultMqttServerUniqueIdServiceImpl();
		ChannelContext context = newContext("192.168.1.10");
		assertEquals("myClientId", service.getUniqueId(context, "myClientId", null, null));
	}

	@Test
	void shouldAppendSourceAddressWhenEnabled() {
		IMqttServerUniqueIdService service = new DefaultMqttServerUniqueIdServiceImpl(true);
		ChannelContext context = newContext("192.168.1.10");
		assertEquals("myClientId@192.168.1.10", service.getUniqueId(context, "myClientId", null, null));
	}

	@Test
	void shouldReturnClientIdWhenClientIdIsBlank() {
		IMqttServerUniqueIdService service = new DefaultMqttServerUniqueIdServiceImpl(true);
		ChannelContext context = newContext("192.168.1.10");
		assertEquals("", service.getUniqueId(context, "", null, null));
	}

	private ChannelContext newContext(String ip) {
		ServerChannelContext context = new ServerChannelContext(
			new TioServerConfig("unique-id-test", null, null), "unique-id-test");
		context.setClientNode(new Node(ip, 12345));
		return context;
	}
}
