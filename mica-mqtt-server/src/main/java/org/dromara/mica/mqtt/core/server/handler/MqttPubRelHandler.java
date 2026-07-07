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
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttMessageFactory;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttPubCompReasonCode;
import org.dromara.mica.mqtt.codec.codes.MqttPubRelReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPubReplyMessageVariableHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * PUBREL 消息处理器。
 *
 * @author L.cm
 */
public class MqttPubRelHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttPubRelHandler.class);

	private final IMqttSessionManager sessionManager;
	private final MqttPublishHandler publishHandler;

	public MqttPubRelHandler(MqttServerCreator serverCreator,
							 ExecutorService executor,
							 TimerTaskService taskService,
							 MqttPublishHandler publishHandler) {
		super(serverCreator, executor, taskService);
		this.sessionManager = serverCreator.getSessionManager();
		this.publishHandler = publishHandler;
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.PUBREL};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage rawMessage) {
		MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) rawMessage.variableHeader();
		String clientId = context.getBsId();
		int packetId = variableHeader.messageId();
		byte reasonCode = getReasonCode(variableHeader);
		logger.debug("PubRel - clientId:{} packetId:{} reasonCode:0x{}", clientId, packetId, Integer.toHexString(reasonCode & 0xFF));
		MqttPendingQos2Publish pendingQos2Publish = sessionManager.getPendingQos2Publish(clientId, packetId);
		if (reasonCode != MqttPubRelReasonCode.SUCCESS.value()) {
			logger.warn("PubRel failure - clientId:{} packetId:{} reasonCode:0x{}", clientId, packetId, Integer.toHexString(reasonCode & 0xFF));
			if (pendingQos2Publish != null) {
				pendingQos2Publish.onPubRelReceived();
				sessionManager.removePendingQos2Publish(clientId, packetId);
			}
		} else if (pendingQos2Publish != null) {
			MqttPublishMessage incomingPublish = pendingQos2Publish.getIncomingPublish();
			publishHandler.invokeListenerForPublish(context, incomingPublish);
			pendingQos2Publish.onPubRelReceived();
			sessionManager.removePendingQos2Publish(clientId, packetId);
		}
		MqttPubReplyMessageVariableHeader pubCompVariableHeader = new MqttPubReplyMessageVariableHeader(
			packetId, MqttPubCompReasonCode.SUCCESS.value(), MqttProperties.NO_PROPERTIES);
		MqttMessage message = MqttMessageFactory.newMessage(
			new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.QOS0, false, 0),
			pubCompVariableHeader, null);
		boolean result = Tio.send(context, message);
		logger.debug("Publish - PubComp send clientId:{} packetId:{} result:{}", clientId, packetId, result);
	}

	private byte getReasonCode(MqttMessageIdVariableHeader variableHeader) {
		if (variableHeader instanceof MqttPubReplyMessageVariableHeader) {
			return ((MqttPubReplyMessageVariableHeader) variableHeader).reasonCode();
		}
		return MqttPubRelReasonCode.SUCCESS.value();
	}
}
