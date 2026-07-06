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

package org.dromara.mica.mqtt.core.server.handler;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.utils.mica.IntPair;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttMessageFactory;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPubAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerPublishPermission;
import org.dromara.mica.mqtt.core.server.pipeline.IMqttPublishPipeline;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * PUBLISH 消息处理器。
 *
 * @author L.cm
 */
public class MqttPublishHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttPublishHandler.class);

	private final IMqttServerPublishPermission publishPermission;
	private final IMqttSessionManager sessionManager;
	private final IMqttPublishPipeline publishPipeline;

	public MqttPublishHandler(MqttServerCreator serverCreator,
						  ExecutorService executor,
						  TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.publishPermission = serverCreator.getPublishPermission();
		this.sessionManager = serverCreator.getSessionManager();
		this.publishPipeline = serverCreator.getPublishPipeline();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.PUBLISH};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage rawMessage) {
		MqttPublishMessage message = (MqttPublishMessage) rawMessage;
		String clientId = context.getBsId();
		MqttFixedHeader fixedHeader = message.fixedHeader();
		MqttQoS mqttQoS = fixedHeader.qosLevel();
		MqttPublishVariableHeader variableHeader = message.variableHeader();
		String topicName = variableHeader.topicName();
		// 1. 权限判断
		if (publishPermission != null && !publishPermission.verifyPermission(context, clientId, topicName, mqttQoS, fixedHeader.isRetain())) {
			logger.error("Mqtt clientId:{} username:{} topic:{} 没有发布权限。", clientId, context.getUserId(), topicName);
			return;
		}
		int packetId = variableHeader.packetId();
		logger.debug("Publish - clientId:{} topicName:{} mqttQoS:{} packetId:{}", clientId, topicName, mqttQoS, packetId);
		switch (mqttQoS) {
			case QOS0:
				invokeListenerForPublish(context, clientId, mqttQoS, topicName, message);
				break;
			case QOS1:
				invokeListenerForPublish(context, clientId, mqttQoS, topicName, message);
				if (packetId != -1) {
					MqttMessage messageAck = MqttPubAckMessage.builder()
						.packetId(packetId)
						.build();
					boolean resultPubAck = Tio.send(context, messageAck);
					logger.debug("Publish - PubAck send clientId:{} topicName:{} mqttQoS:{} packetId:{} result:{}", clientId, topicName, mqttQoS, packetId, resultPubAck);
				}
				break;
			case QOS2:
				if (packetId != -1) {
					MqttFixedHeader pubRecFixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.QOS0, false, 0);
					MqttMessage pubRecMessage = new MqttMessage(pubRecFixedHeader, MqttMessageIdVariableHeader.from(packetId));
					MqttPendingQos2Publish pendingQos2Publish = new MqttPendingQos2Publish(message, pubRecMessage);
					sessionManager.addPendingQos2Publish(clientId, packetId, pendingQos2Publish);
					pendingQos2Publish.startPubRecRetransmitTimer(taskService, context);
					boolean resultPubRec = Tio.send(context, pubRecMessage);
					logger.debug("Publish - PubRec send clientId:{} topicName:{} mqttQoS:{} packetId:{} result:{}", clientId, topicName, mqttQoS, packetId, resultPubRec);
				}
				break;
			case FAILURE:
			default:
				break;
		}
	}

	private void invokeListenerForPublish(ChannelContext context, String clientId, MqttQoS mqttQoS,
										  String topicName, MqttPublishMessage publishMessage) {
		PublishContext publishContext = buildPublishContext(context, clientId, mqttQoS, topicName, publishMessage);
		if (publishContext != null) {
			executor.execute(() -> {
				try {
					publishPipeline.handle(publishContext);
				} catch (Throwable e) {
					logger.error("Mqtt server clientId:{} topic:{} publish pipeline handle error.", clientId, topicName, e);
				}
			});
		}
	}

	private PublishContext buildPublishContext(ChannelContext context, String clientId, MqttQoS mqttQoS,
											   String topicName, MqttPublishMessage publishMessage) {
		MqttFixedHeader fixedHeader = publishMessage.fixedHeader();
		MqttPublishVariableHeader variableHeader = publishMessage.variableHeader();
		Node clientNode = context.getClientNode();
		boolean isRetain = fixedHeader.isRetain();
		byte[] payload = publishMessage.payload();
		String username = context.getUserId();
		String actualTopic = topicName;
		if (isRetain) {
			IntPair<String> retainPair = TopicUtil.retainTopicName(topicName);
			int timeOut = retainPair.getKey();
			if (timeOut < 0) {
				logger.error("MqttPublishMessage topic {} 不符合 $retain/${ttl}/topic 规则.", topicName);
				return null;
			}
			actualTopic = retainPair.getValue();
		}
		return PublishContext.builder()
			.publishMessage(publishMessage)
			.context(context)
			.clientId(clientId)
			.username(username)
			.topic(actualTopic)
			.qos(mqttQoS)
			.dup(fixedHeader.isDup())
			.retain(isRetain)
			.payload(payload)
			.messageId(variableHeader.packetId() != -1 ? variableHeader.packetId() : null)
			.properties(variableHeader.properties())
			.peerHost(clientNode.getPeerHost())
			.nodeName(serverCreator.getNodeName())
			.timestamp(System.currentTimeMillis())
			.publishReceivedAt(System.currentTimeMillis())
			.build();
	}

	/**
	 * 暴露给 PubRelHandler 共用的发布上下文构建逻辑。
	 *
	 * @param context        ChannelContext
	 * @param publishMessage MqttPublishMessage
	 * @return PublishContext
	 */
	public PublishContext buildPublishContext(ChannelContext context, MqttPublishMessage publishMessage) {
		MqttFixedHeader fixedHeader = publishMessage.fixedHeader();
		String topicName = publishMessage.variableHeader().topicName();
		return buildPublishContext(context, context.getBsId(), fixedHeader.qosLevel(), topicName, publishMessage);
	}

	/**
	 * 暴露给 PubRelHandler 共用的发布处理逻辑。
	 *
	 * @param context        ChannelContext
	 * @param publishMessage MqttPublishMessage
	 */
	public void invokeListenerForPublish(ChannelContext context, MqttPublishMessage publishMessage) {
		MqttFixedHeader fixedHeader = publishMessage.fixedHeader();
		String topicName = publishMessage.variableHeader().topicName();
		invokeListenerForPublish(context, context.getBsId(), fixedHeader.qosLevel(), topicName, publishMessage);
	}
}
