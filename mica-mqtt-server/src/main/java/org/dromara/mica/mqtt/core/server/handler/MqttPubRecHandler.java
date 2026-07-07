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
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttPubRecReasonCode;
import org.dromara.mica.mqtt.codec.codes.MqttPubRelReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPubReplyMessageVariableHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * PUBREC 消息处理器。
 *
 * @author L.cm
 */
public class MqttPubRecHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttPubRecHandler.class);

	private final IMqttSessionManager sessionManager;

	public MqttPubRecHandler(MqttServerCreator serverCreator,
						 ExecutorService executor,
						 TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.sessionManager = serverCreator.getSessionManager();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.PUBREC};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage rawMessage) {
		MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) rawMessage.variableHeader();
		String clientId = context.getBsId();
		int packetId = variableHeader.messageId();
		byte reasonCode = getReasonCode(variableHeader);
		logger.debug("PubRec - clientId:{} packetId:{} reasonCode:0x{}", clientId, packetId, Integer.toHexString(reasonCode & 0xFF));
		MqttPendingPublish pendingPublish = sessionManager.getPendingPublish(clientId, packetId);
		if (pendingPublish == null) {
			return;
		}
		if (reasonCode != MqttPubRecReasonCode.SUCCESS.value()) {
			logger.warn("PubRec failure - clientId:{} packetId:{} reasonCode:0x{}", clientId, packetId, Integer.toHexString(reasonCode & 0xFF));
			// 对端拒绝 QoS2 PUBLISH 后不会进入 PUBREL 阶段，本地 pending 要立即收敛。
			pendingPublish.onPubAckReceived();
			sessionManager.removePendingPublish(clientId, packetId);
			return;
		}
		pendingPublish.onPubAckReceived();
		MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.QOS1, false, 0);
		// PUBREL 在 MQTT 5.0 中同样可以携带 reason code，3.x 连接由 encoder 保持兼容。
		MqttPubReplyMessageVariableHeader pubRelVariableHeader = new MqttPubReplyMessageVariableHeader(
			packetId, MqttPubRelReasonCode.SUCCESS.value(), MqttProperties.NO_PROPERTIES);
		MqttMessage pubRelMessage = new MqttMessage(fixedHeader, pubRelVariableHeader);

		pendingPublish.setPubRelMessage(pubRelMessage);
		pendingPublish.startPubRelRetransmissionTimer(taskService, context);

		boolean result = Tio.send(context, pubRelMessage);
		logger.debug("Publish - PubRel send clientId:{} packetId:{} result:{}", clientId, packetId, result);
	}

	private byte getReasonCode(MqttMessageIdVariableHeader variableHeader) {
		if (variableHeader instanceof MqttPubReplyMessageVariableHeader) {
			return ((MqttPubReplyMessageVariableHeader) variableHeader).reasonCode();
		}
		return MqttPubRecReasonCode.SUCCESS.value();
	}
}
