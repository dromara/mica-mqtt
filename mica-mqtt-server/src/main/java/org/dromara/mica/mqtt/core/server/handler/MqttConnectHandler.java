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
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttConnectMessage;
import org.dromara.mica.mqtt.codec.message.MqttConnAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttConnectVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttConnectPayload;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnectProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttWillPublishProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.MqttServerProperties;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.pipeline.IMqttMessagePipeline;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.MqttSessionState;
import org.dromara.mica.mqtt.core.server.session.SessionExpireScheduler;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.server.will.WillDelayScheduler;
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
	private final WillDelayScheduler willDelayScheduler;
	private final SessionExpireScheduler sessionExpireScheduler;
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
		this.willDelayScheduler = serverCreator.getWillDelayScheduler();
		this.sessionExpireScheduler = serverCreator.getSessionExpireScheduler();
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
		boolean requestResponseInformation = isRequestResponseInformation(context, variableHeader);
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
				0, false, false, requestProblemInformation, requestResponseInformation);
			return;
		}
		// 3. 认证
		if (authHandler != null) {
			// 账号密码认证
			MqttConnectReasonCode reasonCode = authHandler.verifyAuthenticate(context, uniqueId, clientId, userName, password);
			// 兼容用户返回 null 的场景
			if (reasonCode == null) {
				reasonCode = MqttConnectReasonCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
			}
			// 没有认证成功，返回认证失败
			if (!reasonCode.isAccepted()) {
				connAckByReturnCode(clientId, uniqueId, context, reasonCode,
					0, false, false, requestProblemInformation, requestResponseInformation);
				return;
			}
		}
		if (hasInvalidReceiveMaximum(context, variableHeader)) {
			connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_REFUSED_PROTOCOL_ERROR,
				0, false, false, requestProblemInformation, requestResponseInformation);
			return;
		}
		if (hasInvalidClientMaxPacketSize(context, variableHeader, clientId)) {
			connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_REFUSED_PACKET_TOO_LARGE,
				0, false, false, requestProblemInformation, requestResponseInformation);
			return;
		}
		// 认证成功
		context.setAccepted(true);
		// 4. 互踢逻辑
		ChannelContext otherContext = Tio.getByBsId(context.getTioConfig(), uniqueId);
		// spec 3.2.2.1.1 Session Present：
		//   Clean Start = true  → Session Present = 0
		//   Clean Start = false → 服务端已存在该 ClientID 的 session 时为 1，否则为 0
		// 判定时机必须在互踢 / cleanSession 之前，否则旧 session 状态会被清掉。
		boolean cleanStart = variableHeader.isCleanStart();
		boolean sessionPresent = !cleanStart
			&& (sessionManager.hasSession(uniqueId) || otherContext != null);
		if (otherContext != null) {
			Tio.unbindBsId(otherContext);
			cleanSession(uniqueId);
			String remark = String.format("uniqueId:[%s] clientId:[%s] 被踢出，请检查是否有相同 clientId 互踢，新 contextId:[%s]",
				uniqueId, clientId, context.getId());
			Tio.remove(otherContext, remark, ChannelContext.CloseCode.KICK_EACH_OTHER);
		} else if (cleanStart) {
			// Clean Start=true 无条件丢弃旧会话状态（覆盖「旧连接已断开、仅残留会话」的情况）。
			// spec 3.1.2.4: MQTT 3.x 的 Clean Session 与 MQTT 5 的 Clean Start 位置不同，但语义一致。
			cleanSession(uniqueId);
		}
		// 4.5 广播上线消息
		sendConnected(context, uniqueId);
		// 5. 会话绑定：记录归属连接、是否持久与过期时长
		bindSession(uniqueId, context.getId(), cleanStart, variableHeader);
		// 5.5 绑定 uniqueId / username
		Tio.bindBsId(context, uniqueId);
		if (StrUtil.isNotBlank(userName)) {
			Tio.bindUser(context, userName);
		}
		sessionManager.setClientReceiveMaximum(uniqueId, resolveClientReceiveMaximum(context, variableHeader, uniqueId));
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
		// 7. 会话处理
		// 会话归属、持久标志与过期时长已在第 5 步通过 bindSession 记录。
		// 断开时由 MqttDisConnectHandler（正常 DISCONNECT）与 MqttServerAioListener.onBeforeClose
		// （底层 channel 断开）根据持久标志决定是立即清理还是调度过期：
		//   - 非持久会话 → 立即 sessionManager.remove
		//   - 持久会话 → SessionExpireScheduler.scheduleExpire，到期后清理
		// Session Present 标志已在互踢逻辑前按 spec 3.2.2.1.1 计算并用于 CONNACK。
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
			// MQTT 5.0 Will Delay Interval（spec 3.1.3.5）：仅当 Session Expiry Interval >= Will Delay Interval
			// 时才延迟发送；否则按"立即 Will"流程处理。
			// 仅对 MQTT 5 校验，3.x 客户端没有 WillDelayInterval 属性。
			scheduleWillDelayIfNeeded(context, uniqueId, variableHeader, payload);
		}
		// 9. 返回 ack
		connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_ACCEPTED,
			serverKeepAliveSeconds, assignedClientId, sessionPresent,
			requestProblemInformation, requestResponseInformation);
		// 9.5 会话恢复：CONNACK 之后异步重发在途消息并回放离线队列
		if (sessionPresent) {
			final String resumeUniqueId = uniqueId;
			executor.execute(() -> {
				try {
					resumeSession(context, resumeUniqueId);
				} catch (Throwable e) {
					logger.error("Mqtt server uniqueId:{} session resume error.", resumeUniqueId, e);
				}
			});
		}
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

	/**
	 * MQTT 5.0 Will Delay Interval 调度（spec 3.1.3.5）。
	 * <p>
	 * 规则：
	 * <ol>
	 *     <li>仅当 {@code Will Delay Interval > 0} 且 {@code Session Expiry Interval >= Will Delay Interval}
	 *         时才调度延迟发送；否则走"立即 Will"流程（{@code MqttServerAioListener.onBeforeClose} 处理）。</li>
	 *     <li>当 {@code Will Delay Interval} 大于本端最大允许延迟（4 字节 unsigned）时，截断到 0xFFFFFFFF。</li>
	 *     <li>任务到期后：
	 *         <ul>
	 *             <li>若 {@code messageStore.getWillMessage(clientId)} 仍存在，发送 Will 并清理；</li>
	 *             <li>若已被清理（说明客户端在延迟窗口内成功重连），跳过发送（spec 3.1.3.5.3）。</li>
	 *         </ul>
	 *     </li>
	 * </ol>
	 * 3.x 客户端没有 willDelayInterval 属性，会走到"立即 Will"路径，行为不变。
	 */
	private void scheduleWillDelayIfNeeded(ChannelContext context, String uniqueId,
										  MqttConnectVariableHeader variableHeader, MqttConnectPayload payload) {
		if (!MqttCodecUtil.isMqtt5(context)) {
			return;
		}
		MqttWillPublishProperties willProperties = new MqttWillPublishProperties(payload.willProperties());
		Integer willDelayInterval = willProperties.getWillDelayInterval();
		if (willDelayInterval == null || willDelayInterval <= 0) {
			return;
		}
		// Session Expiry Interval 在 CONNECT 级别 properties 中
		Integer sessionExpiryInterval = variableHeader.properties().<Integer>getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL);
		// spec 3.1.3.5: 仅当 sessionExpiry >= willDelayInterval 时延迟发送。
		// 未声明 Session Expiry Interval 时按 0 处理，立即发送。
		if (sessionExpiryInterval == null || sessionExpiryInterval < willDelayInterval) {
			logger.debug("Will Delay Interval {} ignored: Session Expiry Interval {} < willDelayInterval",
				willDelayInterval, sessionExpiryInterval);
			return;
		}
		// 4 字节 unsigned 上限保护（虽然 spec 允许 0xFFFFFFFF，但保险起见用 long 防止溢出）
		long delayMillis = willDelayInterval.longValue() & 0xFFFFFFFFL;
		// 取消可能存在的旧任务（同 clientId 重连场景）
		willDelayScheduler.cancel(uniqueId);
		willDelayScheduler.schedule(uniqueId, delayMillis, () -> {
			try {
				Message willMessage = messageStore.getWillMessage(uniqueId);
				if (willMessage == null) {
					logger.debug("Will Delay Interval fired for clientId:{} but will message already cleared (likely reconnect)", uniqueId);
					return;
				}
				boolean result = messagePipeline.handle(willMessage);
				logger.debug("Mqtt server clientId:{} send delayed willMessage result:{}.", uniqueId, result);
				messageStore.clearWillMessage(uniqueId);
			} catch (Throwable e) {
				logger.error("Mqtt server clientId:{} send delayed willMessage error.", uniqueId, e);
			}
		});
		logger.debug("Will Delay Interval scheduled clientId:{} delayMillis:{}", uniqueId, delayMillis);
	}

	private void connAckByReturnCode(String clientId, String uniqueId, ChannelContext context, MqttConnectReasonCode returnCode,
									 int serverKeepAlive, boolean assignedClientId, boolean sessionPresent,
									 boolean requestProblemInformation, boolean requestResponseInformation) {
		if (returnCode.isAccepted() && MqttCodecUtil.isMqtt5(context)) {
			// 解码器必须使用与 CONNACK 宣告一致的入站 Topic Alias 上限。
			MqttCodecUtil.setInboundTopicAliasMaximum(context,
				serverCreator.getMqttServerProperties().getTopicAliasMaximum());
		}
		MqttConnAckMessage message = MqttConnAckMessage.builder()
			.returnCode(returnCode)
			// spec 3.2.2.1.1：失败 CONNACK 固定为 false；成功 CONNACK 由调用方按 Clean Start / 既有 session 计算。
			.sessionPresent(returnCode.isAccepted() && sessionPresent)
			.properties(buildConnAckProperties(uniqueId, returnCode, serverKeepAlive, assignedClientId,
				requestProblemInformation, requestResponseInformation).getProperties())
			.build();
		boolean result = Tio.send(context, message);
		if (returnCode.isAccepted()) {
			logger.info("Connect successful, clientId: {} uniqueId:{} result:{}", clientId, uniqueId, result);
		} else {
			logger.error("Connect error - clientId: {} uniqueId:{} returnCode:{} result:{}", clientId, uniqueId, returnCode, result);
		}
	}

	private MqttConnAckProperties buildConnAckProperties(String uniqueId, MqttConnectReasonCode returnCode, int serverKeepAlive,
														 boolean assignedClientId, boolean requestProblemInformation,
														 boolean requestResponseInformation) {
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
		// MQTT 5.0 规范 3.2.2.3.4：Maximum QoS 属性只能为 0 或 1；属性缺省表示服务端支持 QoS 2。
		setMaximumQosProperty(connAckProperties, properties.getMaximumQos());
		// 仅当 serverKeepAlive > 0 时才下发该字段，避免污染 3.x 客户端
		if (serverKeepAlive > 0) {
			connAckProperties.setServerKeepAlive(serverKeepAlive);
		}
		if (assignedClientId && StrUtil.isNotBlank(uniqueId)) {
			connAckProperties.setAssignedClientIdentifier(uniqueId);
		}
		// MQTT 5.0 规范 3.2.2.3.16：仅当客户端在 CONNECT 中通过
		// Request Response Information 显式请求（值 = 1）且服务端配置了非空响应信息时才下发 Response Information。
		if (requestResponseInformation) {
			String responseInformation = properties.getResponseInformation();
			if (StrUtil.isNotBlank(responseInformation)) {
				connAckProperties.setResponseInformation(responseInformation);
			}
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

	/**
	 * 解析客户端在 CONNECT 中是否请求了 Response Information。
	 * MQTT 5.0 规范 3.1.2.3.10：缺省值与 Request Problem Information 相反（缺省 false），
	 * 客户端必须显式置 1 才会被服务端下发 Response Information。
	 *
	 * @param context        ChannelContext
	 * @param variableHeader CONNECT variable header
	 * @return 是否请求 Response Information
	 */
	private boolean isRequestResponseInformation(ChannelContext context, MqttConnectVariableHeader variableHeader) {
		if (!MqttCodecUtil.isMqtt5(context)) {
			return false;
		}
		Boolean requestResponseInformation = new MqttConnectProperties(variableHeader.properties()).getRequestResponseInformation();
		return requestResponseInformation != null && requestResponseInformation;
	}

	private int resolveClientReceiveMaximum(ChannelContext context, MqttConnectVariableHeader variableHeader, String clientId) {
		if (!MqttCodecUtil.isMqtt5(context)) {
			return IMqttSessionManager.MQTT5_DEFAULT_RECEIVE_MAXIMUM;
		}
		Integer receiveMaximum = new MqttConnectProperties(variableHeader.properties()).getReceiveMaximum();
		if (receiveMaximum == null) {
			return IMqttSessionManager.MQTT5_DEFAULT_RECEIVE_MAXIMUM;
		}
		if (receiveMaximum > 0 && receiveMaximum <= IMqttSessionManager.MQTT5_DEFAULT_RECEIVE_MAXIMUM) {
			return receiveMaximum;
		}
		logger.warn("Connect clientId:{} invalid receiveMaximum:{}, fallback to spec default 65535", clientId, receiveMaximum);
		return IMqttSessionManager.MQTT5_DEFAULT_RECEIVE_MAXIMUM;
	}

	private boolean hasInvalidReceiveMaximum(ChannelContext context, MqttConnectVariableHeader variableHeader) {
		if (!MqttCodecUtil.isMqtt5(context)) {
			return false;
		}
		Integer receiveMaximum = new MqttConnectProperties(variableHeader.properties()).getReceiveMaximum();
		return receiveMaximum != null && receiveMaximum < 1;
	}

	/**
	 * 校验客户端在 CONNECT 中声明的 Maximum Packet Size 是否在合法范围。
	 * MQTT 5.0 规范 3.1.2.11.5：取值范围 [1, 4294967295]。
	 */
	private boolean hasInvalidClientMaxPacketSize(ChannelContext context, MqttConnectVariableHeader variableHeader, String clientId) {
		if (!MqttCodecUtil.isMqtt5(context)) {
			return false;
		}
		Integer maxPacketSize = new MqttConnectProperties(variableHeader.properties()).getMaximumPacketSize();
		if (maxPacketSize == null) {
			return false;
		}
		if (maxPacketSize == 0) {
			logger.warn("Connect clientId:{} invalid Maximum Packet Size:0", clientId);
			return true;
		}
		return false;
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

	/**
	 * 会话绑定：解析持久会话策略并记录归属连接。
	 * <p>
	 * spec 3.1.2.11.4 Session Expiry Interval 决定会话在断开后保留多久：
	 * <ul>
	 *     <li>MQTT 5.0：Clean Start 只决定本次是否丢弃旧会话，是否保留由 Session Expiry Interval 决定
	 *         （0 = 连接结束即结束、0xFFFFFFFF = 不限期、未携带取服务端默认值）；</li>
	 *     <li>MQTT 3.1.1：只有 Clean Session = false 才是持久会话，保留时长取
	 *         {@code v311IdleSessionTimeoutSeconds}（0 表示不自动过期）。</li>
	 * </ul>
	 *
	 * @param uniqueId    uniqueId
	 * @param connectionId 连接 id（ChannelContext#getId）
	 * @param cleanStart  CONNECT 中的 Clean Start / Clean Session
	 * @param header      CONNECT 固定头 + 可变头
	 */
	private void bindSession(String uniqueId, String connectionId, boolean cleanStart, MqttConnectVariableHeader header) {
		try {
			// 重连覆盖：取消上一连接遗留的过期任务
			sessionExpireScheduler.cancel(uniqueId);
			MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(header.name(), (byte) header.version());
			boolean mqtt5 = MqttVersion.MQTT_5 == mqttVersion;
			long expirySeconds;
			if (mqtt5) {
				Integer declared = new MqttConnectProperties(header.properties()).getSessionExpiryInterval();
				expirySeconds = declared == null
					? serverCreator.getSessionExpiryIntervalSeconds()
					: Integer.toUnsignedLong(declared);
			} else {
				expirySeconds = serverCreator.getV311IdleSessionTimeoutSeconds();
			}
			// 0xFFFFFFFF 表示不限期，归一化为 0（不调度过期）
			int normalisedExpiry = (expirySeconds <= 0L || expirySeconds >= MqttSessionState.NEVER_EXPIRE_SECONDS)
				? 0
				: (int) expirySeconds;
			// Clean Start 只决定"本次是否丢弃旧会话"，保留与否由过期时长决定
			boolean persistent = serverCreator.isEnablePersistentSession() && !cleanStart && expirySeconds != 0L;
			sessionManager.bindSession(uniqueId, connectionId, cleanStart, persistent, normalisedExpiry);
			if (logger.isDebugEnabled()) {
				logger.debug("Connect session bound - clientId:{} mqtt5:{} cleanStart:{} persistent:{} expirySeconds:{}",
					uniqueId, mqtt5, cleanStart, persistent, normalisedExpiry);
			}
		} catch (Throwable throwable) {
			logger.error("Mqtt server clientId:{} bind session error.", uniqueId, throwable);
		}
	}

	/**
	 * 会话恢复：重发在途消息并回放离线队列，保持消息顺序。
	 * <ol>
	 *     <li>在途消息：QoS2 已收 PUBREC 的重发 PUBREL（spec 4.3.3），其余重发 PUBLISH 并置 DUP=1；</li>
	 *     <li>离线队列：按入队顺序逐条投递，QoS1/2 分配服务端 packetId 并登记在途；
	 *         发送失败则放回队首并中断，保证顺序不被破坏。</li>
	 * </ol>
	 */
	private void resumeSession(ChannelContext context, String uniqueId) {
		// 1. 在途消息重发
		for (MqttPendingPublish pendingPublish : sessionManager.getPendingPublishes(uniqueId)) {
			MqttPublishMessage origin = pendingPublish.getMessage();
			if (origin == null) {
				continue;
			}
			int packetId = origin.variableHeader().packetId();
			if (sessionManager.isPubRecReceived(uniqueId, packetId)) {
				MqttMessage pubRelMessage = new MqttMessage(
					new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.QOS1, false, 0),
					MqttMessageIdVariableHeader.from(packetId)
				);
				pendingPublish.setPubRelMessage(pubRelMessage);
				// 先启动重传定时器再发送：避免 ack 抢跑后定时器作用在已被移除的条目上（产生无谓重传）
				pendingPublish.startPubRelRetransmissionTimer(taskService, context);
				Tio.send(context, pubRelMessage);
				logger.debug("Session resume - PubRel resend clientId:{} packetId:{}", uniqueId, packetId);
				continue;
			}
			MqttPublishMessage dupMessage = MqttPublishMessage.builder()
				.topicName(origin.variableHeader().topicName())
				.payload(origin.payload())
				.qos(origin.fixedHeader().qosLevel())
				.isDup(true)
				.retained(false)
				.messageId(packetId)
				.properties(origin.variableHeader().properties())
				.build();
			// 先启动重传定时器再发送（同上：避免 ack 抢跑导致孤儿定时器反复重传）
			pendingPublish.startPublishRetransmissionTimer(taskService, context);
			boolean result = Tio.send(context, dupMessage);
			logger.debug("Session resume - Publish dup resend clientId:{} packetId:{} result:{}", uniqueId, packetId, result);
		}
		// 2. 离线队列回放
		int replayCount = 0;
		while (true) {
			Message offlineMessage = sessionManager.pollOfflineMessage(uniqueId);
			if (offlineMessage == null) {
				break;
			}
			MqttQoS mqttQoS = MqttQoS.valueOf(offlineMessage.getQos());
			boolean isHighQos = MqttQoS.QOS1 == mqttQoS || MqttQoS.QOS2 == mqttQoS;
			int packetId = isHighQos ? sessionManager.getPacketId(uniqueId) : -1;
			MqttPublishMessage publishMessage = MqttPublishMessage.builder()
				.topicName(offlineMessage.getTopic())
				.payload(offlineMessage.getPayload())
				.qos(mqttQoS)
				.retained(false)
				.messageId(packetId)
				.properties(offlineMessage.getProperties())
				.build();
			// 先登记在途并启动重传，再发送：避免 ack 抢跑导致登记残留 / 定时器成孤儿
			MqttPendingPublish pendingPublish = null;
			if (isHighQos) {
				pendingPublish = new MqttPendingPublish(publishMessage, mqttQoS);
				sessionManager.addPendingPublish(uniqueId, packetId, pendingPublish);
				pendingPublish.startPublishRetransmissionTimer(taskService, context);
			}
			boolean sent = Tio.send(context, publishMessage);
			if (!sent) {
				if (pendingPublish != null) {
					pendingPublish.onPubAckReceived();
					sessionManager.removePendingPublish(uniqueId, packetId);
				}
				sessionManager.pushOfflineMessageFirst(uniqueId, offlineMessage);
				logger.warn("Session resume - offline message send failed, keep in queue clientId:{} topic:{}",
					uniqueId, offlineMessage.getTopic());
				break;
			}
			replayCount++;
		}
		if (replayCount > 0) {
			logger.info("Session resume - offline messages replayed clientId:{} count:{}", uniqueId, replayCount);
		}
	}
}
