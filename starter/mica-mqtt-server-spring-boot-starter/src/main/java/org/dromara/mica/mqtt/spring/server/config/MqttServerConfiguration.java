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

package org.dromara.mica.mqtt.spring.server.config;

import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.core.ssl.SslConfig;
import net.dreamlu.mica.net.http.common.router.HttpFilter;
import net.dreamlu.mica.net.http.mcp.server.McpServer;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.deserialize.MqttJsonDeserializer;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.MqttServerCustomizer;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerPublishPermission;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerSubscribeValidator;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.event.IMqttSessionListener;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionMessageListener;
import org.dromara.mica.mqtt.core.server.interceptor.IMqttMessageInterceptor;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttServerAuthHandler;
import org.dromara.mica.mqtt.spring.server.MqttServerFunctionDetector;
import org.dromara.mica.mqtt.spring.server.MqttServerTemplate;
import org.dromara.mica.mqtt.spring.server.event.SpringEventMqttConnectStatusListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mqtt server 配置
 *
 * @author L.cm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	prefix = MqttServerProperties.PREFIX,
	name = "enabled",
	havingValue = "true",
	matchIfMissing = true
)
@EnableConfigurationProperties(MqttServerProperties.class)
public class MqttServerConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public MqttDeserializer mqttDeserializer() {
		return new MqttJsonDeserializer();
	}

	@Bean
	@ConditionalOnMissingBean
	public IMqttConnectStatusListener springEventMqttConnectStatusListener(ApplicationEventPublisher eventPublisher) {
		return new SpringEventMqttConnectStatusListener(eventPublisher);
	}

	@Bean
	public MqttServerCreator mqttServerCreator(MqttServerProperties mqttServerProperties,
											   ObjectProvider<MqttServerPropertiesCustomizer> propertiesCustomizers,
											   ObjectProvider<IMqttServerAuthHandler> authHandlerObjectProvider,
											   ObjectProvider<IMqttServerUniqueIdService> uniqueIdServiceObjectProvider,
											   ObjectProvider<IMqttServerSubscribeValidator> subscribeValidatorObjectProvider,
											   ObjectProvider<IMqttServerPublishPermission> publishPermissionObjectProvider,
											   ObjectProvider<HttpFilter> httpFilterObjectProvider,
											   ObjectProvider<IMqttMessageStore> messageStoreObjectProvider,
											   ObjectProvider<IMqttSessionManager> sessionManagerObjectProvider,
											   ObjectProvider<IMqttSessionListener> sessionListenerObjectProvider,
											   ObjectProvider<IMqttMessageListener> messageListenerObjectProvider,
											   ObjectProvider<IMqttConnectStatusListener> connectStatusListenerObjectProvider,
											   ObjectProvider<IMqttMessageInterceptor> messageInterceptorObjectProvider,
											   ObjectProvider<MqttServerCustomizer> customizers) {
		propertiesCustomizers.orderedStream().forEach(customizer -> customizer.customize(mqttServerProperties));

		MqttServerCreator serverCreator = MqttServer.create()
			.name(mqttServerProperties.getName())
			.heartbeatTimeout(mqttServerProperties.getHeartbeatTimeout())
			.keepaliveBackoff(mqttServerProperties.getKeepaliveBackoff())
			.readBufferSize((int) mqttServerProperties.getReadBufferSize().toBytes())
			.maxBytesInMessage((int) mqttServerProperties.getMaxBytesInMessage().toBytes())
			.maxClientIdLength(mqttServerProperties.getMaxClientIdLength())
			.nodeName(mqttServerProperties.getNodeName())
			.statEnable(mqttServerProperties.isStatEnable())
			.proxyProtocolEnable(mqttServerProperties.isProxyProtocolOn())
			.shutdownTimeoutSec(mqttServerProperties.getShutdownTimeoutSec())
			.properties(properties -> {
				MqttServerProperties.Properties serverProperties = mqttServerProperties.getProperties();
				properties.receiveMaximum(serverProperties.getReceiveMaximum())
					.maximumQos(serverProperties.getMaximumQos())
					.retainAvailable(serverProperties.isRetainAvailable())
					.maximumPacketSize(serverProperties.getMaximumPacketSize())
					.topicAliasMaximum(serverProperties.getTopicAliasMaximum())
					.wildcardSubscriptionAvailable(serverProperties.isWildcardSubscriptionAvailable())
					.sharedSubscriptionAvailable(serverProperties.isSharedSubscriptionAvailable())
					.subscriptionIdentifierAvailable(serverProperties.isSubscriptionIdentifierAvailable())
					.serverKeepAlive(serverProperties.getServerKeepAlive());
			});
		if (mqttServerProperties.isDebug()) {
			serverCreator.debug();
		}
		// tio 编解码等线程数
		Integer tioExecutorSize = mqttServerProperties.getTioExecutorSize();
		if (tioExecutorSize != null && tioExecutorSize > 0) {
			serverCreator.tioExecutorSize(tioExecutorSize);
		}
		// AIO AsynchronousChannelGroup 的线程池
		Integer groupExecutorSize = mqttServerProperties.getGroupExecutorSize();
		if (groupExecutorSize != null && groupExecutorSize > 0) {
			serverCreator.groupExecutorSize(groupExecutorSize);
		}
		// mqtt 工作线程数
		Integer mqttExecutorSize = mqttServerProperties.getMqttExecutorSize();
		if (mqttExecutorSize != null && mqttExecutorSize > 0) {
			serverCreator.mqttExecutorSize(mqttExecutorSize);
		}
		// mqtt 协议
		MqttServerProperties.Listener mqttListener = mqttServerProperties.getMqttListener();
		if (mqttListener.isEnable()) {
			serverCreator.enableMqtt(builder -> builder.serverNode(mqttListener.getServerNode()).build());
		}
		// mqtt ssl 协议
		MqttServerProperties.SslListener mqttSslListener = mqttServerProperties.getMqttSslListener();
		if (mqttSslListener.isEnable()) {
			serverCreator.enableMqttSsl(sslBuilder -> sslBuilder
				.serverNode(mqttSslListener.getServerNode())
				.sslConfig(createSslConfig(mqttSslListener.getSsl()))
				.build());
		}
		// mqtt websocket 协议
		MqttServerProperties.Listener wsListener = mqttServerProperties.getWsListener();
		if (wsListener.isEnable()) {
			serverCreator.enableMqttWs(builder -> builder.serverNode(wsListener.getServerNode()).build());
		}
		MqttServerProperties.SslListener wssListener = mqttServerProperties.getWssListener();
		if (wssListener.isEnable()) {
			serverCreator.enableMqttWss(sslBuilder -> sslBuilder
				.serverNode(wssListener.getServerNode())
				.sslConfig(createSslConfig(wssListener.getSsl()))
				.build());
		}
		// mqtt http api
		MqttServerProperties.HttpListener httpListener = mqttServerProperties.getHttpListener();
		if (httpListener.isEnable()) {
			Node serverNode = httpListener.getServerNode();
			MqttServerProperties.HttpBasicAuth basicAuth = httpListener.getBasicAuth();
			MqttServerProperties.Mcp mcp = httpListener.getMcp();
			MqttServerProperties.HttpSsl ssl = httpListener.getSsl();
			serverCreator.enableMqttHttpApi(builder -> {
				builder.serverNode(serverNode);
				if (basicAuth.isEnable()) {
					builder.basicAuth(basicAuth.getUsername(), basicAuth.getPassword());
				}
				if (mcp.isEnable()) {
					McpServer mcpServer = new McpServer();
					mcpServer.useStreamableTransport(mcp.getEndpoint());
					mcpServer.useSseTransport(mcp.getSseEndpoint(), mcp.getSseMessageEndpoint());
					builder.mcpServer(mcpServer);
				}
				if (ssl.isEnable()) {
					builder.sslConfig(createSslConfig(ssl));
				}
				// 认证处理
				httpFilterObjectProvider.ifAvailable(builder::authFilter);
				return builder.build();
			});
		}
		// 自定义消息监听
		messageListenerObjectProvider.ifAvailable(serverCreator::messageListener);
		// 认证处理器
		IMqttServerAuthHandler authHandler = authHandlerObjectProvider.getIfAvailable(() -> {
			MqttServerProperties.MqttAuth mqttAuth = mqttServerProperties.getAuth();
			return mqttAuth.isEnable() ? new DefaultMqttServerAuthHandler(mqttAuth.getUsername(), mqttAuth.getPassword()) : null;
		});
		serverCreator.authHandler(authHandler);
		// mqtt 内唯一id
		uniqueIdServiceObjectProvider.ifAvailable(serverCreator::uniqueIdService);
		// 订阅校验
		subscribeValidatorObjectProvider.ifAvailable(serverCreator::subscribeValidator);
		// 发布权限校验
		publishPermissionObjectProvider.ifAvailable(serverCreator::publishPermission);

		// 消息存储
		messageStoreObjectProvider.ifAvailable(serverCreator::messageStore);
		// session 管理
		sessionManagerObjectProvider.ifAvailable(serverCreator::sessionManager);
		// session 监听
		sessionListenerObjectProvider.ifAvailable(serverCreator::sessionListener);
		// 状态监听
		connectStatusListenerObjectProvider.ifAvailable(serverCreator::connectStatusListener);
		// 消息监听器
		messageInterceptorObjectProvider.orderedStream().forEach(serverCreator::addInterceptor);
		// 自定义处理
		customizers.ifAvailable((customizer) -> customizer.customize(serverCreator));
		return serverCreator;
	}

	private static SslConfig createSslConfig(MqttServerProperties.Ssl ssl) {
		// 配置 ssl 证书
		SslConfig sslConfig = SslConfig.forServer(
			ssl.getKeystorePath(), ssl.getKeystorePass(),
			ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth()
		);
		// 配置 ssl 参数
		sslConfig.setProtocols(ssl.getProtocols());
		sslConfig.setCipherSuites(ssl.getCipherSuites());
		sslConfig.setUseCipherSuitesOrder(ssl.getUseCipherSuitesOrder());
		return sslConfig;
	}

	@Bean
	public MqttServer mqttServer(MqttServerCreator mqttServerCreator) {
		return mqttServerCreator.build();
	}

	@Bean
	public MqttServerLauncher mqttServerLauncher(MqttServer mqttServer) {
		return new MqttServerLauncher(mqttServer);
	}

	@Bean
	public MqttServerTemplate mqttServerTemplate(MqttServer mqttServer) {
		return new MqttServerTemplate(mqttServer);
	}

	@Bean
	@ConditionalOnMissingBean(MqttFunctionManager.class)
	public static MqttFunctionManager mqttFunctionManager() {
		return new MqttFunctionManager();
	}

	@Bean
	@ConditionalOnMissingBean(IMqttMessageListener.class)
	public IMqttMessageListener mqttFunctionMessageListener(MqttFunctionManager mqttFunctionManager) {
		return new MqttFunctionMessageListener(mqttFunctionManager);
	}

	@Bean
	public static MqttServerFunctionDetector mqttServerFunctionDetector(ApplicationContext applicationContext) {
		return new MqttServerFunctionDetector(applicationContext);
	}

}
