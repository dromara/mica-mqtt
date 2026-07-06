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
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttTopicSubscription;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerSubscribeValidator;
import org.dromara.mica.mqtt.core.server.event.IMqttSessionListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * SUBSCRIBE 消息处理器。
 *
 * @author L.cm
 */
public class MqttSubscribeHandler extends AbstractMqttMessageHandler implements IMqttMessageHandler<MqttSubscribeMessage> {
	private static final Logger logger = LoggerFactory.getLogger(MqttSubscribeHandler.class);

	private final IMqttServerSubscribeValidator subscribeValidator;
	private final IMqttSessionManager sessionManager;
	private final IMqttSessionListener sessionListener;
	private final IMqttMessageStore messageStore;

	public MqttSubscribeHandler(MqttServerCreator serverCreator,
							ExecutorService executor,
							TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.subscribeValidator = serverCreator.getSubscribeValidator();
		this.sessionManager = serverCreator.getSessionManager();
		this.sessionListener = serverCreator.getSessionListener();
		this.messageStore = serverCreator.getMessageStore();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.SUBSCRIBE};
	}

	@Override
	public void handle(ChannelContext context, MqttSubscribeMessage message) {
		String clientId = context.getBsId();
		int packetId = message.variableHeader().messageId();
		List<MqttTopicSubscription> topicSubscriptionList = message.payload().topicSubscriptions();
		List<MqttQoS> grantedQosList = new ArrayList<>();
		List<String> subscribedTopicList = new ArrayList<>();
		boolean enableSubscribeValidator = subscribeValidator != null;
		for (MqttTopicSubscription subscription : topicSubscriptionList) {
			String topicFilter = subscription.topicFilter();
			TopicUtil.validateTopicFilter(topicFilter);
			MqttQoS mqttQoS = subscription.qualityOfService();
			boolean noLocal = subscription.option().isNoLocal();
			if (enableSubscribeValidator && !subscribeValidator.verifyTopicFilter(context, clientId, topicFilter, mqttQoS)) {
				grantedQosList.add(MqttQoS.FAILURE);
				logger.error("Subscribe - clientId:{} username:{} topicFilter:{} mqttQoS:{} 没有订阅权限 packetId:{}", clientId, context.getUserId(), topicFilter, mqttQoS, packetId);
			} else {
				grantedQosList.add(mqttQoS);
				subscribedTopicList.add(topicFilter);
				sessionManager.addSubscribe(new TopicFilter(topicFilter), clientId, mqttQoS.value(), noLocal);
				logger.info("Subscribe - clientId:{} topicFilter:{} mqttQoS:{} noLocal:{} packetId:{}", clientId, topicFilter, mqttQoS, noLocal, packetId);
				publishSubscribedEvent(context, clientId, topicFilter, mqttQoS);
			}
		}
		MqttMessage subAckMessage = MqttSubAckMessage.builder()
			.addGrantedQosList(grantedQosList)
			.packetId(packetId)
			.build();
		boolean result = Tio.send(context, subAckMessage);
		logger.info("Subscribe - SubAck send clientId:{} subscribedTopicList:{} packetId:{} result:{}", clientId, subscribedTopicList, packetId, result);
		for (String topic : subscribedTopicList) {
			executor.submit(() -> sendRetainMessage(context, topic));
		}
	}

	private void sendRetainMessage(ChannelContext context, String topic) {
		List<Message> retainMessageList = messageStore.getRetainMessage(topic);
		if (retainMessageList == null || retainMessageList.isEmpty()) {
			return;
		}
		String clientId = context.getBsId();
		for (Message retainMessage : retainMessageList) {
			MqttQoS mqttQoS = MqttQoS.valueOf(retainMessage.getQos());
			boolean isHighLevelQoS = MqttQoS.QOS1 == mqttQoS || MqttQoS.QOS2 == mqttQoS;
			int messageId = isHighLevelQoS ? sessionManager.getPacketId(clientId) : -1;
			MqttPublishMessage publishMessage = MqttPublishMessage.builder()
				.topicName(retainMessage.getTopic())
				.payload(retainMessage.getPayload())
				.qos(mqttQoS)
				.retained(true)
				.messageId(messageId)
				.properties(retainMessage.getProperties())
				.build();
			Tio.send(context, publishMessage);
		}
	}

	private void publishSubscribedEvent(ChannelContext context, String clientId, String topicFilter, MqttQoS mqttQoS) {
		if (sessionListener == null) {
			return;
		}
		executor.execute(() -> {
			try {
				sessionListener.onSubscribed(context, clientId, topicFilter, mqttQoS);
			} catch (Throwable e) {
				logger.error("Mqtt server clientId:{} topicFilter:{} onSubscribed error.", clientId, topicFilter, e);
			}
		});
	}
}
