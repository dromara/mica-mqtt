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

package org.dromara.mica.mqtt.core.server.dispatcher;

import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.codec.MqttMessageBuilders;
import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Node;
import org.tio.core.Tio;
import org.tio.server.ServerChannelContext;

import java.util.Objects;

/**
 * 内部消息转发抽象
 *
 * @author L.cm
 */
public abstract class AbstractMqttMessageDispatcher implements org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher {
	private static final Logger logger = LoggerFactory.getLogger(AbstractMqttMessageDispatcher.class);
	protected MqttServer mqttServer;
	protected IMqttMessageListener messageListener;
	protected IMqttSessionManager sessionManager;

	public void config(MqttServer mqttServer) {
		this.mqttServer = mqttServer;
		this.messageListener = mqttServer.getServerCreator().getMessageListener();
		this.sessionManager = mqttServer.getServerCreator().getSessionManager();
	}

	/**
	 * 转发所有消息
	 *
	 * @param message Message
	 */
	public abstract void sendAll(Message message);

	@Override
	public boolean send(Message message) {
		Objects.requireNonNull(mqttServer, "MqttServer require not Null.");
		// 1. 先发送到本服务
		MessageType messageType = message.getMessageType();
		if (MessageType.SUBSCRIBE == messageType) {
			sessionManager.addSubscribe(message.getTopic(), message.getFromClientId(), message.getQos());
		} else if (MessageType.UNSUBSCRIBE == messageType) {
			sessionManager.removeSubscribe(message.getTopic(), message.getFromClientId());
		} else if (MessageType.UP_STREAM == messageType) {
			mqttServer.sendToClient(message.getTopic(), message);
		} else if (MessageType.DOWN_STREAM == messageType) {
			mqttServer.sendToClient(message.getTopic(), message);
		} else if (MessageType.HTTP_API == messageType) {
			String topic = message.getTopic();
			// http rest api 消息也会转发到此
			MqttQoS mqttQoS = MqttQoS.valueOf(message.getQos());
			mqttServer.publishAll(topic, message.getPayload(), mqttQoS, message.isRetain());
			// 触发消息
			try {
				onHttpApiMessage(topic, mqttQoS, message);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		} else if (MessageType.DISCONNECT == messageType) {
			String clientId = message.getClientId();
			ChannelContext context = mqttServer.getChannelContext(clientId);
			if (context != null) {
				Tio.remove(context, "Mqtt server delete clients:" + clientId);
			}
		}
		sendAll(message);
		return true;
	}

	private void onHttpApiMessage(String topic, MqttQoS mqttQoS, Message message) {
		String clientId = message.getClientId();
		// 构造 context
		ServerChannelContext context = new ServerChannelContext(mqttServer.getServerConfig());
		Node serverNode = mqttServer.getTioServer().getServerNode();
		context.setServerNode(serverNode);
		Node clientNode = mqttServer.getWebServer().getTioServer().getServerNode();
		context.setClientNode(clientNode);
		context.setBsId(clientId);
		context.setUserId(MessageType.HTTP_API.name());
		// 构造 MqttPublishMessage
		MqttPublishMessage publishMessage = MqttMessageBuilders.publish()
			.topicName(topic)
			.qos(mqttQoS)
			.retained(message.isRetain())
			.payload(message.getPayload())
			.build();
		messageListener.onMessage(context, clientId, topic, mqttQoS, publishMessage, message);
	}

}
