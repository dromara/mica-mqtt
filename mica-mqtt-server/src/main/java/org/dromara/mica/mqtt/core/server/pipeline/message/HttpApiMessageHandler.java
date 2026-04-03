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
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
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
	public boolean handle(Message message) {
		if (MessageType.HTTP_API != message.getMessageType()) {
			return true;
		}

		String topic = message.getTopic();
		byte[] payload = message.getPayload();
		MqttQoS mqttQoS = MqttQoS.valueOf(message.getQos());
		boolean retain = message.isRetain();

		// 发布到所有订阅者
		mqttServer.publishAll(topic, payload, mqttQoS, retain);

		// 触发消息监听器
		try {
			onHttpApiMessage(topic, mqttQoS, message);
		} catch (Throwable e) {
			logger.error("Http API message listener error", e);
		}

		return true;
	}

	private void onHttpApiMessage(String topic, MqttQoS mqttQoS, Message message) {
		if (messageListener == null) {
			return;
		}

		String clientId = message.getClientId();
		// 构造 context
		ServerChannelContext context = new ServerChannelContext(mqttServer.getServerConfig());
		context.setBsId(clientId);
		context.setUserId(MessageType.HTTP_API.name());

		// 构造 MqttPublishMessage
		MqttPublishMessage publishMessage = MqttPublishMessage.builder()
			.topicName(topic)
			.qos(mqttQoS)
			.retained(message.isRetain())
			.payload(message.getPayload())
			.build();

		messageListener.onMessage(context, clientId, topic, mqttQoS, publishMessage);
	}

	@Override
	public int getOrder() {
		return 100; // HTTP API 消息处理优先级高
	}

}
