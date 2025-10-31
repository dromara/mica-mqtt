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

package org.dromara.mica.mqtt.huawei;

import org.dromara.mica.mqtt.core.client.MqttClient;

import java.nio.charset.StandardCharsets;

/**
 * 客户端测试
 *
 * @author L.cm
 */
public class MqttClientTest {

	public static void main(String[] args) {
		// 设备id和密钥，请从华为云iot获取
		String deviceId = "630eb6f8664c6f7938db6ef0_test";
		String deviceSecret = "";
		// 计算MQTT连接参数。
		MqttSign sign = new MqttSign(deviceId, deviceSecret);

		String username = sign.getUsername();
		String password = sign.getPassword();
		String clientId = sign.getClientId();
		System.out.println("username: " + username);
		System.out.println("password: " + password);
		System.out.println("clientid: " + clientId);

		// 初始化 mqtt 客户端
		MqttClient client = MqttClient.create()
			.ip("iot-mqtts.cn-north-4.myhuaweicloud.com")
			.port(8883)
			.username(username)
			.password(password)
			.clientId(clientId)
			.useSsl()
			.connectSync();

		// 订阅命令下发topic
		String cmdRequestTopic = "$oc/devices/" + deviceId + "/sys/commands/#";

		client.subQos0(cmdRequestTopic, (context, topic, message, payload) -> {
			System.out.println(topic + '\t' + new String(payload, StandardCharsets.UTF_8));
		});

		// 属性上报消息
		String reportTopic = "$oc/devices/" + deviceId + "/sys/properties/report";
		String jsonMsg = "{\"services\":[{\"service_id\":\"Temperature\", \"properties\":{\"value\":57}},{\"service_id\":\"Battery\",\"properties\":{\"level\":88}}]}";

		client.schedule(() -> {
			client.publish(reportTopic, jsonMsg.getBytes(StandardCharsets.UTF_8));
		}, 3000);
	}

}
