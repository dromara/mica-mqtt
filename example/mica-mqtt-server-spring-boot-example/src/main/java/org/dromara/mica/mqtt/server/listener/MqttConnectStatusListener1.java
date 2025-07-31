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

package org.dromara.mica.mqtt.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.spring.server.event.MqttClientOfflineEvent;
import org.dromara.mica.mqtt.spring.server.event.MqttClientOnlineEvent;
import org.springframework.context.event.EventListener;

/**
 * mqtt 连接状态，使用 spring boot event 方式，性能有损耗
 *
 * @author L.cm
 */
@Slf4j
//@Service
public class MqttConnectStatusListener1 {

	@EventListener
	public void online(MqttClientOnlineEvent event) {
		log.info("MqttClientOnlineEvent:{}", event);
	}

	@EventListener
	public void offline(MqttClientOfflineEvent event) {
		log.info("MqttClientOfflineEvent:{}", event);
	}

}
