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
import org.dromara.mica.mqtt.codec.codes.MqttSubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption;
import org.dromara.mica.mqtt.codec.message.properties.MqttSubscribeProperties;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption.RetainedHandlingPolicy;
import org.dromara.mica.mqtt.codec.message.builder.MqttTopicSubscription;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
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
public class MqttSubscribeHandler extends AbstractMqttMessageHandler {
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
	public void handle(ChannelContext context, MqttMessage rawMessage) {
		MqttSubscribeMessage message = (MqttSubscribeMessage) rawMessage;
		String clientId = context.getBsId();
		int packetId = message.variableHeader().messageId();
		// MQTT 5.0 Subscription Identifier：spec 3.8.4 / 3.3.2.3.5 允许在 SUBSCRIBE 一级
		// properties 中携带 varint（1 ~ 268,435,455）。该 id 关联到本次 SUBSCRIBE 的所有 topic filter。
		Integer subscribeSubscriptionId = extractSubscribeSubscriptionId(context, message);
		List<MqttTopicSubscription> topicSubscriptionList = message.payload().topicSubscriptions();
		List<MqttSubAckReasonCode> reasonCodeList = new ArrayList<>();
		List<String> subscribedTopicList = new ArrayList<>();
		List<String> retainTopicList = new ArrayList<>();
		boolean enableSubscribeValidator = subscribeValidator != null;
		for (MqttTopicSubscription subscription : topicSubscriptionList) {
			String topicFilter = subscription.topicFilter();
			try {
				TopicUtil.validateTopicFilter(topicFilter);
			} catch (IllegalArgumentException e) {
				// SUBACK reason code 数量必须与 SUBSCRIBE topic filter 数量保持一致。
				reasonCodeList.add(MqttSubAckReasonCode.TOPIC_FILTER_INVALID);
				logger.error("Subscribe - clientId:{} username:{} topicFilter:{} invalid packetId:{}", clientId, context.getUserId(), topicFilter, packetId, e);
				continue;
			}
			MqttQoS mqttQoS = subscription.qualityOfService();
			MqttSubscriptionOption option = subscription.option();
			boolean noLocal = option.isNoLocal();
			if (enableSubscribeValidator && !subscribeValidator.verifyTopicFilter(context, clientId, topicFilter, mqttQoS)) {
				// 仅拒绝当前 topic filter，其他合法订阅仍继续处理并在同一个 SUBACK 中逐项返回结果。
				reasonCodeList.add(MqttSubAckReasonCode.NOT_AUTHORIZED);
				logger.error("Subscribe - clientId:{} username:{} topicFilter:{} mqttQoS:{} 没有订阅权限 packetId:{}", clientId, context.getUserId(), topicFilter, mqttQoS, packetId);
			} else {
				reasonCodeList.add(MqttSubAckReasonCode.qosGranted(mqttQoS));
				subscribedTopicList.add(topicFilter);
				boolean newSubscription = sessionManager.addSubscribe(new TopicFilter(topicFilter), clientId,
					mqttQoS.value(), noLocal, option.isRetainAsPublished(), option.retainHandling().value(),
					subscribeSubscriptionId);
				if (shouldSendRetainedMessage(option.retainHandling(), newSubscription)) {
					retainTopicList.add(topicFilter);
				}
				logger.info("Subscribe - clientId:{} topicFilter:{} mqttQoS:{} noLocal:{} retainAsPublished:{} retainHandling:{} subscriptionId:{} packetId:{}",
					clientId, topicFilter, mqttQoS, noLocal, option.isRetainAsPublished(), option.retainHandling(), subscribeSubscriptionId, packetId);
				publishSubscribedEvent(context, clientId, topicFilter, mqttQoS);
			}
		}
		MqttSubAckMessage subAckMessage = MqttSubAckMessage.builder()
			.addReasonCodes(reasonCodeList.toArray(new MqttSubAckReasonCode[0]))
			.packetId(packetId)
			.build();
		boolean result = Tio.send(context, subAckMessage);
		logger.info("Subscribe - SubAck send clientId:{} subscribedTopicList:{} packetId:{} result:{}", clientId, subscribedTopicList, packetId, result);
		for (String topic : retainTopicList) {
			// 只有成功写入 session 的订阅才触发保留消息补发，失败项不能收到 retained publish。
			executor.submit(() -> sendRetainMessage(context, topic));
		}
	}

	static boolean shouldSendRetainedMessage(RetainedHandlingPolicy retainHandling, boolean newSubscription) {
		return RetainedHandlingPolicy.SEND_AT_SUBSCRIBE == retainHandling
			|| (RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS == retainHandling && newSubscription);
	}

	/**
	 * 提取 SUBSCRIBE 报文级别的 Subscription Identifier。
	 * <p>
	 * spec 3.8.4：客户端在 SUBSCRIBE 报文 properties 中携带 varint，关联到本次所有 topic filter。
	 * spec 3.3.2.3.5：合法范围 1 ~ 268,435,455（varint 4 字节正数上限），0 由服务端视为"未设置"。
	 * 仅对 MQTT 5 客户端解析；3.x 客户端无该字段。
	 *
	 * @param context ChannelContext（用于判定 MQTT 版本）
	 * @param message SUBSCRIBE 报文
	 * @return Subscription Identifier；0 表示未设置
	 */
	private static int extractSubscribeSubscriptionId(ChannelContext context, MqttSubscribeMessage message) {
		if (!org.dromara.mica.mqtt.codec.MqttCodecUtil.isMqtt5(context)) {
			return 0;
		}
		Integer subscriptionId = new MqttSubscribeProperties(message.variableHeader().properties()).getSubscriptionIdentifier();
		if (subscriptionId == null) {
			return 0;
		}
		int value = subscriptionId;
		// 防御性校验：超过 varint 上限（268,435,455）或 <= 0 时忽略，避免与 codec 层 varint 解码冲突
		if (value < 1 || value > 0x0FFFFFFF) {
			return 0;
		}
		return value;
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
			if (isHighLevelQoS) {
				MqttPendingPublish pendingPublish = new MqttPendingPublish(publishMessage, mqttQoS);
				sessionManager.addPendingPublish(clientId, messageId, pendingPublish);
				pendingPublish.startPublishRetransmissionTimer(taskService, context);
			}
			boolean result = Tio.send(context, publishMessage);
			logger.debug("Subscribe - RetainMessage send clientId:{} topic:{} qos:{} messageId:{} result:{}",
				clientId, retainMessage.getTopic(), mqttQoS, messageId, result);
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
