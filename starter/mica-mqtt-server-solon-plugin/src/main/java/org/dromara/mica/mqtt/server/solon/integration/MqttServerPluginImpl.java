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

package org.dromara.mica.mqtt.server.solon.integration;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.MqttServerCustomizer;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerPublishPermission;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerSubscribeValidator;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.event.IMqttSessionListener;
import org.dromara.mica.mqtt.core.server.interceptor.IMqttMessageInterceptor;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttServerAuthHandler;
import org.dromara.mica.mqtt.server.solon.MqttServerTemplate;
import org.dromara.mica.mqtt.server.solon.config.MqttServerConfiguration;
import org.dromara.mica.mqtt.server.solon.config.MqttServerMetricsConfiguration;
import org.dromara.mica.mqtt.server.solon.config.MqttServerProperties;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.tio.core.ssl.SSLEngineCustomizer;
import org.tio.core.ssl.SslConfig;

import java.util.Objects;

/**
 * <b>(MqttServerPluginImpl)</b>
 *
 * @author LiHai
 * @version 1.0.0
 * @since 2023/7/20
 */
@Slf4j
public class MqttServerPluginImpl implements Plugin {
	private volatile boolean running = false;
	private AppContext context;

	@Override
	public void start(AppContext context) throws Throwable {
		this.context = context; //todo: 去掉 Solon.context() 写法，可同时兼容 2.5 之前与之后的版本

		context.lifecycle(-9, () -> {
			context.beanMake(MqttServerProperties.class);
			context.beanMake(MqttServerConfiguration.class);
			MqttServerProperties properties = context.getBean(MqttServerProperties.class);
			MqttServerCreator serverCreator = context.getBean(MqttServerCreator.class);

			IMqttServerAuthHandler authHandlerImpl = context.getBean(IMqttServerAuthHandler.class);
			IMqttServerUniqueIdService uniqueIdService = context.getBean(IMqttServerUniqueIdService.class);
			IMqttServerSubscribeValidator subscribeValidator = context.getBean(IMqttServerSubscribeValidator.class);
			IMqttServerPublishPermission publishPermission = context.getBean(IMqttServerPublishPermission.class);
			IMqttMessageDispatcher messageDispatcher = context.getBean(IMqttMessageDispatcher.class);
			IMqttMessageStore messageStore = context.getBean(IMqttMessageStore.class);
			IMqttSessionManager sessionManager = context.getBean(IMqttSessionManager.class);
			IMqttSessionListener sessionListener = context.getBean(IMqttSessionListener.class);
			IMqttMessageListener messageListener = context.getBean(IMqttMessageListener.class);
			IMqttConnectStatusListener connectStatusListener = context.getBean(IMqttConnectStatusListener.class);
			IMqttMessageInterceptor messageInterceptor = context.getBean(IMqttMessageInterceptor.class);
			MqttServerCustomizer customizers = context.getBean(MqttServerCustomizer.class);
			// ssl 自定义配置
			SslConfig sslConfig = serverCreator.getSslConfig();
			if (sslConfig != null) {
				SSLEngineCustomizer sslCustomizer = context.getBean(SSLEngineCustomizer.class);
				if (sslCustomizer != null) {
					sslConfig.setSslEngineCustomizer(sslCustomizer);
				}
			}
			// 自定义消息监听
			serverCreator.messageListener(messageListener);
			// 认证处理器
			MqttServerProperties.MqttAuth mqttAuth = properties.getAuth();
			if (Objects.isNull(authHandlerImpl)) {
				IMqttServerAuthHandler authHandler = mqttAuth.isEnable() ? new DefaultMqttServerAuthHandler(mqttAuth.getUsername(), mqttAuth.getPassword()) : null;
				serverCreator.authHandler(authHandler);
			} else {
				serverCreator.authHandler(authHandlerImpl);
			}
			// mqtt 内唯一id
			if (Objects.nonNull(uniqueIdService)) {
				serverCreator.uniqueIdService(uniqueIdService);
			}
			// 订阅校验
			if (Objects.nonNull(subscribeValidator)) {
				serverCreator.subscribeValidator(subscribeValidator);
			}
			// 订阅权限校验
			if (Objects.nonNull(publishPermission)) {
				serverCreator.publishPermission(publishPermission);
			}
			// 消息转发
			if (Objects.nonNull(messageDispatcher)) {
				serverCreator.messageDispatcher(messageDispatcher);
			}
			// 消息存储
			if (Objects.nonNull(messageStore)) {
				serverCreator.messageStore(messageStore);
			}
			// session 管理
			if (Objects.nonNull(sessionManager)) {
				serverCreator.sessionManager(sessionManager);
			}
			// session 监听
			if (Objects.nonNull(sessionListener)) {
				serverCreator.sessionListener(sessionListener);
			}
			// 状态监听
			if (Objects.nonNull(connectStatusListener)) {
				serverCreator.connectStatusListener(connectStatusListener);
			}
			// 消息监听器
			if (Objects.nonNull(messageInterceptor)) {
				serverCreator.addInterceptor(messageInterceptor);
			}
			// 自定义处理
			if (Objects.nonNull(customizers)) {
				customizers.customize(serverCreator);
			}

			MqttServer mqttServer = serverCreator.build();
			MqttServerTemplate mqttServerTemplate = new MqttServerTemplate(mqttServer);
			context.wrapAndPut(MqttServerTemplate.class, mqttServerTemplate);
			// Metrics
			context.beanMake(MqttServerMetricsConfiguration.class);
			// 启动
			if (properties.isEnabled() && !running) {
				running = mqttServerTemplate.getMqttServer().start();
				log.info("mqtt server start...");
			}
		});
	}

	@Override
	public void stop() {
		if (running) {
			MqttServerTemplate mqttServerTemplate = context.getBean(MqttServerTemplate.class);
			mqttServerTemplate.getMqttServer().stop();
			log.info("mqtt server stop...");
		}
	}
}
