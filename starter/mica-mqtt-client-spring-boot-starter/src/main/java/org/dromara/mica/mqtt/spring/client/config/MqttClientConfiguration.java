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

package org.dromara.mica.mqtt.spring.client.config;

import org.dromara.mica.mqtt.codec.MqttTopicSubscription;
import org.dromara.mica.mqtt.core.client.IMqttClientConnectListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.deserialize.MqttJsonDeserializer;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribeDetector;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribeLazyFilter;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.dromara.mica.mqtt.spring.client.event.SpringEventMqttClientConnectListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.tio.core.ssl.SSLEngineCustomizer;
import org.tio.core.ssl.SslConfig;

import java.util.List;

/**
 * mqtt client 配置
 *
 * @author L.cm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	prefix = MqttClientProperties.PREFIX,
	name = "enabled",
	havingValue = "true",
	matchIfMissing = true
)
@EnableConfigurationProperties(MqttClientProperties.class)
public class MqttClientConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MqttDeserializer mqttDeserializer() {
		return new MqttJsonDeserializer();
	}

	@Bean
	@ConditionalOnMissingBean
	public IMqttClientConnectListener springEventMqttClientConnectListener(ApplicationEventPublisher eventPublisher) {
		return new SpringEventMqttClientConnectListener(eventPublisher);
	}

	@Bean
	public MqttClientCreator mqttClientCreator(MqttClientProperties properties, ObjectProvider<SSLEngineCustomizer> sslCustomizers) {
		MqttClientCreator clientCreator = MqttClient.create()
			.name(properties.getName())
			.ip(properties.getIp())
			.port(properties.getPort())
			.username(properties.getUsername())
			.password(properties.getPassword())
			.clientId(properties.getClientId())
			.bindIp(properties.getBindIp())
			.bindNetworkInterface(properties.getBindNetworkInterface())
			.readBufferSize((int) properties.getReadBufferSize().toBytes())
			.maxBytesInMessage((int) properties.getMaxBytesInMessage().toBytes())
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
			SslConfig sslConfig = SslConfig.forClient(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass());
			clientCreator.sslConfig(sslConfig);
			sslCustomizers.ifAvailable(sslConfig::setSslEngineCustomizer);
		}
		// 构造遗嘱消息
		MqttClientProperties.WillMessage willMessage = properties.getWillMessage();
		if (willMessage != null && StringUtils.hasText(willMessage.getTopic())) {
			clientCreator.willMessage(builder -> {
				builder.topic(willMessage.getTopic())
					.qos(willMessage.getQos())
					.retain(willMessage.isRetain());
				if (StringUtils.hasText(willMessage.getMessage())) {
					builder.messageText(willMessage.getMessage());
				}
			});
		}
		// 全局订阅
		List<MqttTopicSubscription> globalSubscribe = properties.getGlobalSubscribe();
		if (globalSubscribe != null && !globalSubscribe.isEmpty()) {
			clientCreator.globalSubscribe(globalSubscribe);
		}
		return clientCreator;
	}

	@Bean(MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN)
	@ConditionalOnMissingBean(name = MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN)
	public MqttClientTemplate mqttClientTemplate(MqttClientCreator mqttClientCreator) {
		return new MqttClientTemplate(mqttClientCreator);
	}

	@Bean
	@ConditionalOnMissingBean
	public static MqttClientSubscribeDetector mqttClientSubscribeDetector(ApplicationContext applicationContext) {
		return new MqttClientSubscribeDetector(applicationContext);
	}

	@Bean
	public MqttClientSubscribeLazyFilter mqttClientSubscribeLazyFilter() {
		return new MqttClientSubscribeLazyFilter();
	}

}
