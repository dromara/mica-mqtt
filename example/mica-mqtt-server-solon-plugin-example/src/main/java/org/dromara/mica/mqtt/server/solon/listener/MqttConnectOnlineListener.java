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

package org.dromara.mica.mqtt.server.solon.listener;

import org.dromara.mica.mqtt.server.solon.event.MqttClientOnlineEvent;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 连接状态，使用 solon event 方式，性能有损耗
 *
 * @author L.cm
 */
@Component
public class MqttConnectOnlineListener implements EventListener<MqttClientOnlineEvent> {
	private static final Logger logger = LoggerFactory.getLogger(MqttConnectOnlineListener.class);

	@Override
	public void onEvent(MqttClientOnlineEvent mqttClientOnlineEvent) throws Throwable {
		logger.info("MqttClientOnlineEvent:{}", mqttClientOnlineEvent);
	}
}
