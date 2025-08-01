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

package org.dromara.mica.mqtt.ssl;

import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.server.MqttConnectStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.utils.buffer.ByteBufferUtil;

import java.nio.charset.StandardCharsets;

/**
 * mqtt 服务端测试
 *
 * @author L.cm
 */
public class SslMqttServerTest {
	private static final Logger logger = LoggerFactory.getLogger(SslMqttServerTest.class);

	public static void main(String[] args) {
		MqttServer mqttServer = MqttServer.create()
			.enableMqttSsl(builder ->
				builder.useSsl("classpath:ssl/dreamlu.net.jks", "123456")
					.build()
			)
			.messageListener((context, clientId, topic, qoS, message) -> {
				logger.info("clientId:{} message:{} payload:{}", clientId, message, ByteBufferUtil.toString(message.payload()));
			})
			.connectStatusListener(new MqttConnectStatusListener())
			.debug()
			.start();

		// 定时发送数据
		mqttServer.schedule(() -> {
			String message = "mica最牛皮 " + System.currentTimeMillis();
			mqttServer.publishAll("/test/123", message.getBytes(StandardCharsets.UTF_8));
		}, 5000);
	}
}
