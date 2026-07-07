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
import org.dromara.mica.mqtt.codec.codes.MqttUnSubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttUnSubAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttUnSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttUnSubAckBuilder;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.event.IMqttSessionListener;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * UNSUBSCRIBE 消息处理器。
 *
 * @author L.cm
 */
public class MqttUnSubscribeHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttUnSubscribeHandler.class);

	private final IMqttSessionManager sessionManager;
	private final IMqttSessionListener sessionListener;

	public MqttUnSubscribeHandler(MqttServerCreator serverCreator,
							  ExecutorService executor,
							  TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.sessionManager = serverCreator.getSessionManager();
		this.sessionListener = serverCreator.getSessionListener();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.UNSUBSCRIBE};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage rawMessage) {
		MqttUnSubscribeMessage message = (MqttUnSubscribeMessage) rawMessage;
		String clientId = context.getBsId();
		int packetId = message.variableHeader().messageId();
		List<String> topicFilterList = message.payload().topics();
		for (String topicFilter : topicFilterList) {
			sessionManager.removeSubscribe(topicFilter, clientId);
			publishUnsubscribedEvent(context, clientId, topicFilter);
		}
		logger.info("UnSubscribe - clientId:{} Topic:{} packetId:{}", clientId, topicFilterList, packetId);
		MqttUnSubAckBuilder builder = MqttUnSubAckMessage.builder()
			.packetId(packetId);
		// MQTT 5.0 UNSUBACK payload 需要逐 topic 返回 reason code；当前删除操作保持幂等成功。
		for (int i = 0; i < topicFilterList.size(); i++) {
			builder.addReasonCode(MqttUnSubAckReasonCode.SUCCESS);
		}
		MqttUnSubAckMessage unSubMessage = builder.build();
		boolean result = Tio.send(context, unSubMessage);
		logger.debug("UnSubscribe - UnSubAck send clientId:{} result:{}", clientId, result);
	}

	private void publishUnsubscribedEvent(ChannelContext context, String clientId, String topicFilter) {
		if (sessionListener == null) {
			return;
		}
		executor.execute(() -> {
			try {
				sessionListener.onUnsubscribed(context, clientId, topicFilter);
			} catch (Throwable e) {
				logger.error("Mqtt server clientId:{} topicFilter:{} onUnsubscribed error.", clientId, topicFilter, e);
			}
		});
	}
}
