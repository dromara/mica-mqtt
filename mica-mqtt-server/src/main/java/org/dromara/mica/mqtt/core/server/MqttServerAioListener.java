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

package org.dromara.mica.mqtt.core.server;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.intf.Packet;
import net.dreamlu.mica.net.server.DefaultTioServerListener;
import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.pipeline.IMqttMessagePipeline;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * mqtt 服务监听
 *
 * @author L.cm
 */
public class MqttServerAioListener extends DefaultTioServerListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttServerAioListener.class);
	private static final int WILL_DELIVERY_ATTEMPTS = 3;
	public static final String WILL_MESSAGE_KEY = MqttServerAioListener.class.getName() + ".will";
	public static final String WILL_CLIENT_ID_KEY = MqttServerAioListener.class.getName() + ".clientId";
	private final ExecutorService executor;
	private final IMqttMessageStore messageStore;
	private final IMqttSessionManager sessionManager;
	private final IMqttMessagePipeline messagePipeline;
	private final IMqttConnectStatusListener connectStatusListener;
	private final MqttMessageInterceptors messageInterceptors;

	public MqttServerAioListener(MqttServerCreator serverCreator) {
		this.executor = serverCreator.getMqttExecutor();
		this.messageStore = serverCreator.getMessageStore();
		this.sessionManager = serverCreator.getSessionManager();
		this.messagePipeline = serverCreator.getMessagePipeline();
		this.connectStatusListener = serverCreator.getConnectStatusListener();
		this.messageInterceptors = serverCreator.getMessageInterceptors();
	}

	@Override
	public boolean onHeartbeatTimeout(ChannelContext context, long interval, int heartbeatTimeoutCount) {
		String clientId = context.getBsId();
		logger.info("Mqtt HeartbeatTimeout clientId:{} interval:{} count:{}", clientId, interval, heartbeatTimeoutCount);
		return false;
	}

	@Override
	public void onAfterConnected(ChannelContext context, boolean isConnected, boolean isReconnect) throws Exception {
		messageInterceptors.onAfterConnected(context, isConnected, isReconnect);
	}

	@Override
	public void onBeforeClose(ChannelContext context, Throwable throwable, String remark, boolean isRemove) {
		// 标记认证为 false
		context.setAccepted(false);
		// 1. 业务 id
		String clientId = context.getBsId();
		if (StrUtil.isBlank(clientId)) {
			clientId = context.get(WILL_CLIENT_ID_KEY);
		}
		// 2. 判断是否正常断开
		boolean isNotNormalDisconnect = !context.isBizStatus();
		ChannelContext.CloseCode closeCode = context.getCloseCode();
		// MQTT-3.1.4-3：同一 clientId 被新连接接管时，旧连接没有发送 DISCONNECT，
		// 其遗嘱仍应按异常断开发布。条件清除保证不会误删新连接刚写入的遗嘱。
		boolean shouldSendWill = shouldPublishWillOnClose(context.isBizStatus(), closeCode);
		context.setBizStatus(false);
		if (isNotNormalDisconnect || throwable != null) {
			// 避免网络异常时短期照成大量异常打印，会导致内存突增
			if (throwable instanceof IOException) {
				logger.error("Mqtt server close clientId:{}, remark:{} isRemove:{} error:{}", clientId, remark, isRemove, throwable.getMessage());
			} else {
				logger.error("Mqtt server close clientId:{}, remark:{} isRemove:{}", clientId, remark, isRemove, throwable);
			}
		} else {
			logger.info("Mqtt server close clientId:{} remark:{} isRemove:{}", clientId, remark, isRemove);
		}
		// 3. 业务 id 不能为空
		if (StrUtil.isBlank(clientId)) {
			return;
		}
		// 4. 对于异常断开或被接管连接，处理遗嘱消息
		if (shouldSendWill) {
			Message willMessage = context.get(WILL_MESSAGE_KEY);
			context.remove(WILL_MESSAGE_KEY);
			sendWillMessage(clientId, willMessage);
		}
		// 5. 会话清理，互踢已清，不用再清（避免误删刚订阅的 topic）
		if (closeCode != ChannelContext.CloseCode.KICK_EACH_OTHER) {
			cleanSession(clientId);
		}
		// 6. 下线事件
		notify(context, clientId, remark);
	}

	private void sendWillMessage(String clientId, Message willMessage) {
		if (willMessage != null) {
			dispatchWillMessage(clientId, willMessage, 1);
		}
	}

	/**
	 * 判断连接关闭时是否应发布遗嘱。
	 * <p>
	 * 异常断开（未收到 DISCONNECT）以及同 clientId 接管（KICK_EACH_OTHER）
	 * 都要发布；正常 DISCONNECT 不发布。
	 * </p>
	 *
	 * @param bizStatus 连接关闭前是否处于正常业务状态
	 * @param closeCode 关闭码
	 * @return 是否发布遗嘱
	 */
	static boolean shouldPublishWillOnClose(boolean bizStatus, ChannelContext.CloseCode closeCode) {
		return !bizStatus || ChannelContext.CloseCode.KICK_EACH_OTHER == closeCode;
	}

	/**
	 * 异步投递遗嘱，成功后仅清除原连接的遗嘱。
	 *
	 * @param clientId 客户端标识
	 * @param willMessage 遗嘱消息
	 * @param attempt 当前投递次数
	 */
	private void dispatchWillMessage(String clientId, Message willMessage, int attempt) {
		try {
			executor.execute(() -> {
				boolean delivered = false;
				try {
					delivered = messagePipeline.handle(willMessage);
					if (delivered) {
						messageStore.clearWillMessage(clientId, willMessage);
					}
				} catch (Throwable e) {
					logger.error("Mqtt server clientId:{} send willMessage async error.", clientId, e);
				}
				if (!delivered && attempt < WILL_DELIVERY_ATTEMPTS) {
					dispatchWillMessage(clientId, willMessage, attempt + 1);
				} else if (!delivered) {
					logger.error("Mqtt server clientId:{} willMessage delivery failed after {} attempts.", clientId, attempt);
				}
			});
		} catch (RuntimeException e) {
			logger.error("Mqtt server clientId:{} cannot schedule willMessage delivery.", clientId, e);
		}
	}

	private void cleanSession(String clientId) {
		try {
			sessionManager.remove(clientId);
		} catch (Throwable throwable) {
			logger.error("Mqtt server clientId:{} session clean error.", clientId, throwable);
		}
	}

	private void notify(ChannelContext context, String clientId, String remark) {
		String username = context.getUserId();
		try {
			connectStatusListener.offline(context, clientId, username, remark);
		} catch (Throwable throwable) {
			logger.error("Mqtt server clientId:{} offline notify error.", clientId, throwable);
		}
	}

	@Override
	public void onAfterSent(ChannelContext context, Packet packet, boolean isSentSuccess) throws Exception {
		if (packet instanceof MqttMessage) {
			messageInterceptors.onAfterSent(context, (MqttMessage) packet, isSentSuccess);
		}
	}

	@Override
	public void onAfterReceivedBytes(ChannelContext context, int receivedBytes) throws Exception {
		messageInterceptors.onAfterReceivedBytes(context, receivedBytes);
	}

	@Override
	public void onAfterDecoded(ChannelContext context, Packet packet, int packetSize) throws Exception {
		if (packet instanceof MqttMessage) {
			messageInterceptors.onAfterDecoded(context, (MqttMessage) packet, packetSize);
		}
	}

	@Override
	public void onAfterHandled(ChannelContext context, Packet packet, long cost) throws Exception {
		if (packet instanceof MqttMessage) {
			messageInterceptors.onAfterHandled(context, (MqttMessage) packet, cost);
		}
	}

}
