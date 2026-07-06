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
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * PUBCOMP 消息处理器。
 *
 * @author L.cm
 */
public class MqttPubCompHandler extends AbstractMqttMessageHandler implements IMqttMessageHandler<MqttMessageIdVariableHeader> {
	private static final Logger logger = LoggerFactory.getLogger(MqttPubCompHandler.class);

	private final IMqttSessionManager sessionManager;

	public MqttPubCompHandler(MqttServerCreator serverCreator,
						  ExecutorService executor,
						  TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.sessionManager = serverCreator.getSessionManager();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.PUBCOMP};
	}

	@Override
	public void handle(ChannelContext context, MqttMessageIdVariableHeader variableHeader) {
		int packetId = variableHeader.messageId();
		String clientId = context.getBsId();
		logger.debug("PubComp - clientId:{}, packetId:{}", clientId, packetId);
		MqttPendingPublish pendingPublish = sessionManager.getPendingPublish(clientId, packetId);
		if (pendingPublish != null) {
			pendingPublish.onPubCompReceived();
			sessionManager.removePendingPublish(clientId, packetId);
		}
	}
}
