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
 * 订阅转发处理器 - 将发布消息转发给所有匹配的订阅客户端
 * <p>
 * 处理流程：
 * <ol>
 *   <li>查找所有匹配 topic 的订阅</li>
 *   <li>MQTT 5.0 属性处理（过期检查、属性重写）</li>
 *   <li>逐一转发给订阅者（遵循 No Local、QoS 降级等规范）</li>
 * </ol>
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
		// MQTT 5.0 才有 properties，MQTT 3.x 消息直接跳过属性处理
		MqttProperties properties = context.getProperties();
		if (properties == null || properties.isEmpty()) {
			forwardToSubscribers(context, subscribeList, properties);
		} else {
			properties = rewriteProperties(context, properties);
			if (properties != null) {
				forwardToSubscribers(context, subscribeList, properties);
			}
		}
		return true;
	}

	/**
	 * MQTT 5.0 属性重写（规范 3.3.2.3）：
	 * <ul>
	 *   <li>检查 Message Expiry Interval，若已过期则返回 null 表示丢弃消息</li>
	 *   <li>移除 Topic Alias（服务端转发时不携带发布者的 Topic Alias）</li>
	 *   <li>移除 Subscription Identifier（发布者的订阅标识符不转发给订阅者）</li>
	 *   <li>递减 Message Expiry Interval 为剩余有效时间</li>
	 * </ul>
	 *
	 * @param context    发布上下文
	 * @param properties 原始 MQTT 5.0 属性（非 null 且非空）
	 * @return 重写后的属性；null 表示消息已过期，应丢弃
	 */
	private MqttProperties rewriteProperties(PublishContext context, MqttProperties properties) {
		Long receivedAt = context.getPublishReceivedAt();
		if (receivedAt == null) {
			receivedAt = context.getTimestamp();
		}
		long elapsedSeconds = (System.currentTimeMillis() - receivedAt) / 1000;
		// 检查消息过期
		Integer expiryInterval = properties.getPropertyValue(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL);
		long remaining = -1;
		if (expiryInterval != null) {
			remaining = expiryInterval - elapsedSeconds;
			if (remaining <= 0) {
				logger.debug("Mqtt Topic:{} message expired, skip forwarding", context.getTopic());
				return null;
			}
		}
		// O(1) 属性查找判断是否需要重写，避免不必要的遍历和对象分配
		boolean needsRewrite = properties.getProperty(MqttPropertyType.TOPIC_ALIAS) != null
			|| properties.getProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER) != null
			|| (expiryInterval != null && elapsedSeconds > 0);
		if (!needsRewrite) {
			return properties;
		}
		MqttProperties newProperties = new MqttProperties();
		for (MqttProperty property : properties.listAll()) {
			int propertyId = property.propertyId();
			// MQTT 5.0 规范: 服务端转发时不携带发布者的 Topic Alias 和 Subscription Identifier
			if (propertyId == MqttPropertyType.TOPIC_ALIAS.value()
				|| propertyId == MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) {
				continue;
			}
			if (propertyId == MqttPropertyType.MESSAGE_EXPIRY_INTERVAL.value()
				&& expiryInterval != null && elapsedSeconds > 0) {
				// remaining 已在上方计算且保证 > 0
				newProperties.add(new IntegerProperty(propertyId, (int) remaining));
			} else {
				newProperties.add(property);
			}
		}
		return newProperties;
	}

	/**
	 * 将消息转发给所有匹配的订阅者
	 *
	 * @param context       发布上下文
	 * @param subscribeList 匹配的订阅列表
	 * @param properties    重写后的 MQTT 5.0 属性
	 */
	private void forwardToSubscribers(PublishContext context, List<Subscribe> subscribeList, MqttProperties properties) {
		TioConfig tioConfig = context.getContext().getTioConfig();
		String publisherClientId = context.getClientId();
		try {
			for (Subscribe subscribe : subscribeList) {
				// MQTT 5.0 No Local（规范 3.8.3.1）: 订阅者即发布者时跳过
				if (subscribe.isNoLocal() && subscribe.getClientId().equals(publisherClientId)) {
					logger.debug("Mqtt Topic:{} skip forwarding to clientId:{} due to No Local flag", context.getTopic(), subscribe.getClientId());
					continue;
				}
				ChannelContext clientContext = Tio.getByBsId(tioConfig, subscribe.getClientId());
				if (clientContext == null || clientContext.isClosed()) {
					logger.warn("Mqtt Topic:{} publish to clientId:{} ChannelContext is null may be disconnected.", context.getTopic(), subscribe.getClientId());
					continue;
				}
				// MQTT 规范: 实际 QoS 取发布 QoS 与订阅 QoS 的较小值
				int qosValue = Math.min(context.getQos().value(), subscribe.getMqttQoS());
				MqttQoS mqttQoS = MqttQoS.valueOf(qosValue);
				MqttPublishMessage publishMessage = MqttPublishMessage.builder()
					.topicName(context.getTopic())
					.payload(context.getPayload())
					.qos(mqttQoS)
					.retained(false)
					.messageId(context.getMessageId())
					.properties(properties)
					.build();
				Tio.send(clientContext, publishMessage);
			}
		} catch (Throwable e) {
			logger.error("Subscription forward error", e);
		}
	}

	@Override
	public int getOrder() {
		return 300;
	}

	@Override
	public boolean isCritical() {
		return true;
	}
}
