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
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperty;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
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

/**
 * 订阅转发处理器 - 转发给订阅客户端
 * <p>
 * 同步执行，避免二次 submit 到线程池，减少队列积压和内存占用
 *
 * @author L.cm
 */
public class SubscriptionForwardHandler implements MqttPublishPipelineHandler {
	private static final Logger logger = LoggerFactory.getLogger(SubscriptionForwardHandler.class);
	private final IMqttSessionManager sessionManager;

	public SubscriptionForwardHandler(MqttServerCreator serverCreator) {
		this.sessionManager = serverCreator.getSessionManager();
	}

	@Override
	public boolean handle(PublishContext context) {
		List<Subscribe> subscribeList = sessionManager.searchSubscribe(context.getTopic());
		if (subscribeList == null || subscribeList.isEmpty()) {
			return true;
		}

		MqttProperties properties = context.getProperties();
		if (properties != null && !properties.isEmpty()) {
			Long receivedAt = context.getPublishReceivedAt();
			if (receivedAt == null) {
				receivedAt = context.getTimestamp();
			}
			long elapsed = (System.currentTimeMillis() - receivedAt) / 1000;

			Integer expiryInterval = properties.getPropertyValue(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL);
			if (expiryInterval != null) {
				long remaining = expiryInterval - elapsed;
				if (remaining <= 0) {
					logger.debug("Mqtt Topic:{} message expired, skip forwarding", context.getTopic());
					return true;
				}
			}

			boolean hasModified = false;
			for (MqttProperty property : properties.listAll()) {
				int propertyId = property.propertyId();
				if (propertyId == MqttPropertyType.TOPIC_ALIAS.value() ||
					propertyId == MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) {
					hasModified = true;
				} else if (propertyId == MqttPropertyType.MESSAGE_EXPIRY_INTERVAL.value() && elapsed > 0) {
					hasModified = true;
				}
			}

			if (hasModified) {
				MqttProperties newProperties = new MqttProperties();
				for (MqttProperty property : properties.listAll()) {
					int propertyId = property.propertyId();
					// MQTT 5.0 规范: 服务端在转发时不能携带发布者的 Topic Alias 和 Subscription Identifier
					if (propertyId == MqttPropertyType.TOPIC_ALIAS.value() ||
						propertyId == MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) {
						continue;
					} else if (propertyId == MqttPropertyType.MESSAGE_EXPIRY_INTERVAL.value()) {
						if (elapsed > 0 && expiryInterval != null) {
							long remaining = expiryInterval - elapsed;
							newProperties.add(new IntegerProperty(propertyId, Math.max(0, (int) remaining)));
						} else {
							newProperties.add(property);
						}
					} else {
						newProperties.add(property);
					}
				}
				properties = newProperties;
			}
		}

		TioConfig tioConfig = context.getContext().getTioConfig();
		String publisherClientId = context.getClientId();
		try {
			for (Subscribe subscribe : subscribeList) {
				// MQTT 5.0 No Local: 如果订阅时设置了 No Local 标志，且订阅者就是消息发布者，则跳过
				if (subscribe.isNoLocal() && subscribe.getClientId().equals(publisherClientId)) {
					logger.debug("Mqtt Topic:{} skip forwarding to clientId:{} due to No Local flag", context.getTopic(), subscribe.getClientId());
					continue;
				}
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
					.properties(properties)
					.build();
				// 发送消息
				Tio.send(clientContext, publishMessage);
			}
		} catch (Throwable e) {
			logger.error("Subscription forward error", e);
		}
		return true;
	}

	@Override
	public int getOrder() {
		return 300; // 订阅转发处理
	}

	@Override
	public boolean isCritical() {
		return true; // 订阅转发是核心功能，失败应中断流程
	}
}
