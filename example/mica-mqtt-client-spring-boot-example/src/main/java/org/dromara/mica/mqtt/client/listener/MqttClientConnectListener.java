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

package org.dromara.mica.mqtt.client.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.dromara.mica.mqtt.spring.client.event.MqttConnectedEvent;
import org.dromara.mica.mqtt.spring.client.event.MqttDisconnectEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 客户端连接状态监听
 *
 * @author L.cm
 */
@Slf4j
@Service
public class MqttClientConnectListener {

	@Autowired
	private MqttClientCreator mqttClientCreator;

	@EventListener
	public void onConnected(MqttConnectedEvent event) {
		log.info("MqttConnectedEvent:{}", event);
	}

	@EventListener
	public void onDisconnect(MqttDisconnectEvent event) {
		log.info("MqttDisconnectEvent:{}", event);
		// 在断线时更新 clientId、username、password，只能改这 3 个，不可调用其他方法。
//		mqttClientCreator.clientId("newClient" + System.currentTimeMillis())
//			.username("newUserName")
//			.password("newPassword");
	}

}
