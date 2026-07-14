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
import org.dromara.mica.mqtt.codec.codes.MqttDisconnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttReasonCodeAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.will.WillDelayScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * DISCONNECT 消息处理器。
 *
 * @author L.cm
 */
public class MqttDisConnectHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttDisConnectHandler.class);
	private final WillDelayScheduler willDelayScheduler;
	private final org.dromara.mica.mqtt.core.server.session.SessionExpireScheduler sessionExpireScheduler;

	public MqttDisConnectHandler(MqttServerCreator serverCreator,
							 ExecutorService executor,
							 TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.willDelayScheduler = serverCreator.getWillDelayScheduler();
		this.sessionExpireScheduler = serverCreator.getSessionExpireScheduler();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.DISCONNECT};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage message) {
		String clientId = context.getBsId();
		byte reasonCode = getReasonCode(message);
		if (reasonCode == MqttDisconnectReasonCode.NORMAL.value()) {
			logger.info("DisConnect - clientId:{} contextId:{} reasonCode:0x{}", clientId, context.getId(), Integer.toHexString(reasonCode & 0xFF));
		} else {
			logger.warn("DisConnect - clientId:{} contextId:{} reasonCode:0x{}", clientId, context.getId(), Integer.toHexString(reasonCode & 0xFF));
		}
		// MQTT 5.0 Will Delay Interval 取消（spec 3.1.3.5.3）：
		// 正常断开（DISCONNECT reason code = 0）时，will 消息不应发送，需取消任何已调度的延迟任务。
		if (reasonCode == MqttDisconnectReasonCode.NORMAL.value()) {
			willDelayScheduler.cancel(clientId);
		}
		// PR9（P2.8）：正常断开且未声明 Clean Start、且 Session Expiry Interval > 0 时
		// 调度会话过期任务；否则立即清理（保持 MQTT 3.x 与 MQTT 5 cleanStart=true 的"立即清理"语义）。
		// DISCONNECT 一定走正常路径（由 MqttDisConnectHandler 兜底），业务方主动关闭的断开也会经过这里。
		// 注意：本 handler 仅处理 DISCONNECT 报文级别的断开；底层 channel 异常断开由 MqttServerAioListener.onBeforeClose 处理。
		// 实际场景中两种断开都会调用 sessionManager.remove(...) 或等待 SessionExpireScheduler 触发清理。
		scheduleSessionExpiryOnNormalDisconnect(clientId);
		context.setBizStatus(true);
		Tio.remove(context, "Mqtt DisConnect");
	}

	/**
	 * PR9：正常 DISCONNECT 时根据 session state 决定是立即清理还是调度过期。
	 */
	private void scheduleSessionExpiryOnNormalDisconnect(String clientId) {
		// spec 3.2.2.4 DISCONNECT 中的 Session Expiry Interval（MQTT 5）允许客户端更新会话；
		// PR9 简化：从 sessionManager 读取 CONNECT 时记录的 Session Expiry Interval。
		int expirySeconds = serverCreator.getSessionManager().getSessionExpiryInterval(clientId);
		boolean cleanStart = serverCreator.getSessionManager().isCleanStart(clientId);
		if (cleanStart || expirySeconds <= 0) {
			// cleanStart=true 或 expiry=0：立即清理。
			// 底层 Tio.remove 会触发 MqttServerAioListener.onBeforeClose 的清理路径。
			return;
		}
		// 调度过期任务。sessionExpireScheduler 内部已做旧任务取消与并发安全。
		sessionExpireScheduler.scheduleExpire(clientId, expirySeconds);
		if (logger.isDebugEnabled()) {
			logger.debug("Session Expire scheduled - clientId:{} seconds:{}", clientId, expirySeconds);
		}
	}

	private byte getReasonCode(MqttMessage message) {
		Object variableHeader = message.variableHeader();
		if (variableHeader instanceof MqttReasonCodeAndPropertiesVariableHeader) {
			return ((MqttReasonCodeAndPropertiesVariableHeader) variableHeader).reasonCode();
		}
		// MQTT 3.x DISCONNECT 只有固定头；统一按正常断开处理。
		return MqttDisconnectReasonCode.NORMAL.value();
	}
}
