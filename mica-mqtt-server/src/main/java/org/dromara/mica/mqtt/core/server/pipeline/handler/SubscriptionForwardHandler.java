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

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.core.TioConfig;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 订阅转发处理器 - 转发给订阅客户端
 *
 * @author L.cm
 */
public class SubscriptionForwardHandler implements MqttPublishPipelineHandler {
	private static final Logger logger = LoggerFactory.getLogger(SubscriptionForwardHandler.class);
	private final IMqttSessionManager sessionManager;
	private final ExecutorService executor;

	public SubscriptionForwardHandler(MqttServerCreator serverCreator, ExecutorService executor) {
		this.sessionManager = serverCreator.getSessionManager();
		this.executor = executor;
	}

	@Override
	public boolean handle(PublishContext context) {
		// 异步转发
		executor.submit(() -> {
			// 搜索所有订阅者
			List<Subscribe> subscribeList = sessionManager.searchSubscribe(context.getTopic());
			if (subscribeList == null || subscribeList.isEmpty()) {
				return;
			}
			TioConfig tioConfig = context.getContext().getTioConfig();
			try {
				for (Subscribe subscribe : subscribeList) {
					// 获取客户端上下文
					ChannelContext clientContext = Tio.getByBsId(tioConfig, subscribe.getClientId());
					if (clientContext == null || clientContext.isClosed()) {
						logger.warn("Mqtt Topic:{} publish to clientId:{} ChannelContext is null may be disconnected.", context.getTopic(), subscribe.getClientId());
						continue;
					}
					// 确定 QoS（取最小的）
					int qosValue = Math.min(context.getQos().value(), subscribe.getMqttQoS());
					MqttQoS mqttQoS = MqttQoS.valueOf(qosValue);
					// 创建发布消息
					MqttPublishMessage publishMessage = MqttPublishMessage.builder()
						.topicName(context.getTopic())
						.payload(context.getPayload())
						.qos(mqttQoS)
						.retained(false)
						.messageId(context.getMessageId())
						.properties(context.getProperties())
						.build();
					// 发送消息
					Tio.send(clientContext, publishMessage);
				}
			} catch (Throwable e) {
				logger.error("Subscription forward error", e);
			}
		});
		return true;
	}

	@Override
	public int getOrder() {
		return 300; // 订阅转发处理
	}
}
