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

package org.dromara.mica.mqtt.core.server.cluster;

import org.dromara.mica.mqtt.codec.MqttMessageBuilders;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.server.ServerChannelContext;

/**
 * mqtt 集群消息处理
 *
 * @author L.cm
 */
public class MqttClusterMessageListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttClusterMessageListener.class);
	private final String nodeName;
	private final IMqttMessageListener messageListener;
	private final IMqttSessionManager sessionManager;
	private final MqttServer mqttServer;

	public MqttClusterMessageListener(MqttServer mqttServer) {
		this.nodeName = mqttServer.getServerCreator().getNodeName();
		this.messageListener = mqttServer.getServerCreator().getMessageListener();
		this.sessionManager = mqttServer.getServerCreator().getSessionManager();
		this.mqttServer = mqttServer;
	}

	/**
	 * 来着集群的消息
	 *
	 * @param message Message
	 */
	public void onMessage(Message message) {
		MessageType messageType = message.getMessageType();
		String topic = message.getTopic();
		if (MessageType.CONNECT == messageType) {
			// 1. 如果一个 clientId 在集群多个服务上连接时断开其他的
			String node = message.getNode();
			if (nodeName.equals(node)) {
				return;
			}
			String clientId = message.getClientId();
			ChannelContext context = Tio.getByBsId(mqttServer.getServerConfig(), clientId);
			if (context != null) {
				Tio.remove(context, "clientId:[" + clientId + "] now bind on mqtt node:" + node);
			}
		} else if (MessageType.SUBSCRIBE == messageType) {
			// http api 订阅广播
			String formClientId = message.getFromClientId();
			ChannelContext context = mqttServer.getChannelContext(formClientId);
			if (context != null) {
				sessionManager.addSubscribe(topic, formClientId, message.getQos());
			}
		} else if (MessageType.UNSUBSCRIBE == messageType) {
			// http api 取消订阅广播
			String formClientId = message.getFromClientId();
			ChannelContext context = mqttServer.getChannelContext(formClientId);
			if (context != null) {
				sessionManager.removeSubscribe(topic, formClientId);
			}
		} else if (MessageType.UP_STREAM == messageType) {
			// mqtt 上行消息，需要发送到对应的监听的客户端
			mqttServer.sendToClient(topic, message);
		} else if (MessageType.DOWN_STREAM == messageType) {
			// http rest api 下行消息也会转发到此
			mqttServer.sendToClient(topic, message);
		} else if (MessageType.HTTP_API == messageType) {
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
	}

	private void onHttpApiMessage(String topic, MqttQoS mqttQoS, Message message) {
		String clientId = message.getClientId();
		// 构造 context
		ServerChannelContext context = new ServerChannelContext(mqttServer.getServerConfig());
//		Node serverNode = mqttServer.getTioServer().getServerNode();
//		context.setServerNode(serverNode);
//		Node clientNode = mqttServer.getWebServer().getServerNode();
//		context.setClientNode(clientNode);
		context.setBsId(clientId);
		context.setUserId(MessageType.HTTP_API.name());
		// 构造 MqttPublishMessage
		MqttPublishMessage publishMessage = MqttMessageBuilders.publish()
			.topicName(topic)
			.qos(mqttQoS)
			.retained(message.isRetain())
			.payload(message.getPayload())
			.build();
		messageListener.onMessage(context, clientId, topic, mqttQoS, publishMessage);
	}

}
