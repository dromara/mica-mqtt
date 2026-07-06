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
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttConnectMessage;
import org.dromara.mica.mqtt.codec.message.MqttConnAckMessage;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttConnectVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttConnectPayload;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
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
		// 1. 获取唯一 id
		String uniqueId = uniqueIdService.getUniqueId(context, clientId, userName, password);
		// 2. uniqueId 不能为空
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
		MqttConnectVariableHeader variableHeader = mqttMessage.variableHeader();
		int keepAliveSeconds = variableHeader.keepAliveTimeSeconds();
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
			willMessage.setPeerHost(clientNode.getPeerHost());
			willMessage.setNode(serverCreator.getNodeName());
			messageStore.addWillMessage(uniqueId, willMessage);
		}
		// 9. 返回 ack
		connAckByReturnCode(clientId, uniqueId, context, MqttConnectReasonCode.CONNECTION_ACCEPTED);
		// 10. 在线通知
		executor.execute(() -> {
			try {
				connectStatusListener.online(context, uniqueId, userName);
			} catch (Throwable e) {
				logger.error("Mqtt server uniqueId:{} clientId:{} online notify error.", uniqueId, clientId, e);
			}
		});
	}

	private static void connAckByReturnCode(String clientId, String uniqueId, ChannelContext context, MqttConnectReasonCode returnCode) {
		MqttConnAckMessage message = MqttConnAckMessage.builder()
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
