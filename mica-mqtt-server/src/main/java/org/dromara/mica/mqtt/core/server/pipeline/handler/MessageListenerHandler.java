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

package org.dromara.mica.mqtt.core.server.pipeline.handler;

import java.util.concurrent.ExecutorService;

import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息监听器处理器
 *
 * @author L.cm
 */
public class MessageListenerHandler implements MqttPublishPipelineHandler {
	private static final Logger logger = LoggerFactory.getLogger(MessageListenerHandler.class);
	private final IMqttMessageListener messageListener;
	private final ExecutorService executor;

	public MessageListenerHandler(IMqttMessageListener messageListener, ExecutorService executor) {
		this.messageListener = messageListener;
		this.executor = executor;
	}

	@Override
	public boolean handle(PublishContext context) {
		if (messageListener == null) {
			return true;
		}
		executor.submit(() -> {
			try {
				messageListener.onMessage(
					context.getContext(),
					context.getClientId(),
					context.getTopic(),
					context.getQos(),
					context.getPublishMessage()
				);
			} catch (Throwable e) {
				logger.error("Message listener error", e);
			}
		});
		return true;
	}

	@Override
	public int getOrder() {
		return 200; // 消息监听器处理
	}
}
