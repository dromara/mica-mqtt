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

package org.dromara.mica.mqtt.core.server.support;

import org.dromara.mica.mqtt.codec.*;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.MqttServerProcessor;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerPublishPermission;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerSubscribeValidator;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.event.IMqttSessionListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Node;
import org.tio.core.Tio;
import org.tio.core.TioConfig;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.timer.TimerTaskService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * mqtt broker 处理器
 *
 * @author L.cm
 */
public class DefaultMqttServerProcessor implements MqttServerProcessor {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttServerProcessor.class);
	/**
	 * 2 倍客户端 keepAlive 时间
	 */
	private static final long KEEP_ALIVE_UNIT = 2000L;
	private final MqttServerCreator serverCreator;
	private final long heartbeatTimeout;
	private final IMqttMessageStore messageStore;
	private final IMqttSessionManager sessionManager;
	private final IMqttServerAuthHandler authHandler;
	private final IMqttServerUniqueIdService uniqueIdService;
	private final IMqttServerSubscribeValidator subscribeValidator;
	private final IMqttServerPublishPermission publishPermission;
	private final IMqttMessageDispatcher messageDispatcher;
	private final IMqttConnectStatusListener connectStatusListener;
	private final IMqttSessionListener sessionListener;
	private final IMqttMessageListener messageListener;
	private final TimerTaskService taskService;
	private final ExecutorService executor;

	public DefaultMqttServerProcessor(MqttServerCreator serverCreator,
									  TimerTaskService taskService,
									  ExecutorService executor) {
		this.serverCreator = serverCreator;
		this.heartbeatTimeout = serverCreator.getHeartbeatTimeout() == null ? TioConfig.DEFAULT_HEARTBEAT_TIMEOUT : serverCreator.getHeartbeatTimeout();
		this.messageStore = serverCreator.getMessageStore();
		this.sessionManager = serverCreator.getSessionManager();
		this.authHandler = serverCreator.getAuthHandler();
		this.uniqueIdService = serverCreator.getUniqueIdService();
		this.subscribeValidator = serverCreator.getSubscribeValidator();
		this.publishPermission = serverCreator.getPublishPermission();
		this.messageDispatcher = serverCreator.getMessageDispatcher();
		this.connectStatusListener = serverCreator.getConnectStatusListener();
		this.sessionListener = serverCreator.getSessionListener();
		this.messageListener = serverCreator.getMessageListener();
		this.taskService = taskService;
		this.executor = executor;
	}

	@Override
	public void processConnect(ChannelContext context, MqttConnectMessage mqttMessage) {
		MqttConnectPayload payload = mqttMessage.payload();
		// 参数
		String clientId = payload.clientIdentifier();
		String userName = payload.userName();
		String password = payload.password();
		// 1. 获取唯一id，用于 mqtt 内部绑定，部分用户的业务采用 userName 作为唯一id，故抽象之，默认：uniqueId == clientId
		String uniqueId = uniqueIdService.getUniqueId(context, clientId, userName, password);
		// 2. 客户端必须提供 uniqueId, 不管 cleanSession 是否为1, 此处没有参考标准协议实现
		if (StrUtil.isBlank(uniqueId)) {
			connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
			return;
		}
		// 3. 认证
		if (authHandler != null && !authHandler.verifyAuthenticate(context, uniqueId, clientId, userName, password)) {
			connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
			return;
		}
		// 认证成功
		context.setAccepted(true);
		// 4. 判断 uniqueId 是否在多个地方使用，如果在其他地方有使用，先解绑
		ChannelContext otherContext = Tio.getByBsId(context.getTioConfig(), uniqueId);
		if (otherContext != null) {
			Tio.unbindBsId(otherContext);
			String remark = String.format("uniqueId:[%s] clientId:[%s] now bind on new context id:[%s]", uniqueId, clientId, context.getId());
			Tio.remove(otherContext, remark);
			cleanSession(uniqueId);
		}
		// 4.5 广播上线消息，避免一个 uniqueId 多个集群服务器中连接。
		sendConnected(context, uniqueId);
		// 5. 绑定 uniqueId、保存 username
		Tio.bindBsId(context, uniqueId);
		if (StrUtil.isNotBlank(userName)) {
			Tio.bindUser(context, userName);
		}
		// 6. 心跳超时时间，当然这个值如果小于全局配置（默认：120s），定时检查的时间间隔还是以全局为准，只是在判断时用此值
		MqttConnectVariableHeader variableHeader = mqttMessage.variableHeader();
		int keepAliveSeconds = variableHeader.keepAliveTimeSeconds();
		// keepAlive * keepAliveBackoff * 2 时间作为服务端心跳超时时间，如果配置同全局默认不设置，节约内存
		long keepAliveTimeout = keepAliveSeconds * KEEP_ALIVE_UNIT;
		if (keepAliveSeconds > 0 && heartbeatTimeout != keepAliveTimeout) {
			context.setHeartbeatTimeout(keepAliveTimeout);
		}
		// 7. session 处理，先默认全部连接关闭时清除，mqtt5 为 CleanStart，
		// 按照 mqtt 协议的规则是下一次连接时清除，emq 是添加了全局 session 超时，关闭时激活 session 有效期倒计时
//		boolean cleanSession = variableHeader.isCleanSession();
//		if (cleanSession) {
//			// TODO L.cm 考虑 session 处理 可参数： https://www.emqx.com/zh/blog/mqtt-session
//			// mqtt v5.0 会话超时时间
//			MqttProperties properties = variableHeader.properties();
//			Integer sessionExpiryInterval = properties.getPropertyValue(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL);
//			System.out.println(sessionExpiryInterval);
//		}
		// 8. 存储遗嘱消息
		boolean willFlag = variableHeader.isWillFlag();
		if (willFlag) {
			Message willMessage = new Message();
			willMessage.setMessageType(MessageType.DOWN_STREAM);
			willMessage.setFromClientId(uniqueId);
			willMessage.setFromUsername(userName);
			willMessage.setTopic(payload.willTopic());
			byte[] willMessageInBytes = payload.willMessageInBytes();
			if (willMessageInBytes != null) {
				willMessage.setPayload(willMessageInBytes);
			}
			willMessage.setQos(variableHeader.willQos());
			willMessage.setRetain(variableHeader.isWillRetain());
			willMessage.setTimestamp(System.currentTimeMillis());
			Node clientNode = context.getClientNode();
			// 客户端 ip:端口
			willMessage.setPeerHost(clientNode.getPeerHost());
			willMessage.setNode(serverCreator.getNodeName());
			messageStore.addWillMessage(uniqueId, willMessage);
		}
		// 9. 返回 ack
		connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_ACCEPTED);
		// 10. 在线状态
		executor.execute(() -> {
			try {
				connectStatusListener.online(context, uniqueId, userName);
			} catch (Throwable e) {
				logger.error("Mqtt server uniqueId:{} clientId:{} online notify error.", uniqueId, clientId, e);
			}
		});
	}

	private static void connAckByReturnCode(String clientId, String uniqueId, ChannelContext context, MqttConnectReasonCode returnCode) {
		MqttConnAckMessage message = MqttMessageBuilders.connAck()
			.returnCode(returnCode)
			.sessionPresent(false)
			.build();
		boolean result = Tio.send(context, message);
		if (returnCode.isAccepted()) {
			logger.info("Connect successful, clientId: {} uniqueId:{} result:{}", clientId, uniqueId, result);
		} else {
			logger.error("Connect error - clientId: {} uniqueId:{} returnCode:{} result:{}", clientId, uniqueId, returnCode, result);
		}
	}

	private void sendConnected(ChannelContext context, String uniqueId) {
		Message message = new Message();
		message.setClientId(uniqueId);
		message.setMessageType(MessageType.CONNECT);
		message.setNode(serverCreator.getNodeName());
		message.setTimestamp(System.currentTimeMillis());
		Node clientNode = context.getClientNode();
		message.setPeerHost(clientNode.getPeerHost());
		messageDispatcher.send(message);
	}

	private void cleanSession(String clientId) {
		try {
			sessionManager.remove(clientId);
		} catch (Throwable throwable) {
			logger.error("Mqtt server clientId:{} session clean error.", clientId, throwable);
		}
	}

	@Override
	public void processPublish(ChannelContext context, MqttPublishMessage message) {
		String clientId = context.getBsId();
		MqttFixedHeader fixedHeader = message.fixedHeader();
		MqttQoS mqttQoS = fixedHeader.qosLevel();
		MqttPublishVariableHeader variableHeader = message.variableHeader();
		String topicName = variableHeader.topicName();
		// 1. 权限判断，在 MQTT v3.1 和 v3.1.1 协议中，发布操作被拒绝后服务器无任何报文错误返回，这是协议设计的一个缺陷。但在 MQTT v5.0 协议上已经支持应答一个相应的错误报文。
		if (publishPermission != null && !publishPermission.verifyPermission(context, clientId, topicName, mqttQoS, fixedHeader.isRetain())) {
			logger.error("Mqtt clientId:{} username:{} topic:{} no publish permission.", clientId, context.getUserId(), topicName);
			return;
		}
		// 2. 处理发布逻辑
		int packetId = variableHeader.packetId();
		logger.debug("Publish - clientId:{} topicName:{} mqttQoS:{} packetId:{}", clientId, topicName, mqttQoS, packetId);
		switch (mqttQoS) {
			case QOS0:
				invokeListenerForPublish(context, clientId, mqttQoS, topicName, message);
				break;
			case QOS1:
				invokeListenerForPublish(context, clientId, mqttQoS, topicName, message);
				if (packetId != -1) {
					MqttMessage messageAck = MqttMessageBuilders.pubAck()
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
					// 添加重试
					sessionManager.addPendingQos2Publish(clientId, packetId, pendingQos2Publish);
					pendingQos2Publish.startPubRecRetransmitTimer(taskService, context);
					// 发送消息
					boolean resultPubRec = Tio.send(context, pubRecMessage);
					logger.debug("Publish - PubRec send clientId:{} topicName:{} mqttQoS:{} packetId:{} result:{}", clientId, topicName, mqttQoS, packetId, resultPubRec);
				}
				break;
			case FAILURE:
			default:
				break;
		}
	}

	@Override
	public void processPubAck(ChannelContext context, MqttMessageIdVariableHeader variableHeader) {
		int packetId = variableHeader.messageId();
		String clientId = context.getBsId();
		logger.debug("PubAck - clientId:{}, packetId:{}", clientId, packetId);
		MqttPendingPublish pendingPublish = sessionManager.getPendingPublish(clientId, packetId);
		if (pendingPublish == null) {
			return;
		}
		pendingPublish.onPubAckReceived();
		sessionManager.removePendingPublish(clientId, packetId);
	}

	@Override
	public void processPubRec(ChannelContext context, MqttMessageIdVariableHeader variableHeader) {
		String clientId = context.getBsId();
		int packetId = variableHeader.messageId();
		logger.debug("PubRec - clientId:{}, packetId:{}", clientId, packetId);
		MqttPendingPublish pendingPublish = sessionManager.getPendingPublish(clientId, packetId);
		if (pendingPublish == null) {
			return;
		}
		pendingPublish.onPubAckReceived();
		MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.QOS1, false, 0);
		MqttMessage pubRelMessage = new MqttMessage(fixedHeader, variableHeader);

		pendingPublish.setPubRelMessage(pubRelMessage);
		pendingPublish.startPubRelRetransmissionTimer(taskService, context);

		boolean result = Tio.send(context, pubRelMessage);
		logger.debug("Publish - PubRel send clientId:{} packetId:{} result:{}", clientId, packetId, result);
	}

	@Override
	public void processPubRel(ChannelContext context, MqttMessageIdVariableHeader variableHeader) {
		String clientId = context.getBsId();
		int packetId = variableHeader.messageId();
		logger.debug("PubRel - clientId:{}, packetId:{}", clientId, packetId);
		MqttPendingQos2Publish pendingQos2Publish = sessionManager.getPendingQos2Publish(clientId, packetId);
		if (pendingQos2Publish != null) {
			MqttPublishMessage incomingPublish = pendingQos2Publish.getIncomingPublish();
			String topicName = incomingPublish.variableHeader().topicName();
			MqttFixedHeader incomingFixedHeader = incomingPublish.fixedHeader();
			MqttQoS mqttQoS = incomingFixedHeader.qosLevel();
			invokeListenerForPublish(context, clientId, mqttQoS, topicName, incomingPublish);
			pendingQos2Publish.onPubRelReceived();
			sessionManager.removePendingQos2Publish(clientId, packetId);
		}
		MqttMessage message = MqttMessageFactory.newMessage(
			new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.QOS0, false, 0),
			MqttMessageIdVariableHeader.from(packetId), null);

		boolean result = Tio.send(context, message);
		logger.debug("Publish - PubComp send clientId:{} packetId:{} result:{}", clientId, packetId, result);
	}

	@Override
	public void processPubComp(ChannelContext context, MqttMessageIdVariableHeader variableHeader) {
		int packetId = variableHeader.messageId();
		String clientId = context.getBsId();
		logger.debug("PubComp - clientId:{}, packetId:{}", clientId, packetId);
		MqttPendingPublish pendingPublish = sessionManager.getPendingPublish(clientId, packetId);
		if (pendingPublish != null) {
			pendingPublish.onPubCompReceived();
			sessionManager.removePendingPublish(clientId, packetId);
		}
	}

	@Override
	public void processSubscribe(ChannelContext context, MqttSubscribeMessage message) {
		String clientId = context.getBsId();
		int packetId = message.variableHeader().messageId();
		// 1. 校验订阅的 topicFilter
		List<MqttTopicSubscription> topicSubscriptionList = message.payload().topicSubscriptions();
		List<MqttQoS> grantedQosList = new ArrayList<>();
		// 校验订阅
		List<String> subscribedTopicList = new ArrayList<>();
		boolean enableSubscribeValidator = subscribeValidator != null;
		for (MqttTopicSubscription subscription : topicSubscriptionList) {
			String topicFilter = subscription.topicFilter();
			// 校验 topicFilter 是否合法
			TopicUtil.validateTopicFilter(topicFilter);
			MqttQoS mqttQoS = subscription.qualityOfService();
			// 校验是否可以订阅
			if (enableSubscribeValidator && !subscribeValidator.verifyTopicFilter(context, clientId, topicFilter, mqttQoS)) {
				grantedQosList.add(MqttQoS.FAILURE);
				logger.error("Subscribe - clientId:{} username:{} topicFilter:{} mqttQoS:{} valid failed packetId:{}", clientId, context.getUserId(), topicFilter, mqttQoS, packetId);
			} else {
				grantedQosList.add(mqttQoS);
				subscribedTopicList.add(topicFilter);
				sessionManager.addSubscribe(topicFilter, clientId, mqttQoS.value());
				logger.info("Subscribe - clientId:{} topicFilter:{} mqttQoS:{} packetId:{}", clientId, topicFilter, mqttQoS, packetId);
				publishSubscribedEvent(context, clientId, topicFilter, mqttQoS);
			}
		}
		// 3. 返回 ack
		MqttMessage subAckMessage = MqttMessageBuilders.subAck()
			.addGrantedQosList(grantedQosList)
			.packetId(packetId)
			.build();
		boolean result = Tio.send(context, subAckMessage);
		logger.info("Subscribe - SubAck send clientId:{} subscribedTopicList:{} packetId:{} result:{}", clientId, subscribedTopicList, packetId, result);
		// 4. 发送保留消息
		for (String topic : subscribedTopicList) {
			executor.submit(() -> {
				List<Message> retainMessageList = messageStore.getRetainMessage(topic);
				if (retainMessageList != null && !retainMessageList.isEmpty()) {
					for (Message retainMessage : retainMessageList) {
						messageDispatcher.send(clientId, retainMessage);
					}
				}
			});
		}
	}

	/**
	 * 发送订阅事件
	 *
	 * @param context     ChannelContext
	 * @param clientId    clientId
	 * @param topicFilter topicFilter
	 * @param mqttQoS     MqttQoS
	 */
	private void publishSubscribedEvent(ChannelContext context, String clientId, String topicFilter, MqttQoS mqttQoS) {
		if (sessionListener == null) {
			return;
		}
		executor.execute(() -> {
			try {
				sessionListener.onSubscribed(context, clientId, topicFilter, mqttQoS);
			} catch (Throwable e) {
				logger.error("Mqtt server clientId:{} topicFilter:{} onUnsubscribed error.", clientId, topicFilter, e);
			}
		});
	}

	@Override
	public void processUnSubscribe(ChannelContext context, MqttUnsubscribeMessage message) {
		String clientId = context.getBsId();
		int packetId = message.variableHeader().messageId();
		List<String> topicFilterList = message.payload().topics();
		for (String topicFilter : topicFilterList) {
			sessionManager.removeSubscribe(topicFilter, clientId);
			publishUnsubscribedEvent(context, clientId, topicFilter);
		}
		logger.info("UnSubscribe - clientId:{} Topic:{} packetId:{}", clientId, topicFilterList, packetId);
		MqttMessage unSubMessage = MqttMessageBuilders.unsubAck()
			.packetId(packetId)
			.build();
		boolean result = Tio.send(context, unSubMessage);
		logger.debug("UnSubscribe - UnSubAck send clientId:{} result:{}", clientId, result);
	}

	/**
	 * 发送取消订阅事件
	 *
	 * @param context     ChannelContext
	 * @param clientId    clientId
	 * @param topicFilter topicFilter
	 */
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

	@Override
	public void processPingReq(ChannelContext context) {
		String clientId = context.getBsId();
		boolean result = Tio.send(context, MqttMessage.PINGRESP);
		logger.debug("PingReq - PingResp send clientId:{} result:{}", clientId, result);
	}

	@Override
	public void processDisConnect(ChannelContext context) {
		String clientId = context.getBsId();
		logger.info("DisConnect - clientId:{} contextId:{}", clientId, context.getId());
		// 设置正常断开的标识
		context.setBizStatus(true);
		Tio.remove(context, "Mqtt DisConnect");
	}

	/**
	 * 处理订阅的消息
	 *
	 * @param context        ChannelContext
	 * @param clientId       clientId
	 * @param topicName      topicName
	 * @param publishMessage MqttPublishMessage
	 */
	private void invokeListenerForPublish(ChannelContext context, String clientId, MqttQoS mqttQoS,
										  String topicName, MqttPublishMessage publishMessage) {
		MqttFixedHeader fixedHeader = publishMessage.fixedHeader();
		boolean isRetain = fixedHeader.isRetain();
		byte[] payload = publishMessage.payload();
		// 1. retain 消息逻辑
		if (isRetain) {
			// qos == 0 or payload is none,then clear previous retain message
			if (MqttQoS.QOS0 == mqttQoS || payload == null || payload.length == 0) {
				this.messageStore.clearRetainMessage(topicName);
			} else {
				Message retainMessage = new Message();
				retainMessage.setTopic(topicName);
				retainMessage.setQos(mqttQoS.value());
				retainMessage.setPayload(payload);
				retainMessage.setFromClientId(clientId);
				retainMessage.setMessageType(MessageType.DOWN_STREAM);
				retainMessage.setRetain(true);
				retainMessage.setDup(fixedHeader.isDup());
				retainMessage.setTimestamp(System.currentTimeMillis());
				Node clientNode = context.getClientNode();
				// 客户端 ip:端口
				retainMessage.setPeerHost(clientNode.getPeerHost());
				retainMessage.setNode(serverCreator.getNodeName());
				this.messageStore.addRetainMessage(topicName, retainMessage);
			}
		}
		// 2. message
		MqttPublishVariableHeader variableHeader = publishMessage.variableHeader();
		// messageId
		int packetId = variableHeader.packetId();
		Message message = new Message();
		message.setId(packetId);
		// 注意：broker 消息转发是不需要设置 toClientId 而是应该按 topic 找到订阅的客户端进行发送
		message.setFromClientId(clientId);
		message.setTopic(topicName);
		message.setQos(mqttQoS.value());
		if (payload != null) {
			message.setPayload(payload);
		}
		message.setMessageType(MessageType.UP_STREAM);
		message.setRetain(isRetain);
		message.setDup(fixedHeader.isDup());
		message.setTimestamp(System.currentTimeMillis());
		Node clientNode = context.getClientNode();
		// 客户端 ip:端口
		message.setPeerHost(clientNode.getPeerHost());
		message.setNode(serverCreator.getNodeName());
		// 3. 消息发布
		if (messageListener != null) {
			executor.submit(() -> {
				try {
					messageListener.onMessage(context, clientId, topicName, mqttQoS, publishMessage, message);
				} catch (Throwable e) {
					logger.error(e.getMessage(), e);
				}
			});
		}
		// 4. 消息流转
		executor.submit(() -> {
			try {
				messageDispatcher.send(message);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		});
	}

}
