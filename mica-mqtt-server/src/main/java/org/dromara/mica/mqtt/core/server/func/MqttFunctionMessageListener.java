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

package org.dromara.mica.mqtt.core.server.func;

import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;

import java.util.List;

/**
 * 使用函数监听器，方便代码编写
 *
 * @author L.cm
 */
public class MqttFunctionMessageListener implements IMqttMessageListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttFunctionMessageListener.class);
	private final MqttFunctionManager functionManager;

	public MqttFunctionMessageListener(MqttFunctionManager functionManager) {
		this.functionManager = functionManager;
	}

	@Override
	public void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qoS, MqttPublishMessage message) {
		List<IMqttFunctionMessageListener> listenerList = functionManager.get(topic);
		for (IMqttFunctionMessageListener listener : listenerList) {
			try {
				listener.onMessage(context, clientId, topic, qoS, message);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

}
