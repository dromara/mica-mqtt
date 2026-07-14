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

package org.dromara.mica.mqtt.core.server.pipeline.message;

import net.dreamlu.mica.net.server.ServerChannelContext;
import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP API 消息处理器
 *
 * @author L.cm
 */
public class HttpApiMessageHandler extends BaseMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(HttpApiMessageHandler.class);

	public HttpApiMessageHandler(MqttServer mqttServer) {
		super(mqttServer);
	}

	@Override
	public MessageType[] messageTypes() {
		return new MessageType[]{MessageType.HTTP_API};
	}

	@Override
	public boolean handle(Message message) {
		String topic = message.getTopic();
		byte[] payload = message.getPayload();
		MqttQoS mqttQoS = MqttQoS.valueOf(message.getQos());
		boolean retain = message.isRetain();
		MqttProperties properties = resolveProperties(message);

		// 发布到所有订阅者，透传 MQTT 5.0 属性（Payload Format / Content Type / Response Topic / Correlation Data 等）
		mqttServer.publishAll(topic, payload, mqttQoS, retain, properties);

		// 触发消息监听器
		try {
			onHttpApiMessage(topic, mqttQoS, message, properties);
		} catch (Throwable e) {
			logger.error("Http API message listener error", e);
		}

		return true;
	}

	/**
	 * 解析消息的 MQTT 5.0 属性，优先取 {@link Message#getProperties()}，
	 * 集群传输场景下属性已被序列化为 {@link Message#getPropertiesBytes()}，需要按需反序列化。
	 *
	 * @param message Message
	 * @return MQTT 5.0 属性，可能为 {@code null}
	 */
	private static MqttProperties resolveProperties(Message message) {
		MqttProperties properties = message.getProperties();
		if (properties != null && !properties.isEmpty()) {
			return properties;
		}
		byte[] propertiesBytes = message.getPropertiesBytes();
		if (propertiesBytes != null && propertiesBytes.length > 0) {
			return MqttDecoder.decodeProperties(propertiesBytes);
		}
		return null;
	}

	private void onHttpApiMessage(String topic, MqttQoS mqttQoS, Message message, MqttProperties properties) {
		if (messageListener == null) {
			return;
		}

		String clientId = message.getClientId();
		// 构造 context
		ServerChannelContext context = new ServerChannelContext(mqttServer.getServerConfig());
		context.setBsId(clientId);
		context.setUserId(MessageType.HTTP_API.name());

		// 构造 MqttPublishMessage，透传 MQTT 5.0 属性
		MqttPublishMessage publishMessage = MqttPublishMessage.builder()
			.topicName(topic)
			.qos(mqttQoS)
			.retained(message.isRetain())
			.payload(message.getPayload())
			.properties(properties)
			.build();

		messageListener.onMessage(context, clientId, topic, mqttQoS, publishMessage);
	}

	@Override
	public int getOrder() {
		return 100; // HTTP API 消息处理优先级高
	}

}
