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

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.core.TioConfig;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 订阅转发处理器 - 将发布消息转发给所有匹配的订阅客户端
 * <p>
 * 处理流程：
 * <ol>
 *   <li>查找所有匹配 topic 的订阅</li>
 *   <li>MQTT 5.0 属性处理（过期检查、属性重写）</li>
 *   <li>逐一转发给订阅者（遵循 No Local、QoS 降级等规范）</li>
 *   <li>订阅者离线时按会话持久性入离线队列（spec 4.1 持久会话）</li>
 * </ol>
 * 同步执行，避免二次 submit 到线程池，减少队列积压和内存占用
 *
 * @author L.cm
 */
public class SubscriptionForwardHandler implements MqttPublishPipelineHandler {
	private static final Logger logger = LoggerFactory.getLogger(SubscriptionForwardHandler.class);
	private final IMqttSessionManager sessionManager;
	private final MqttServer mqttServer;

	public SubscriptionForwardHandler(MqttServerCreator serverCreator, MqttServer mqttServer) {
		this.sessionManager = serverCreator.getSessionManager();
		this.mqttServer = mqttServer;
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
	 * MQTT 5.0 属性重写（规范 3.3.2.3）：由 {@link MqttServer#rewriteProperties} 统一实现，
	 * 集群跨节点转发走同一条路径，两边语义保持一致。
	 *
	 * @param context    发布上下文
	 * @param properties 原始 MQTT 5.0 属性（非 null 且非空）
	 * @return 重写后的属性；null 表示消息已过期，应丢弃
	 */
	private MqttProperties rewriteProperties(PublishContext context, MqttProperties properties) {
		return mqttServer.rewriteProperties(context.getTopic(), properties,
			context.getPublishReceivedAt(), context.getTimestamp());
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
				String topic = context.getTopic();
				String clientId = subscribe.getClientId();
				// MQTT 5.0 No Local（规范 3.8.3.1）: 订阅者即发布者时跳过
				if (subscribe.isNoLocal() && clientId.equals(publisherClientId)) {
					logger.debug("Mqtt Topic:{} skip forwarding to clientId:{} due to No Local flag", topic, clientId);
					continue;
				}
				ChannelContext clientContext = Tio.getByBsId(tioConfig, clientId);
				if (clientContext == null || clientContext.isClosed()) {
					// 离线订阅者不再直接丢弃：持久会话按 QoS 进入离线队列，重连后回放
					queueOfflineMessage(context, clientId, subscribe.getMqttQoS(), properties);
					continue;
				}
				// 发送消息
				boolean retain = subscribe.isRetainAsPublished() && context.isRetain();
				mqttServer.publish(clientContext, clientId, topic, context.getPayload(),
					context.getQos(), subscribe.getMqttQoS(), retain, properties);
			}
		} catch (Throwable e) {
			logger.error("Subscription forward error", e);
		}
	}

	/**
	 * 订阅者离线时按会话持久性决定入队或丢弃。
	 * <p>
	 * QoS1/2 消息始终尝试入队（由 {@code IMqttSessionManager} 判定会话是否持久、容量与 TTL）；
	 * QoS0 消息默认丢弃，可通过 {@code mqueueStoreQos0} 打开。
	 */
	private void queueOfflineMessage(PublishContext context, String subscriberClientId, int subMqttQoS, MqttProperties properties) {
		Message offlineMessage = new Message();
		offlineMessage.setMessageType(MessageType.UP_STREAM);
		offlineMessage.setTopic(context.getTopic());
		offlineMessage.setPayload(context.getPayload());
		offlineMessage.setQos(context.getQos().value());
		offlineMessage.setRetain(false);
		offlineMessage.setDup(false);
		offlineMessage.setFromClientId(context.getClientId());
		offlineMessage.setFromUsername(context.getUsername());
		offlineMessage.setPeerHost(context.getPeerHost());
		offlineMessage.setNode(context.getNodeName());
		offlineMessage.setTimestamp(context.getTimestamp());
		offlineMessage.setPublishReceivedAt(context.getPublishReceivedAt());
		offlineMessage.setProperties(properties);
		mqttServer.queueOfflineMessage(offlineMessage, subscriberClientId, subMqttQoS);
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
