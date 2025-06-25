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

import org.dromara.mica.mqtt.codec.MqttMessage;
import org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.intf.Packet;
import org.tio.server.DefaultTioServerListener;
import org.tio.utils.hutool.StrUtil;

import java.io.IOException;

/**
 * mqtt 服务监听
 *
 * @author L.cm
 */
public class MqttServerAioListener extends DefaultTioServerListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttServerAioListener.class);
	private final IMqttMessageStore messageStore;
	private final IMqttSessionManager sessionManager;
	private final IMqttMessageDispatcher messageDispatcher;
	private final IMqttConnectStatusListener connectStatusListener;
	private final MqttMessageInterceptors messageInterceptors;

	public MqttServerAioListener(MqttServerCreator serverCreator) {
		this.messageStore = serverCreator.getMessageStore();
		this.sessionManager = serverCreator.getSessionManager();
		this.messageDispatcher = serverCreator.getMessageDispatcher();
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
		// 1. http 请求跳过
		boolean isHttpRequest = context.getAndRemove(MqttConst.IS_HTTP) != null;
		if (isHttpRequest) {
			return;
		}
		// 2. 业务 id
		String clientId = context.getBsId();
		// 3. 判断是否正常断开
		boolean isNotNormalDisconnect = !context.isBizStatus();
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
		// 4. 业务 id 不能为空
		if (StrUtil.isBlank(clientId)) {
			return;
		}
		// 5. 对于异常断开连接，处理遗嘱消息
		if (isNotNormalDisconnect) {
			sendWillMessage(clientId);
		}
		// 6. 会话清理
		cleanSession(clientId);
		// 7. 下线事件
		notify(context, clientId, remark);
	}

	private void sendWillMessage(String clientId) {
		// 发送遗嘱消息
		try {
			Message willMessage = messageStore.getWillMessage(clientId);
			if (willMessage == null) {
				return;
			}
			boolean result = messageDispatcher.send(willMessage);
			logger.debug("Mqtt server clientId:{} send willMessage result:{}.", clientId, result);
			// 4. 清理遗嘱消息
			messageStore.clearWillMessage(clientId);
		} catch (Throwable throwable) {
			logger.error("Mqtt server clientId:{} send willMessage error.", clientId, throwable);
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
