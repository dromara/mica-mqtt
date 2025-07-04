/* Copyright (c) 2022 Peigen.info. All rights reserved. */

package org.dromara.mica.mqtt.server.solon.config;

import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.server.solon.event.SolonEventMqttConnectStatusListener;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;
import org.tio.core.Node;

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
			.heartbeatTimeout(properties.getHeartbeatTimeout())
			.keepaliveBackoff(properties.getKeepaliveBackoff())
			.readBufferSize((int) DataSize.parse(properties.getReadBufferSize()).getBytes())
			.maxBytesInMessage((int) DataSize.parse(properties.getMaxBytesInMessage()).getBytes())
			.maxClientIdLength(properties.getMaxClientIdLength())
			.nodeName(properties.getNodeName())
			.statEnable(properties.isStatEnable())
			.proxyProtocolEnable(properties.isProxyProtocolOn());
		if (properties.isDebug()) {
			serverCreator.debug();
		}
		// mqtt 协议
		MqttServerProperties.Listener mqttListener = properties.getMqttListener();
		if (mqttListener.isEnable()) {
			serverCreator.enableMqtt(builder -> builder.serverNode(mqttListener.getServerNode()).build());
		}
		// mqtt ssl 协议
		MqttServerProperties.SslListener mqttSslListener = properties.getMqttSslListener();
		if (mqttSslListener.isEnable()) {
			MqttServerProperties.Ssl ssl = mqttSslListener.getSsl();
			serverCreator.enableMqttSsl(sslBuilder -> sslBuilder
				.serverNode(mqttSslListener.getServerNode())
				.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth())
				.build());
		}
		// mqtt websocket 协议
		MqttServerProperties.Listener wsListener = properties.getWsListener();
		if (wsListener.isEnable()) {
			serverCreator.enableMqttWs(builder -> builder.serverNode(wsListener.getServerNode()).build());
		}
		MqttServerProperties.SslListener wssListener = properties.getWssListener();
		if (mqttSslListener.isEnable()) {
			MqttServerProperties.Ssl ssl = wssListener.getSsl();
			serverCreator.enableMqttWss(sslBuilder -> sslBuilder
				.serverNode(wssListener.getServerNode())
				.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth())
				.build());
		}
		// mqtt http api
		MqttServerProperties.HttpListener httpListener = properties.getHttpListener();
		if (httpListener.isEnable()) {
			Node serverNode = httpListener.getServerNode();
			MqttServerProperties.HttpBasicAuth basicAuth = httpListener.getBasicAuth();
			MqttServerProperties.McpServer mcpServer = httpListener.getMcpServer();
			MqttServerProperties.HttpSsl ssl = httpListener.getSsl();
			serverCreator.enableMqttHttpApi(builder -> {
				builder.serverNode(serverNode);
				if (basicAuth.isEnable()) {
					builder.basicAuth(basicAuth.getUsername(), basicAuth.getPassword());
				}
				if (mcpServer.isEnable()) {
					builder.mcpServer(mcpServer.getSseEndpoint(), mcpServer.getMessageEndpoint());
				}
				if (ssl.isEnable()) {
					builder.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth());
				}
				return builder.build();
			});
		}
		return serverCreator;
	}

}
