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

package org.dromara.mica.mqtt.client.solon.config;

import org.dromara.mica.mqtt.client.solon.event.SolonEventMqttClientConnectListener;
import org.dromara.mica.mqtt.core.client.IMqttClientConnectListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;
import org.tio.utils.hutool.StrUtil;

import java.nio.charset.StandardCharsets;

/**
 * mqtt client 配置
 *
 * @author L.cm
 */
@Configuration
public class MqttClientConfiguration {

	@Bean
	@Condition(onMissingBean = IMqttClientConnectListener.class)
	public IMqttClientConnectListener solonEventMqttClientConnectListener() {
		return new SolonEventMqttClientConnectListener();
	}

	@Bean
	public MqttClientCreator mqttClientCreator(MqttClientProperties properties) {
		MqttClientCreator clientCreator = MqttClient.create()
			.name(properties.getName())
			.ip(properties.getIp())
			.port(properties.getPort())
			.username(properties.getUsername())
			.password(properties.getPassword())
			.clientId(properties.getClientId())
			.bindIp(properties.getBindIp())
			.bindNetworkInterface(properties.getBindNetworkInterface())
			.readBufferSize((int) DataSize.parse(properties.getReadBufferSize()).getBytes())
			.maxBytesInMessage((int) DataSize.parse(properties.getMaxBytesInMessage()).getBytes())
			.maxClientIdLength(properties.getMaxClientIdLength())
			.keepAliveSecs(properties.getKeepAliveSecs())
			.heartbeatMode(properties.getHeartbeatMode())
			.heartbeatTimeoutStrategy(properties.getHeartbeatTimeoutStrategy())
			.reconnect(properties.isReconnect())
			.reInterval(properties.getReInterval())
			.retryCount(properties.getRetryCount())
			.reSubscribeBatchSize(properties.getReSubscribeBatchSize())
			.version(properties.getVersion())
			.cleanStart(properties.isCleanStart())
			.sessionExpiryIntervalSecs(properties.getSessionExpiryIntervalSecs())
			.statEnable(properties.isStatEnable())
			.debug(properties.isDebug());
		Integer timeout = properties.getTimeout();
		if (timeout != null && timeout > 0) {
			clientCreator.timeout(timeout);
		}
		// mqtt 业务线程数
		Integer bizThreadPoolSize = properties.getBizThreadPoolSize();
		if (bizThreadPoolSize != null && bizThreadPoolSize > 0) {
			clientCreator.bizThreadPoolSize(bizThreadPoolSize);
		}
		// 开启 ssl
		MqttClientProperties.Ssl ssl = properties.getSsl();
		if (ssl.isEnabled()) {
			clientCreator.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass());
		}
		// 构造遗嘱消息
		MqttClientProperties.WillMessage willMessage = properties.getWillMessage();
		if (willMessage != null && StrUtil.isNotBlank(willMessage.getTopic())) {
			clientCreator.willMessage(builder -> {
				builder.topic(willMessage.getTopic())
						.qos(willMessage.getQos())
						.retain(willMessage.isRetain());
				if (StrUtil.isNotBlank(willMessage.getMessage())) {
					builder.message(willMessage.getMessage().getBytes(StandardCharsets.UTF_8));
				}
			});
		}
		return clientCreator;
	}

}
