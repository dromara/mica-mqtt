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
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.codes.MqttAuthReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttAuthBuilder;
import org.dromara.mica.mqtt.codec.message.header.MqttReasonCodeAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.properties.MqttAuthProperties;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerExtendedAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * MQTT 5.0 AUTH 报文处理（spec 3.15 / 4.12 / P2.7）。
 *
 * <p>收到客户端的 AUTH 报文后：
 * <ol>
 *     <li>解析 reason code（SUCCESS / CONTINUE_AUTHENTICATION / RE_AUTHENTICATE）；</li>
 *     <li>委托给 {@link IMqttServerExtendedAuthHandler#onAuth}；</li>
 *     <li>用 AUTH 报文回发响应。</li>
 * </ol>
 *
 * <p>仅当服务端配置了 {@code extendedAuthHandler} 时才会处理；未配置时收到 AUTH 报文记日志后忽略。
 *
 * @author L.cm
 */
public class MqttAuthHandler extends AbstractMqttMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttAuthHandler.class);
	private final IMqttServerExtendedAuthHandler extendedAuthHandler;

	public MqttAuthHandler(MqttServerCreator serverCreator,
						   ExecutorService executor,
						   TimerTaskService taskService) {
		super(serverCreator, executor, taskService);
		this.extendedAuthHandler = serverCreator.getExtendedAuthHandler();
	}

	@Override
	public MqttMessageType[] messageTypes() {
		return new MqttMessageType[]{MqttMessageType.AUTH};
	}

	@Override
	public void handle(ChannelContext context, MqttMessage message) {
		String clientId = context.getBsId();
		if (extendedAuthHandler == null) {
			logger.warn("Auth - clientId:{} but extendedAuthHandler is not configured, ignored.", clientId);
			return;
		}
		MqttReasonCodeAndPropertiesVariableHeader variableHeader = (MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();
		byte rawCode = variableHeader.reasonCode();
		MqttAuthReasonCode reasonCode = MqttAuthReasonCode.valueOf(rawCode);
		MqttAuthProperties inProps = new MqttAuthProperties(variableHeader.properties());
		String method = inProps.getAuthenticationMethod();
		if (method == null || method.isEmpty()) {
			logger.warn("Auth - clientId:{} missing Authentication Method property, ignored.", clientId);
			return;
		}
		IMqttServerExtendedAuthHandler.AuthResult outResult;
		try {
			outResult = extendedAuthHandler.onAuth(context, clientId, reasonCode, inProps);
		} catch (Throwable e) {
			logger.error("Auth - clientId:{} method:{} onAuth error.", clientId, method, e);
			return;
		}
		if (outResult == null) {
			logger.debug("Auth - clientId:{} method:{} handler returned null, no response sent.", clientId, method);
			return;
		}
		// 回发 AUTH 报文：reason code 通过 MqttAuthBuilder 显式设置。
		MqttAuthBuilder builder = new MqttAuthBuilder()
			.reasonCode(outResult.getReasonCode())
			.properties(outResult.getProperties() == null ? null : outResult.getProperties().getProperties());
		MqttMessage authMessage = builder.build();
		boolean result = mqttServer != null && mqttServer.sendAuth(context, authMessage);
		logger.debug("Auth - clientId:{} method:{} inReasonCode:0x{} outReasonCode:0x{} sent:{}",
			clientId, method,
			Integer.toHexString(reasonCode.value() & 0xFF),
			Integer.toHexString(outResult.getReasonCode().value() & 0xFF),
			result);
	}
}
