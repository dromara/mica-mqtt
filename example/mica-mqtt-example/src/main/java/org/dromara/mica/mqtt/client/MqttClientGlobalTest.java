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

package org.dromara.mica.mqtt.client;

import org.dromara.mica.mqtt.core.client.MqttClient;
import org.tio.utils.buffer.ByteBufferUtil;

/**
 * 客户端全局订阅测试
 *
 * @author L.cm
 */
public class MqttClientGlobalTest {

	public static void main(String[] args) {
		// 初始化 mqtt 客户端
		MqttClient.create()
			.ip("127.0.0.1")
			.port(1883)
			.username("admin")
			.password("123456")
			// 采用 globalSubscribe，保留 session 停机重启后，可以接受到离线消息，注意：clientId 要不能变化。
			.clientId("globalTest")
			.cleanStart(false)
			// 全局订阅的 topic
			.globalSubscribe("/test", "/test/123", "/debug/#")
			// 全局监听，也会监听到服务端 http api 订阅的数据
			.globalMessageListener((context, topic, message, payload) -> {
				System.out.println("topic:\t" + topic);
				System.out.println("payload:\t" + ByteBufferUtil.toString(payload));
            })
//			.debug()
			.connectSync();
	}

}
