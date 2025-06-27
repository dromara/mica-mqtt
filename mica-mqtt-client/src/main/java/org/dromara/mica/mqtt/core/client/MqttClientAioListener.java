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
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.client.DefaultTioClientListener;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.utils.hutool.StrUtil;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

/**
 * mqtt 客户端监听器
 *
 * @author L.cm
 */
public class MqttClientAioListener extends DefaultTioClientListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientAioListener.class);
	private final MqttClientCreator clientCreator;
	private final IMqttClientConnectListener connectListener;
	private final ExecutorService executor;

	public MqttClientAioListener(MqttClientCreator clientCreator) {
		this.clientCreator = clientCreator;
		this.connectListener = clientCreator.getConnectListener();
		this.executor = clientCreator.getMqttExecutor();
	}

	@Override
	public void onAfterConnected(ChannelContext context, boolean isConnected, boolean isReconnect) {
		if (isConnected) {
			// 重连时，发送 mqtt 连接消息
			boolean result = Tio.bSend(context, getConnectMessage(this.clientCreator));
			logger.info("MqttClient reconnect send connect result:{}", result);
			if (!result) {
				// 如果重连未成功，直接关闭连接，等待后续重连
				Tio.close(context, "MqttClient reconnect send fail.");
			}
		}
	}

	@Override
	public void onBeforeClose(ChannelContext context, Throwable throwable, String remark, boolean isRemove) {
		context.setAccepted(false);
		// 先判断是否配置监听
		if (connectListener == null) {
			return;
		}
		// 2. 触发客户断开连接事件
		executor.submit(() -> {
			try {
				connectListener.onDisconnect(context, throwable, remark, isRemove);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		});
	}

	/**
	 * 构造连接消息
	 *
	 * @param mqttClientCreator MqttClientCreator
	 * @return MqttConnectMessage
	 */
	private static MqttConnectMessage getConnectMessage(MqttClientCreator mqttClientCreator) {
		MqttWillMessage willMessage = mqttClientCreator.getWillMessage();
		MqttVersion version = mqttClientCreator.getVersion();
		int keepAliveSecs = mqttClientCreator.getKeepAliveSecs();
		// 1. 建立连接后发送 mqtt 连接的消息
		MqttMessageBuilders.ConnectBuilder builder = MqttMessageBuilders.connect()
			.clientId(mqttClientCreator.getClientId())
			.username(mqttClientCreator.getUsername())
			.cleanStart(mqttClientCreator.isCleanStart())
			.protocolVersion(version)
			// 心跳
			.keepAlive(keepAliveSecs > 0 ? keepAliveSecs : MqttClientCreator.DEFAULT_KEEP_ALIVE_SECS)
			.willFlag(willMessage != null);
		// 2. 密码
		String password = mqttClientCreator.getPassword();
		if (StrUtil.isNotBlank(password)) {
			builder.password(password.getBytes(StandardCharsets.UTF_8));
		}
		// 3. 遗嘱消息
		if (willMessage != null) {
			builder.willTopic(willMessage.getTopic())
				.willMessage(willMessage.getMessage())
				.willRetain(willMessage.isRetain())
				.willQoS(willMessage.getQos())
				.willProperties(willMessage.getWillProperties());
		}
		// 4. mqtt5 特性
		if (MqttVersion.MQTT_5 == version) {
			MqttProperties properties = mqttClientCreator.getProperties();
			// Session Expiry Interval
			Integer sessionExpiryInterval = mqttClientCreator.getSessionExpiryIntervalSecs();
			if (sessionExpiryInterval != null && sessionExpiryInterval > 0) {
				if (properties == null) {
					properties = new MqttProperties();
				}
				properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, sessionExpiryInterval));
			}
			if (properties != null) {
				builder.properties(properties);
			}
		}
		return builder.build();
	}

}
