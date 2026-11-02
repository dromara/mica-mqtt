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

package org.dromara.mica.mqtt.core.client;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.server.ServerChannelContext;
import net.dreamlu.mica.net.server.TioServerConfig;
import org.dromara.mica.mqtt.codec.codes.MqttDisconnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttDisconnectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端处理服务端 DISCONNECT 报文的测试。
 *
 * @author L.cm
 */
class DefaultMqttClientProcessorDisconnectTest {

	@Test
	void shouldRebuildClientIdWhenSessionTakenOverAndSwitchEnabled() {
		MqttClientCreator creator = MqttClient.create()
			.clientId("originalClientId")
			.rebuildClientIdOnSessionTakenOver(true);
		DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator);

		processDisconnect(processor, MqttDisconnectReasonCode.SESSION_TAKEN_OVER);

		assertNotEquals("originalClientId", creator.getClientId(), "clientId 应被重新生成");
		assertTrue(creator.getClientId().startsWith("originalClientId-"),
			"新 clientId 应保留原前缀，实际: " + creator.getClientId());
	}

	@Test
	void shouldKeepClientIdWhenSwitchDisabled() {
		MqttClientCreator creator = MqttClient.create()
			.clientId("originalClientId")
			.rebuildClientIdOnSessionTakenOver(false);
		DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator);

		processDisconnect(processor, MqttDisconnectReasonCode.SESSION_TAKEN_OVER);

		assertEquals("originalClientId", creator.getClientId(), "开关关闭时 clientId 不应变化");
	}

	@Test
	void shouldKeepClientIdWhenReasonCodeIsNotSessionTakenOver() {
		MqttClientCreator creator = MqttClient.create()
			.clientId("originalClientId")
			.rebuildClientIdOnSessionTakenOver(true);
		DefaultMqttClientProcessor processor = new DefaultMqttClientProcessor(creator);

		processDisconnect(processor, MqttDisconnectReasonCode.SERVER_SHUTTING_DOWN);

		assertEquals("originalClientId", creator.getClientId(), "非 0x8E 原因码不应重建 clientId");
	}

	private void processDisconnect(DefaultMqttClientProcessor processor, MqttDisconnectReasonCode reasonCode) {
		MqttMessage message = new MqttDisconnectBuilder().reasonCode(reasonCode).build();
		ChannelContext context = new ServerChannelContext(
			new TioServerConfig("disconnect-test", null, null), "disconnect-test");
		try {
			processor.processDisconnect(context, message);
		} catch (Throwable e) {
			// 虚拟连接没有真实 socket，关闭时可能抛异常，不影响 clientId 断言
		}
	}
}
