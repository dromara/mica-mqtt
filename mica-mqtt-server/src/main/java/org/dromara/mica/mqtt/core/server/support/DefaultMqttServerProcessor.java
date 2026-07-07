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

package org.dromara.mica.mqtt.core.server.support;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.MqttServerProcessor;
import org.dromara.mica.mqtt.core.server.handler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * mqtt server 默认消息处理器（外观层）：按 {@link MqttMessageType}
 * 进行路由分发，真正的业务实现已下沉到 {@code handler} 目录下
 * 各个 {@link IMqttMessageHandler} 中。
 * <p>
 * 外观层只做"按类型找 handler、把 MqttMessage 转给 handler"，不做任何
 * 不安全强转。handler 内部按需做 {@code instanceof} 拆箱成具体的
 * {@code MqttConnectMessage} / {@code MqttPublishMessage} 等。
 *
 * @author L.cm
 */
public class DefaultMqttServerProcessor implements MqttServerProcessor {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttServerProcessor.class);
	private final Map<MqttMessageType, IMqttMessageHandler> handlers = new EnumMap<>(MqttMessageType.class);

	public DefaultMqttServerProcessor(MqttServerCreator serverCreator,
									  TimerTaskService taskService,
									  ExecutorService executor) {
		MqttPublishHandler publishHandler = new MqttPublishHandler(serverCreator, executor, taskService);
		register(new MqttConnectHandler(serverCreator, executor, taskService))
			.register(publishHandler)
			.register(new MqttPubAckHandler(serverCreator, executor, taskService))
			.register(new MqttPubRecHandler(serverCreator, executor, taskService))
			.register(new MqttPubRelHandler(serverCreator, executor, taskService, publishHandler))
			.register(new MqttPubCompHandler(serverCreator, executor, taskService))
			.register(new MqttSubscribeHandler(serverCreator, executor, taskService))
			.register(new MqttUnSubscribeHandler(serverCreator, executor, taskService))
			.register(new MqttPingReqHandler(serverCreator, executor, taskService))
			.register(new MqttDisConnectHandler(serverCreator, executor, taskService));
	}

	/**
	 * 注册一个 handler，按其声明的 messageTypes() 占用对应槽位。
	 *
	 * @param handler IMqttMessageHandler
	 * @return DefaultMqttServerProcessor
	 */
	public DefaultMqttServerProcessor register(IMqttMessageHandler handler) {
		MqttMessageType[] types = handler.messageTypes();
		if (types == null || types.length == 0) {
			throw new IllegalArgumentException("handler " + handler.getClass().getName() + " must declare at least one MqttMessageType");
		}
		for (MqttMessageType type : types) {
			IMqttMessageHandler old = handlers.put(type, handler);
			if (old != null && old != handler) {
				logger.warn("DefaultMqttServerProcessor replace handler for {}: {} -> {}",
					type, old.getClass().getName(), handler.getClass().getName());
			}
		}
		return this;
	}

	@Override
	public void processDispatch(MqttMessageType type, ChannelContext context, MqttMessage message) {
		IMqttMessageHandler handler = handlers.get(type);
		if (handler == null) {
			logger.warn("Mqtt server no handler registered for {} contextId: {}", type, context.getId());
			return;
		}
		handler.handle(context, message);
	}
}
