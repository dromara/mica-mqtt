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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认 MQTT 消息处理管线实现
 *
 * @author L.cm
 */
public class DefaultMqttMessagePipeline implements IMqttMessagePipeline {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttMessagePipeline.class);

	/**
	 * 所有消息类型的处理器列表
	 */
	private final List<MqttMessageHandler> allHandlers = new ArrayList<>();
	
	/**
	 * 按消息类型分组的处理器映射
	 */
	private final Map<MessageType, List<MqttMessageHandler>> typeHandlersMap = new ConcurrentHashMap<>();

	public DefaultMqttMessagePipeline() {
	}

	/**
	 * 添加处理器到所有消息类型
	 *
	 * @param handler 处理器
	 * @return this
	 */
	public DefaultMqttMessagePipeline addHandler(MqttMessageHandler handler) {
		if (handler != null) {
			this.allHandlers.add(handler);
			// 按顺序排序
			this.allHandlers.sort(Comparator.comparingInt(MqttMessageHandler::getOrder));
		}
		return this;
	}

	/**
	 * 添加处理器到特定消息类型
	 *
	 * @param messageType 消息类型
	 * @param handler 处理器
	 * @return this
	 */
	public DefaultMqttMessagePipeline addHandler(MessageType messageType, MqttMessageHandler handler) {
		if (messageType != null && handler != null) {
			this.typeHandlersMap.computeIfAbsent(messageType, k -> new ArrayList<>())
				.add(handler);
			// 按顺序排序
			this.typeHandlersMap.get(messageType)
				.sort(Comparator.comparingInt(MqttMessageHandler::getOrder));
		}
		return this;
	}

	@Override
	public boolean handle(Message message) {
		if (message == null) {
			return false;
		}
		MessageType messageType = message.getMessageType();
		
		// 先处理所有消息类型的处理器
		processHandlers(allHandlers, message);
		
		// 再处理特定消息类型的处理器
		List<MqttMessageHandler> typeHandlers = typeHandlersMap.get(messageType);
		if (typeHandlers != null) {
			processHandlers(typeHandlers, message);
		}
		
		return true;
	}

	/**
	 * 处理处理器列表
	 *
	 * @param handlers 处理器列表
	 * @param message 消息
	 */
	private void processHandlers(List<MqttMessageHandler> handlers, Message message) {
		for (MqttMessageHandler handler : handlers) {
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
