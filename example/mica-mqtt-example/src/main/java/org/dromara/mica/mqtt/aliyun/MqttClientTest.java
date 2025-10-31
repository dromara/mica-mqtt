/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
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

package org.dromara.mica.mqtt.aliyun;

import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端测试
 *
 * @author L.cm
 */
public class MqttClientTest {

	public static void main(String[] args) {
		String productKey = "g27jB42P9hm";
		String deviceName = "3dbc1cb4";
		String deviceSecret = "";
		// 计算MQTT连接参数。
		MqttSign sign = new MqttSign(productKey, deviceName, deviceSecret);

		String username = sign.getUsername();
		String password = sign.getPassword();
		String clientId = sign.getClientId();
		System.out.println("username: " + username);
		System.out.println("password: " + password);
		System.out.println("clientid: " + clientId);

		// 初始化 mqtt 客户端
		MqttClient client = MqttClient.create()
			.ip(productKey + ".iot-as-mqtt.cn-shanghai.aliyuncs.com")
			.port(443)
			.username(username)
			.password(password)
			.clientId(clientId)
			.connectSync();

		client.subQos0("/sys/" + productKey + '/' + deviceName + "/thing/event/property/post_reply", (context, topic, message, payload) -> {
			System.out.println(topic + '\t' + new String(payload, StandardCharsets.UTF_8));
		});

		client.schedule(() -> {
			int LightSwitch = ThreadLocalRandom.current().nextBoolean() ? 0 : 1;
			String content = "{\"id\":\"1\",\"version\":\"1.0\",\"params\":{\"LightSwitch\":" + LightSwitch + "}}";
			client.publish("/sys/" + productKey + "/" + deviceName + "/thing/event/property/post", content.getBytes(StandardCharsets.UTF_8));
		}, 3000);
	}

}
