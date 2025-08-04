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

import org.dromara.mica.mqtt.core.client.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.utils.buffer.ByteBufferUtil;

import java.nio.charset.StandardCharsets;

/**
 * 客户端测试
 *
 * @author L.cm
 */
public class SslMqttClientTest {
	private static final Logger logger = LoggerFactory.getLogger(SslMqttClientTest.class);

	public static void main(String[] args) {
		// 初始化 mqtt 客户端
		MqttClient client = MqttClient.create()
			.ip("127.0.0.1")
			.port(8883)
			.username("mica")
			.password("mica")
			.useSsl("classpath:ssl/dreamlu.net.jks", "123456")
			.connectSync();

		client.subQos0("/test/#", (context, topic, message, payload) -> {
			logger.info(topic + '\t' + ByteBufferUtil.toString(payload));
		});

		// 定时发送数据
		client.schedule(() -> {
			String message = "mica最牛皮 " + System.currentTimeMillis();
			client.publish("/test/123", message.getBytes(StandardCharsets.UTF_8));
		}, 5000);
	}
}
