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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.*;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Node;
import org.tio.core.Tio;
import org.tio.utils.hutool.CollUtil;
import org.tio.utils.timer.TimerTaskService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 默认的 mqtt 消息处理器
 *
 * @author L.cm
 */
public class DefaultMqttClientProcessor implements IMqttClientProcessor {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttClientProcessor.class);
	private final MqttClientCreator mqttClientCreator;
	private final IMqttClientSession clientSession;
	private final IMqttClientConnectListener connectListener;
	private final IMqttClientGlobalMessageListener globalMessageListener;
	private final IMqttClientMessageIdGenerator messageIdGenerator;
	private final TimerTaskService taskService;
	private final ExecutorService executor;

	public DefaultMqttClientProcessor(MqttClientCreator mqttClientCreator) {
		this.mqttClientCreator = mqttClientCreator;
		this.clientSession = mqttClientCreator.getClientSession();
		this.connectListener = mqttClientCreator.getConnectListener();
		this.globalMessageListener = mqttClientCreator.getGlobalMessageListener();
		this.messageIdGenerator = mqttClientCreator.getMessageIdGenerator();
		this.taskService = mqttClientCreator.getTaskService();
		this.executor = mqttClientCreator.getMqttExecutor();
	}

	@Override
	public void processDecodeFailure(ChannelContext context, MqttMessage message, Throwable ex) {
		// 客户端失败，默认记录异常日志
		logger.error(ex.getMessage(), ex);
	}

	@Override
	public void processConAck(ChannelContext context, MqttConnAckMessage message) {
		MqttConnAckVariableHeader connAckVariableHeader = message.variableHeader();
		MqttConnectReasonCode returnCode = connAckVariableHeader.connectReturnCode();
		switch (returnCode) {
			case CONNECTION_ACCEPTED:
				// 1. 连接成功的日志
				context.setAccepted(true);
				if (logger.isInfoEnabled()) {
					Node node = context.getServerNode();
					logger.info("MqttClient contextId:{} connection:{}:{} succeeded!", context.getId(), node.getIp(), node.getPort());
				}
				// 2. 发布连接通知
				publishConnectEvent(context);
				// 3. 发送订阅，不管服务端是否存在 session 都发送
				reSendSubscription(context);
				break;
			case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD:
			case CONNECTION_REFUSED_IDENTIFIER_REJECTED:
			case CONNECTION_REFUSED_NOT_AUTHORIZED:
			case CONNECTION_REFUSED_SERVER_UNAVAILABLE:
			case CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION:
			default:
				String remark = "MqttClient connect error error ReturnCode:" + returnCode;
				Tio.close(context, remark);
				break;
		}
	}

	/**
	 * 发布连接成功事件
	 *
	 * @param context ChannelContext
	 */
	private void publishConnectEvent(ChannelContext context) {
		// 先判断是否配置监听
		if (connectListener == null) {
			return;
		}
		// 触发客户端连接事件
		executor.submit(() -> {
			try {
				connectListener.onConnected(context, context.isReconnect());
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		});
	}

	/**
	 * 批量重新订阅
	 *
	 * @param context ChannelContext
	 */
	private void reSendSubscription(ChannelContext context) {
		// 0. 全局订阅
		Set<MqttTopicSubscription> globalSubscribe = mqttClientCreator.getGlobalSubscribe();
		if (globalSubscribe != null && !globalSubscribe.isEmpty()) {
			globalReSendSubscription(context, globalSubscribe);
		}
		List<MqttClientSubscription> reSubscriptionList = clientSession.getSubscriptions();
		// 1. 判断是否为空
		if (reSubscriptionList.isEmpty()) {
			return;
		}
		// 2. 订阅的数量
		int subscribedSize = reSubscriptionList.size();
		// 重新订阅批次大小
		int reSubscribeBatchSize = mqttClientCreator.getReSubscribeBatchSize();
		if (subscribedSize <= reSubscribeBatchSize) {
			reSendSubscription(context, reSubscriptionList);
		} else {
			List<List<MqttClientSubscription>> partitionList = CollUtil.partition(reSubscriptionList, reSubscribeBatchSize);
			for (List<MqttClientSubscription> partition : partitionList) {
				reSendSubscription(context, partition);
			}
		}
	}

	/**
	 * 全局订阅，不需要存储 session
	 *
	 * @param context                  ChannelContext
	 * @param globalReSubscriptionList globalReSubscriptionList
	 */
	private void globalReSendSubscription(ChannelContext context, Set<MqttTopicSubscription> globalReSubscriptionList) {
		int packetId = messageIdGenerator.getId();
		MqttSubscribeMessage message = MqttMessageBuilders.subscribe()
			.addSubscriptions(globalReSubscriptionList)
			.messageId(packetId)
			.build();
		boolean result = Tio.send(context, message);
		logger.info("MQTT globalReSubscriptionList:{} packetId:{} resubscribing result:{}", globalReSubscriptionList, packetId, result);
	}

	/**
	 * 批量重新订阅
	 *
	 * @param context            ChannelContext
	 * @param reSubscriptionList reSubscriptionList
	 */
	private void reSendSubscription(ChannelContext context, List<MqttClientSubscription> reSubscriptionList) {
		// 2. 批量重新订阅
		List<MqttTopicSubscription> topicSubscriptionList = reSubscriptionList.stream().map(MqttClientSubscription::toTopicSubscription).collect(Collectors.toList());
		int packetId = messageIdGenerator.getId();
		MqttSubscribeMessage message = MqttMessageBuilders.subscribe().addSubscriptions(topicSubscriptionList).messageId(packetId).build();
		MqttPendingSubscription pendingSubscription = new MqttPendingSubscription(reSubscriptionList, message);
		pendingSubscription.startRetransmitTimer(taskService, context);
		clientSession.addPaddingSubscribe(packetId, pendingSubscription);
		// gitee issues #IB72L6 先添加并启动重试，再发送订阅
		boolean result = Tio.send(context, message);
		logger.info("MQTT subscriptionList:{} packetId:{} resubscribing result:{}", reSubscriptionList, packetId, result);
	}

	@Override
	public void processSubAck(ChannelContext context, MqttSubAckMessage message) {
		int packetId = message.variableHeader().messageId();
		logger.debug("MqttClient SubAck packetId:{}", packetId);
		MqttPendingSubscription paddingSubscribe = clientSession.getPaddingSubscribe(packetId);
		if (paddingSubscribe == null) {
			return;
		}
		List<MqttClientSubscription> subscriptionList = paddingSubscribe.getSubscriptionList();
		MqttSubAckPayload subAckPayload = message.payload();
		List<Integer> reasonCodeList = subAckPayload.reasonCodes();
		// reasonCodes 为空
		if (reasonCodeList.isEmpty()) {
			logger.error("MqttClient subscriptionList:{} subscribe failed reasonCodes is empty packetId:{}", subscriptionList, packetId);
			return;
		}
		int reasonCodeListSize = reasonCodeList.size();
		// 找出订阅成功的数据
		List<MqttClientSubscription> subscribedList = new ArrayList<>();
		// MQTT 3.1.1 协议未明确规定批量订阅的返回格式，批量可能只返回一个 reasonCode
		if (reasonCodeListSize == 1) {
			Integer reasonCode = reasonCodeList.get(0);
			// reasonCodes 范围
			if (reasonCode == null || reasonCode < 0 || reasonCode > 2) {
				logger.error("MqttClient subscriptionList:{} subscribe failed reasonCodes:{} packetId:{}", subscriptionList, reasonCode, packetId);
			} else {
				subscribedList.addAll(subscriptionList);
			}
		} else {
			for (int i = 0; i < subscriptionList.size(); i++) {
				MqttClientSubscription subscription = subscriptionList.get(i);
				String topicFilter = subscription.getTopicFilter();
				Integer reasonCode = reasonCodeList.get(i);
				// reasonCodes 范围
				if (reasonCode == null || reasonCode < 0 || reasonCode > 2) {
					logger.error("MqttClient topicFilter:{} subscribe failed reasonCodes:{} packetId:{}", topicFilter, reasonCode, packetId);
				} else {
					subscribedList.add(subscription);
				}
			}
			logger.info("MQTT subscriptionList:{} subscribed successfully packetId:{}", subscribedList, packetId);
		}
		paddingSubscribe.onSubAckReceived();
		clientSession.removePaddingSubscribe(packetId);
		clientSession.addSubscriptionList(subscribedList);
		// 触发已经监听的事件
		subscribedList.forEach(clientSubscription -> {
			String topicFilter = clientSubscription.getTopicFilter();
			MqttQoS mqttQoS = clientSubscription.getMqttQoS();
			IMqttClientMessageListener subscriptionListener = clientSubscription.getListener();
			executor.execute(() -> {
				try {
					subscriptionListener.onSubscribed(context, topicFilter, mqttQoS, message);
				} catch (Throwable e) {
					logger.error("MQTT topicFilter:{} subscribed onSubscribed event error.", subscribedList, e);
				}
			});
		});
	}

	@Override
	public void processPublish(ChannelContext context, MqttPublishMessage message) {
		MqttFixedHeader mqttFixedHeader = message.fixedHeader();
		MqttPublishVariableHeader variableHeader = message.variableHeader();
		String topicName = variableHeader.topicName();
		MqttQoS mqttQoS = mqttFixedHeader.qosLevel();
		int packetId = variableHeader.packetId();
		logger.debug("MqttClient received publish topic:{} qoS:{} packetId:{}", topicName, mqttQoS, packetId);
		switch (mqttFixedHeader.qosLevel()) {
			case QOS0:
				invokeListenerForPublish(context, topicName, message);
				break;
			case QOS1:
				invokeListenerForPublish(context, topicName, message);
				if (packetId != -1) {
					MqttMessage messageAck = MqttMessageBuilders.pubAck().packetId(packetId).build();
					boolean resultPubAck = Tio.send(context, messageAck);
					logger.debug("Publish - PubAck send topicName:{} mqttQoS:{} packetId:{} result:{}", topicName, mqttQoS, packetId, resultPubAck);
				}
				break;
			case QOS2:
				if (packetId != -1) {
					MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.QOS0, false, 0);
					MqttMessage pubRecMessage = new MqttMessage(fixedHeader, MqttMessageIdVariableHeader.from(packetId));
					MqttPendingQos2Publish pendingQos2Publish = new MqttPendingQos2Publish(message, pubRecMessage);
					clientSession.addPendingQos2Publish(packetId, pendingQos2Publish);
					pendingQos2Publish.startPubRecRetransmitTimer(taskService, context);
					// 先启动重试再发布消息
					boolean resultPubRec = Tio.send(context, pubRecMessage);
					logger.debug("Publish - PubRec send topicName:{} mqttQoS:{} packetId:{} result:{}", topicName, mqttQoS, packetId, resultPubRec);
				}
				break;
			case FAILURE:
			default:
		}
	}

	@Override
	public void processUnSubAck(MqttUnsubAckMessage message) {
		int packetId = message.variableHeader().messageId();
		logger.debug("MqttClient UnSubAck packetId:{}", packetId);
		MqttPendingUnSubscription pendingUnSubscription = clientSession.getPaddingUnSubscribe(packetId);
		if (pendingUnSubscription == null) {
			return;
		}
		List<String> unSubscriptionTopics = pendingUnSubscription.getTopics();
		logger.info("MQTT Topic:{} successfully unSubscribed packetId:{}", unSubscriptionTopics, packetId);
		pendingUnSubscription.onUnSubAckReceived();
		clientSession.removePaddingUnSubscribe(packetId);
		clientSession.removeSubscriptions(unSubscriptionTopics);
	}

	@Override
	public void processPubAck(MqttPubAckMessage message) {
		int packetId = message.variableHeader().messageId();
		logger.debug("MqttClient PubAck packetId:{}", packetId);
		MqttPendingPublish pendingPublish = clientSession.getPendingPublish(packetId);
		if (pendingPublish == null) {
			return;
		}
		if (logger.isInfoEnabled()) {
			String topicName = pendingPublish.getMessage().variableHeader().topicName();
			logger.info("MQTT Topic:{} successfully PubAck packetId:{}", topicName, packetId);
		}
		pendingPublish.onPubAckReceived();
		clientSession.removePendingPublish(packetId);
	}

	@Override
	public void processPubRec(ChannelContext context, MqttMessage message) {
		int packetId = ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
		logger.debug("MqttClient PubRec packetId:{}", packetId);
		MqttPendingPublish pendingPublish = clientSession.getPendingPublish(packetId);
		if (pendingPublish == null) {
			return;
		}
		pendingPublish.onPubAckReceived();

		MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.QOS1, false, 0);
		MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
		MqttMessage pubRelMessage = new MqttMessage(fixedHeader, variableHeader);

		pendingPublish.setPubRelMessage(pubRelMessage);
		pendingPublish.startPubRelRetransmissionTimer(taskService, context);

		// 发送消息
		boolean result = Tio.send(context, pubRelMessage);
		logger.debug("Publish - PubRec send packetId:{} result:{}", packetId, result);
	}

	@Override
	public void processPubRel(ChannelContext context, MqttMessage message) {
		int packetId = ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
		logger.debug("MqttClient PubRel packetId:{}", packetId);
		MqttPendingQos2Publish pendingQos2Publish = clientSession.getPendingQos2Publish(packetId);
		if (pendingQos2Publish != null) {
			MqttPublishMessage incomingPublish = pendingQos2Publish.getIncomingPublish();
			String topicName = incomingPublish.variableHeader().topicName();
			this.invokeListenerForPublish(context, topicName, incomingPublish);
			pendingQos2Publish.onPubRelReceived();
			clientSession.removePendingQos2Publish(packetId);
		}
		MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.QOS0, false, 0);
		MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(packetId);
		// 发送消息
		boolean result = Tio.send(context, new MqttMessage(fixedHeader, variableHeader));
		logger.debug("Publish - PubRel send packetId:{} result:{}", packetId, result);
	}

	@Override
	public void processPubComp(MqttMessage message) {
		int packetId = ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
		MqttPendingPublish pendingPublish = clientSession.getPendingPublish(packetId);
		if (pendingPublish == null) {
			return;
		}
		if (logger.isInfoEnabled()) {
			String topicName = pendingPublish.getMessage().variableHeader().topicName();
			logger.info("MQTT Topic:{} successfully PubComp", topicName);
		}
		pendingPublish.onPubCompReceived();
		clientSession.removePendingPublish(packetId);
	}

	/**
	 * 处理订阅的消息
	 *
	 * @param context   ChannelContext
	 * @param topicName topicName
	 * @param message   MqttPublishMessage
	 */
	private void invokeListenerForPublish(ChannelContext context, String topicName, MqttPublishMessage message) {
		final byte[] payload = message.payload();
		// 全局消息监听器
		if (globalMessageListener != null) {
			executor.submit(() -> {
				try {
					globalMessageListener.onMessage(context, topicName, message, payload);
				} catch (Throwable e) {
					logger.error(e.getMessage(), e);
				}
			});
		}
		// topic 订阅监听
		List<MqttClientSubscription> subscriptionList = clientSession.getMatchedSubscription(topicName);
		if (subscriptionList.isEmpty()) {
			if (globalMessageListener == null || mqttClientCreator.isDebug()) {
				logger.warn("Mqtt message to accept topic:{} subscriptionList is empty.", topicName);
			} else {
				logger.debug("Mqtt message to accept topic:{} subscriptionList is empty.", topicName);
			}
		} else {
			subscriptionList.forEach(subscription -> {
				IMqttClientMessageListener listener = subscription.getListener();
				executor.submit(() -> {
					try {
						listener.onMessage(context, topicName, message, payload);
					} catch (Throwable e) {
						logger.error(e.getMessage(), e);
					}
				});
			});
		}
	}

}
