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

package org.dromara.mica.mqtt.core.server.pipeline;

import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 MQTT 消息处理管线实现
 *
 * @author L.cm
 */
public class DefaultMqttMessagePipeline implements IMqttMessagePipeline {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttMessagePipeline.class);
	/**
	 * 按消息类型注册的处理器链
	 */
	private final Map<MessageType, List<MqttMessagePipelineHandler>> handlers = new EnumMap<>(MessageType.class);

	public DefaultMqttMessagePipeline() {
	}

	@Override
	public void addHandler(MqttMessagePipelineHandler handler) {
		if (handler == null) {
			return;
		}
		MessageType[] messageTypes = handler.messageTypes();
		if (messageTypes == null || messageTypes.length == 0) {
			throw new IllegalArgumentException("handler " + handler.getClass().getName() + " must declare at least one MessageType");
		}
		for (MessageType messageType : messageTypes) {
			if (messageType == null) {
				throw new IllegalArgumentException("handler " + handler.getClass().getName() + " contains null MessageType");
			}
			List<MqttMessagePipelineHandler> typeHandlers = handlers.computeIfAbsent(messageType, key -> new ArrayList<>());
			if (!typeHandlers.contains(handler)) {
				typeHandlers.add(handler);
				typeHandlers.sort(Comparator.comparingInt(MqttMessagePipelineHandler::getOrder));
			}
		}
	}

	@Override
	public boolean handle(Message message) {
		if (message == null) {
			return false;
		}
		MessageType messageType = message.getMessageType();
		if (messageType == null) {
			logger.warn("Mqtt internal message type is null: {}", message);
			return false;
		}
		List<MqttMessagePipelineHandler> typeHandlers = handlers.get(messageType);
		if (typeHandlers == null || typeHandlers.isEmpty()) {
			logger.debug("Mqtt internal message has no pipeline handler for type: {}", messageType);
			return true;
		}
		processHandlers(typeHandlers, message);
		return true;
	}

	/**
	 * 处理处理器列表
	 *
	 * @param handlers 处理器列表
	 * @param message 消息
	 */
	private void processHandlers(List<MqttMessagePipelineHandler> handlers, Message message) {
		for (MqttMessagePipelineHandler handler : handlers) {
			try {
				boolean continueProcess = handler.handle(message);
				if (!continueProcess) {
					logger.debug("Pipeline handler {} interrupted the process for message type: {}",
								 handler.getClass().getSimpleName(), message.getMessageType());
					break;
				}
			} catch (Throwable e) {
				logger.error("Pipeline handler {} error for message type: {}",
							 handler.getClass().getSimpleName(), message.getMessageType(), e);
				// 继续处理下一个处理器，不中断流程
			}
		}
	}

}
