/* Copyright (c) 2022 Peigen.info. All rights reserved. */

package org.dromara.mica.mqtt.server.solon.config;

import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.server.solon.event.SolonEventMqttConnectStatusListener;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;

/**
 * <b>(MqttServerConfiguration)</b>
 *
 * @author LiHai
 * @version 1.0.0
 * @since 2023/7/20
 */
@Configuration
public class MqttServerConfiguration {

	@Bean
	@Condition(onMissingBean = IMqttConnectStatusListener.class)
	public IMqttConnectStatusListener connectStatusListener() {
		return new SolonEventMqttConnectStatusListener();
	}

	@Bean
	public MqttServerCreator mqttServerCreator(MqttServerProperties properties) {
		MqttServerCreator serverCreator = MqttServer.create()
			.name(properties.getName())
			.ip(properties.getIp())
			.port(properties.getPort())
			.heartbeatTimeout(properties.getHeartbeatTimeout())
			.keepaliveBackoff(properties.getKeepaliveBackoff())
			.readBufferSize((int) DataSize.parse(properties.getReadBufferSize()).getBytes())
			.maxBytesInMessage((int) DataSize.parse(properties.getMaxBytesInMessage()).getBytes())
			.maxClientIdLength(properties.getMaxClientIdLength())
			.websocketEnable(properties.isWebsocketEnable())
			.websocketPort(properties.getWebsocketPort())
			.httpEnable(properties.isHttpEnable())
			.httpPort(properties.getHttpPort())
			.nodeName(properties.getNodeName())
			.statEnable(properties.isStatEnable())
			.proxyProtocolEnable(properties.isProxyProtocolOn());
		if (properties.isDebug()) {
			serverCreator.debug();
		}

		// http 认证
		MqttServerProperties.HttpBasicAuth httpBasicAuth = properties.getHttpBasicAuth();
		if (serverCreator.isHttpEnable() && httpBasicAuth.isEnable()) {
			serverCreator.httpBasicAuth(httpBasicAuth.getUsername(), httpBasicAuth.getPassword());
		}
		MqttServerProperties.Ssl ssl = properties.getSsl();
		// ssl 配置
		if (ssl.isEnabled()) {
			serverCreator.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth());
		}
		return serverCreator;
	}

}
