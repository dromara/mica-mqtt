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
import net.dreamlu.mica.net.core.TioConfig;
import net.dreamlu.mica.net.utils.hutool.StrUtil;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttCodecUtil;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttConnectMessage;
import org.dromara.mica.mqtt.codec.message.MqttConnAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttConnectVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttConnectPayload;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnectProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.MqttServerProperties;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.pipeline.IMqttMessagePipeline;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * CONNECT 消息处理器。
 *
 * @author L.cm
 */
public class MqttConnectHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttConnectHandler.class);
	/**
	 * 2 倍客户端 keepAlive 时间
	 */
	private static final long KEEP_ALIVE_UNIT = 2000L;

	private final IMqttServerUniqueIdService uniqueIdService;
	private final IMqttServerAuthHandler authHandler;
	private final IMqttConnectStatusListener connectStatusListener;
	private final IMqttMessageStore messageStore;
	private final IMqttSessionManager sessionManager;
	private final IMqttMessagePipeline messagePipeline;
	private final long heartbeatTimeout;

	public MqttConnectHandler(MqttServerCreator serverCreator,
						 ExecutorService executor,
						 TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.uniqueIdService = serverCreator.getUniqueIdService();
		this.authHandler = serverCreator.getAuthHandler();
		this.connectStatusListener = serverCreator.getConnectStatusListener();
		this.messageStore = serverCreator.getMessageStore();
		this.sessionManager = serverCreator.getSessionManager();
		this.messagePipeline = serverCreator.getMessagePipeline();
		this.heartbeatTimeout = serverCreator.getHeartbeatTimeout() == null
			? TioConfig.DEFAULT_HEARTBEAT_TIMEOUT
			: serverCreator.getHeartbeatTimeout();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.CONNECT};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage rawMessage) {
		MqttConnectMessage mqttMessage = (MqttConnectMessage) rawMessage;
		MqttConnectPayload payload = mqttMessage.payload();
		String clientId = payload.clientIdentifier();
		String userName = payload.username();
		String password = payload.password();
		MqttConnectVariableHeader variableHeader = mqttMessage.variableHeader();
		boolean requestProblemInformation = isRequestProblemInformation(context, variableHeader);
		boolean assignedClientId = StrUtil.isBlank(clientId);
		// 1. 获取唯一 id
		String uniqueId = uniqueIdService.getUniqueId(context, clientId, userName, password);
		// MQTT 5.0 允许客户端使用空 clientId；默认 uniqueIdService 返回空时由服务端兜底分配。
		if (StrUtil.isBlank(uniqueId) && assignedClientId && MqttCodecUtil.isMqtt5(context)) {
			uniqueId = StrUtil.getNanoId();
		}
		// 2. uniqueId 不能为空
		if (StrUtil.isBlank(uniqueId)) {
			connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED,
				0, false, requestProblemInformation);
			return;
		}
		// 3. 认证
		if (authHandler != null && !authHandler.verifyAuthenticate(context, uniqueId, clientId, userName, password)) {
			connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
				0, false, requestProblemInformation);
			return;
		}
		// 认证成功
		context.setAccepted(true);
		// 4. 互踢逻辑
		ChannelContext otherContext = Tio.getByBsId(context.getTioConfig(), uniqueId);
		if (otherContext != null) {
			Tio.unbindBsId(otherContext);
			cleanSession(uniqueId);
			String remark = String.format("uniqueId:[%s] clientId:[%s] 被踢出，请检查是否有相同 clientId 互踢，新 contextId:[%s]",
				uniqueId, clientId, context.getId());
			Tio.remove(otherContext, remark, ChannelContext.CloseCode.KICK_EACH_OTHER);
		}
		// 4.5 广播上线消息
		sendConnected(context, uniqueId);
		// 5. 绑定 uniqueId / username
		Tio.bindBsId(context, uniqueId);
		if (StrUtil.isNotBlank(userName)) {
			Tio.bindUser(context, userName);
		}
		// 6. 心跳超时
		int keepAliveSeconds = variableHeader.keepAliveTimeSeconds();
		// mqtt 5.0 Server Keep Alive：服务端可接管客户端心跳
		int serverKeepAliveSeconds = 0;
		int configuredKeepAlive = serverCreator.getMqttServerProperties().getServerKeepAlive();
		if (configuredKeepAlive > 0) {
			// Server Keep Alive 是服务端在 CONNACK 下发的覆盖值，客户端 CONNECT 中不会携带该属性。
			serverKeepAliveSeconds = configuredKeepAlive;
		}
		long keepAliveTimeout = keepAliveSeconds * KEEP_ALIVE_UNIT;
		if (keepAliveSeconds > 0 && heartbeatTimeout != keepAliveTimeout) {
			context.setHeartbeatTimeout(keepAliveTimeout);
		}
		// mqtt 5.0 Server Keep Alive：当服务端实际接管心跳时，覆盖 heartbeatTimeout
		if (serverKeepAliveSeconds > 0) {
			context.setHeartbeatTimeout(serverKeepAliveSeconds * KEEP_ALIVE_UNIT);
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
			willMessage.setPeerHost(clientNode.getPeerHost());
			willMessage.setNode(serverCreator.getNodeName());
			messageStore.addWillMessage(uniqueId, willMessage);
		}
		// 9. 返回 ack
		connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_ACCEPTED,
			serverKeepAliveSeconds, assignedClientId, requestProblemInformation);
		// 10. 在线通知
		final String finalUniqueId = uniqueId;
		executor.execute(() -> {
			try {
				connectStatusListener.online(context, finalUniqueId, userName);
			} catch (Throwable e) {
				logger.error("Mqtt server uniqueId:{} clientId:{} online notify error.", finalUniqueId, clientId, e);
			}
		});
	}

	private void connAckByReturnCode(String clientId, String uniqueId, ChannelContext context, MqttConnectReasonCode returnCode,
									 int serverKeepAlive, boolean assignedClientId, boolean requestProblemInformation) {
		MqttConnAckMessage message = MqttConnAckMessage.builder()
			.returnCode(returnCode)
			.sessionPresent(false)
			.properties(buildConnAckProperties(uniqueId, returnCode, serverKeepAlive, assignedClientId, requestProblemInformation).getProperties())
			.build();
		boolean result = Tio.send(context, message);
		if (returnCode.isAccepted()) {
			logger.info("Connect successful, clientId: {} uniqueId:{} result:{}", clientId, uniqueId, result);
		} else {
			logger.error("Connect error - clientId: {} uniqueId:{} returnCode:{} result:{}", clientId, uniqueId, returnCode, result);
		}
	}

	private MqttConnAckProperties buildConnAckProperties(String uniqueId, MqttConnectReasonCode returnCode, int serverKeepAlive,
														 boolean assignedClientId, boolean requestProblemInformation) {
		// 失败 CONNACK 只返回诊断信息，避免把服务端能力位误宣告给未成功建立的连接。
		if (!returnCode.isAccepted() && requestProblemInformation) {
			return new MqttConnAckProperties().setReasonString(returnCode.toString());
		}
		MqttConnAckProperties connAckProperties = new MqttConnAckProperties();
		if (!returnCode.isAccepted()) {
			return connAckProperties;
		}
		MqttServerProperties properties = serverCreator.getMqttServerProperties();
		connAckProperties
			.setReceiveMaximum(properties.getReceiveMaximum())
			.setRetainAvailable(properties.isRetainAvailable())
			// 协议宣告值不能大于实际解码上限，否则客户端会按错误上限发送大包。
			.setMaximumPacketSize(Math.min(properties.getMaximumPacketSize(), serverCreator.getMaxBytesInMessage()))
			.setTopicAliasMaximum(properties.getTopicAliasMaximum())
			.setWildcardSubscriptionAvailable(properties.isWildcardSubscriptionAvailable())
			.setSharedSubscriptionAvailable(properties.isSharedSubscriptionAvailable())
			.setSubscriptionIdentifiersAvailable(properties.isSubscriptionIdentifierAvailable());
		setMaximumQosProperty(connAckProperties, properties.getMaximumQos());
		// 仅当 serverKeepAlive > 0 时才下发该字段，避免污染 3.x 客户端
		if (serverKeepAlive > 0) {
			connAckProperties.setServerKeepAlive(serverKeepAlive);
		}
		if (assignedClientId && StrUtil.isNotBlank(uniqueId)) {
			connAckProperties.setAssignedClientIdentifier(uniqueId);
		}
		return connAckProperties;
	}

	/**
	 * MQTT 5.0 规范 3.2.2.3.4：Maximum QoS 属性只能为 0 或 1；
	 * 属性缺省表示服务端支持 QoS 2。
	 */
	static void setMaximumQosProperty(MqttConnAckProperties connAckProperties, int maximumQos) {
		if (maximumQos < 2) {
			connAckProperties.setMaximumQos(maximumQos);
		}
	}

	private boolean isRequestProblemInformation(ChannelContext context, MqttConnectVariableHeader variableHeader) {
		if (!MqttCodecUtil.isMqtt5(context)) {
			return false;
		}
		// MQTT 5.0 默认允许返回问题信息；只有客户端显式 false 时才抑制 Reason String。
		Boolean requestProblemInformation = new MqttConnectProperties(variableHeader.properties()).getRequestProblemInformation();
		return requestProblemInformation == null || requestProblemInformation;
	}

	private void sendConnected(ChannelContext context, String uniqueId) {
		Message message = new Message();
		message.setClientId(uniqueId);
		message.setMessageType(MessageType.CONNECT);
		message.setNode(serverCreator.getNodeName());
		message.setTimestamp(System.currentTimeMillis());
		Node clientNode = context.getClientNode();
		message.setPeerHost(clientNode.getPeerHost());
		messagePipeline.handle(message);
	}

	private void cleanSession(String clientId) {
		try {
			sessionManager.remove(clientId);
		} catch (Throwable throwable) {
			logger.error("Mqtt server clientId:{} session clean error.", clientId, throwable);
		}
	}
}
