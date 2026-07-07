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

package org.dromara.mica.mqtt.core.server.handler;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.codes.MqttDisconnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttReasonCodeAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * DISCONNECT 消息处理器。
 *
 * @author L.cm
 */
public class MqttDisConnectHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttDisConnectHandler.class);

	public MqttDisConnectHandler(MqttServerCreator serverCreator,
							 ExecutorService executor,
							 TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.DISCONNECT};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage message) {
		String clientId = context.getBsId();
		byte reasonCode = getReasonCode(message);
		if (reasonCode == MqttDisconnectReasonCode.NORMAL.value()) {
			logger.info("DisConnect - clientId:{} contextId:{} reasonCode:0x{}", clientId, context.getId(), Integer.toHexString(reasonCode & 0xFF));
		} else {
			logger.warn("DisConnect - clientId:{} contextId:{} reasonCode:0x{}", clientId, context.getId(), Integer.toHexString(reasonCode & 0xFF));
		}
		context.setBizStatus(true);
		Tio.remove(context, "Mqtt DisConnect");
	}

	private byte getReasonCode(MqttMessage message) {
		Object variableHeader = message.variableHeader();
		if (variableHeader instanceof MqttReasonCodeAndPropertiesVariableHeader) {
			return ((MqttReasonCodeAndPropertiesVariableHeader) variableHeader).reasonCode();
		}
		// MQTT 3.x DISCONNECT 只有固定头；统一按正常断开处理。
		return MqttDisconnectReasonCode.NORMAL.value();
	}
}
