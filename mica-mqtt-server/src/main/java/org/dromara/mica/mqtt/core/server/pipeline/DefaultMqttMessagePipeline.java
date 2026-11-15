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

import org.dromara.mica.mqtt.core.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 默认 MQTT 消息处理管线实现
 *
 * @author L.cm
 */
public class DefaultMqttMessagePipeline implements IMqttMessagePipeline {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttMessagePipeline.class);
	/**
	 * 处理器列表
	 */
	private final List<MqttMessageHandler> handlers = new ArrayList<>();

	public DefaultMqttMessagePipeline() {
	}

	@Override
	public void addHandler(MqttMessageHandler handler) {
		if (handler != null) {
			this.handlers.add(handler);
			// 按顺序排序
			this.handlers.sort(Comparator.comparingInt(MqttMessageHandler::getOrder));
		}
	}

	@Override
	public boolean handle(Message message) {
		if (message == null) {
			return false;
		}
		// 处理所有消息类型的处理器
		return processHandlers(handlers, message);
	}

	/**
	 * 处理处理器列表
	 * <p>
	 * 返回值语义：只要存在投递类处理器（{@link MqttMessageDeliveryHandler}），
	 * 就只以投递结果为准——后置 handler 的异常不会让调用方（如遗嘱重试）重复投递；
	 * 若投递前被 handler 主动中断，则返回 false，调用方不会误以为已经投递。
	 * </p>
	 *
	 * @param handlers 处理器列表
	 * @param message 消息
	 */
	private boolean processHandlers(List<MqttMessageHandler> handlers, Message message) {
		boolean success = true;
		boolean deliveryAttempted = false;
		boolean deliverySucceeded = true;
		boolean interruptedBeforeDelivery = false;
		for (MqttMessageHandler handler : handlers) {
			try {
				boolean continueProcess = handler.handle(message);
				if (handler instanceof MqttMessageDeliveryHandler) {
					deliveryAttempted = true;
				}
				if (!continueProcess) {
					logger.debug("Pipeline handler {} interrupted the process for message type: {}",
								 handler.getClass().getSimpleName(), message.getMessageType());
					if (handler instanceof MqttMessageDeliveryHandler) {
						// 投递 handler 返回 false 表示实际发送失败（无订阅者等正常空操作应返回 true）
						deliverySucceeded = false;
					} else {
						interruptedBeforeDelivery = true;
					}
					break;
				}
			} catch (Throwable e) {
				success = false;
				if (handler instanceof MqttMessageDeliveryHandler) {
					deliverySucceeded = false;
				}
				logger.error("Pipeline handler {} error for message type: {}",
							 handler.getClass().getSimpleName(), message.getMessageType(), e);
				// 继续处理下一个处理器，不中断流程
			}
		}
		if (deliveryAttempted) {
			return deliverySucceeded;
		}
		return success && !interruptedBeforeDelivery;
	}

}
