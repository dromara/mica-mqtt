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

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttServerProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.utils.hutool.StrUtil;

/**
 * 订阅转发处理器 - 转发给订阅客户端
 *
 * @author L.cm
 */
public class SubscriptionForwardHandler implements MqttPublishPipelineHandler {
	private static final Logger logger = LoggerFactory.getLogger(SubscriptionForwardHandler.class);
	private final IMqttSessionManager sessionManager;
	private final ExecutorService executor;
	private final MqttServerCreator serverCreator;

	public SubscriptionForwardHandler(MqttServerCreator serverCreator, ExecutorService executor) {
		this.serverCreator = serverCreator;
		this.sessionManager = serverCreator.getSessionManager();
		this.executor = executor;
	}

	@Override
	public boolean handle(PublishContext context) {
		// 构建 Message 用于转发
		Message message = new Message();
		message.setId(context.getMessageId());
		message.setFromClientId(context.getClientId());
		message.setFromUsername(context.getUsername());
		message.setTopic(context.getTopic());
		message.setQos(context.getQos().value());
		if (context.getPayload() != null) {
			message.setPayload(context.getPayload());
		}
		message.setMessageType(MessageType.UP_STREAM);
		// 已订阅状态下的监听，此时消息被视为"实时发布"而非"保留触发"，标志位不会被激活
		message.setRetain(false);
		message.setDup(context.isDup());
		message.setTimestamp(context.getTimestamp());
		message.setPeerHost(context.getPeerHost());
		message.setNode(context.getNodeName());
		if (context.getPublishReceivedAt() != null) {
			message.setPublishReceivedAt(context.getPublishReceivedAt());
		}
		// 设置 MQTT5 properties
		if (context.getProperties() != null && !context.getProperties().isEmpty()) {
			message.setProperties(context.getProperties());
		}
		// 异步转发
		executor.submit(() -> {
			try {
				// 搜索所有订阅者
				List<Subscribe> subscribeList = sessionManager.searchSubscribe(context.getTopic());
				if (subscribeList != null && !subscribeList.isEmpty()) {
					for (Subscribe subscribe : subscribeList) {
						// 获取客户端上下文
						ChannelContext clientContext = Tio.getByBsId(context.getContext().getTioConfig(), subscribe.getClientId());
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
