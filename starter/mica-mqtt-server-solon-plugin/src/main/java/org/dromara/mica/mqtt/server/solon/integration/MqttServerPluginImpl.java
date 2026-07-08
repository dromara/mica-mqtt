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

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.http.common.router.HttpFilter;
import net.dreamlu.mica.net.http.mcp.server.McpServer;
import net.dreamlu.mica.net.utils.hutool.ClassUtil;
import org.dromara.mica.mqtt.core.annotation.MqttServerFunction;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
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
import org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.dromara.mica.mqtt.core.server.interceptor.IMqttMessageInterceptor;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.dromara.mica.mqtt.server.solon.MqttServerTemplate;
import org.dromara.mica.mqtt.server.solon.config.MqttServerConfiguration;
import org.dromara.mica.mqtt.server.solon.config.MqttServerMetricsConfiguration;
import org.dromara.mica.mqtt.server.solon.config.MqttServerProperties;
import org.dromara.mica.mqtt.server.solon.config.MqttServerPropertiesCustomizer;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.BeanWrap;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.bean.LifecycleBean;

import java.lang.reflect.Method;
import java.util.*;

/**
 * <b>(MqttServerPluginImpl)</b>
 *
 * @author LiHai
 * @version 1.0.0
 * @since 2023/7/20
 */
@Slf4j
public class MqttServerPluginImpl implements Plugin {
	private final List<ExtractorClassTag<MqttServerFunction>> functionClassTags = new ArrayList<>();
	private final List<ExtractorMethodTag<MqttServerFunction>> functionMethodTags = new ArrayList<>();
	private volatile boolean running = false;
	private AppContext context;

	@Override
	public void start(AppContext context) throws Throwable {
		this.context = context; //todo: 去掉 Solon.context() 写法，可同时兼容 2.5 之前与之后的版本
		// 查找类上的 MqttServerFunction 注解
		context.beanBuilderAdd(MqttServerFunction.class, (clz, beanWrap, anno) -> {
			functionClassTags.add(new ExtractorClassTag<>(clz, beanWrap, anno));
		});
		// 查找方法上的 MqttServerFunction 注解
		context.beanExtractorAdd(MqttServerFunction.class, (bw, method, anno) -> {
			functionMethodTags.add(new ExtractorMethodTag<>(bw, method, anno));
		});
		context.lifecycle(-9, new LifecycleBean() {
			@Override
			public void start() throws Throwable {
				context.beanMake(MqttServerProperties.class);
				context.beanMake(MqttServerConfiguration.class);
				// Metrics bean
				context.beanMake(MqttServerMetricsConfiguration.class);
				MqttServerProperties properties = context.getBean(MqttServerProperties.class);

				// 初始化自定义配置处理器
				List<MqttServerPropertiesCustomizer> propertiesCustomizers = context.getBeansOfType(MqttServerPropertiesCustomizer.class);
				propertiesCustomizers.forEach((customizer) -> customizer.customize(properties));

				// 初始化 serverCreator
				MqttServerCreator serverCreator = getMqttServerCreator(context, properties);
				BeanWrap clientCreatorWrap = context.wrap(MqttServerCreator.class, serverCreator);
				context.putWrap(MqttServerCreator.class, clientCreatorWrap);

				// 扩展
				IMqttServerAuthHandler authHandler = context.getBean(IMqttServerAuthHandler.class);
				IMqttServerUniqueIdService uniqueIdService = context.getBean(IMqttServerUniqueIdService.class);
				IMqttServerSubscribeValidator subscribeValidator = context.getBean(IMqttServerSubscribeValidator.class);
				IMqttServerPublishPermission publishPermission = context.getBean(IMqttServerPublishPermission.class);

				IMqttMessageStore messageStore = context.getBean(IMqttMessageStore.class);
				IMqttSessionManager sessionManager = context.getBean(IMqttSessionManager.class);
				IMqttSessionListener sessionListener = context.getBean(IMqttSessionListener.class);
				IMqttMessageListener messageListener = context.getBean(IMqttMessageListener.class);
				IMqttConnectStatusListener connectStatusListener = context.getBean(IMqttConnectStatusListener.class);
				IMqttMessageInterceptor messageInterceptor = context.getBean(IMqttMessageInterceptor.class);
				MqttServerCustomizer customizers = context.getBean(MqttServerCustomizer.class);

				// 自定义消息监听
				serverCreator.messageListener(messageListener);
				// 认证处理器
				MqttServerProperties.MqttAuth mqttAuth = properties.getAuth();
				if (Objects.isNull(authHandler)) {
					authHandler = mqttAuth.isEnable() ? new DefaultMqttServerAuthHandler(mqttAuth.getUsername(), mqttAuth.getPassword()) : null;
				}
				serverCreator.authHandler(authHandler);
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
				// mqttServer bean
				MqttServer mqttServer = serverCreator.build();
				context.wrapAndPut(MqttServer.class, mqttServer);
				// mqttServerTemplate bean
				MqttServerTemplate mqttServerTemplate = new MqttServerTemplate(mqttServer);
				context.wrapAndPut(MqttServerTemplate.class, mqttServerTemplate);
				// 添加启动时的函数处理
				functionDetector();
				// 启动
				if (properties.isEnabled() && !running) {
					running = mqttServerTemplate.getMqttServer().start();
					log.info("mqtt server start...");
				}
			}
		});
	}

	private void functionDetector() {
		// functionManager
		MqttFunctionManager functionManager = context.getBean(MqttFunctionManager.class);
		// 类级别的注解订阅
		functionClassTags.forEach(each -> {
			MqttServerFunction anno = each.getAnno();
			String[] topicFilters = getTopicFilters(anno.value());
			IMqttFunctionMessageListener messageListener = each.getBeanWrap().get();
			functionManager.register(topicFilters, messageListener);
		});
		// 方法级别的注解订阅
		functionMethodTags.forEach(each -> {
			MqttServerFunction anno = each.getAnno();
			// topic 信息
			String[] topicTemplates = anno.value();
			String[] topicFilters = getTopicFilters(topicTemplates);
			// 自定义的反序列化，支持 solon bean 或者 无参构造器初始化
			Class<? extends MqttDeserializer> deserialized = anno.deserialize();
			MqttDeserializer deserializer = getMqttDeserializer(deserialized);
			// 构造监听器
			Object bean = each.getBw().get();
			Method method = each.getMethod();
			// 注册监听器
			MqttServerFunctionListener functionListener = new MqttServerFunctionListener(bean, method, topicTemplates, topicFilters, deserializer);
			functionManager.register(topicFilters, functionListener);
		});
	}

	/**
	 * 获取解码器
	 *
	 * @param deserializerType deserializerType
	 * @return 解码器
	 */
	private MqttDeserializer getMqttDeserializer(Class<?> deserializerType) {
		BeanWrap beanWrap = context.getWrap(deserializerType);
		if (beanWrap == null) {
			return ClassUtil.newInstance(deserializerType);
		}
		return beanWrap.get();
	}

	private String[] getTopicFilters(String[] topicTemplates) {
		// 1. 替换 solon cfg 变量
		// 2. 替换订阅中的其他变量
		return Arrays.stream(topicTemplates)
			.map(this::getByCfgOrDef)
			.map(TopicUtil::getTopicFilter)
			.toArray(String[]::new);
	}

	private String getByCfgOrDef(String value) {
		return Optional.ofNullable(context.cfg().getByTmpl(value)).orElse(value);
	}

	private static MqttServerCreator getMqttServerCreator(AppContext context, MqttServerProperties mqttServerProperties) {
		MqttServerCreator serverCreator = MqttServer.create()
			.name(mqttServerProperties.getName())
			.heartbeatTimeout(mqttServerProperties.getHeartbeatTimeout())
			.keepaliveBackoff(mqttServerProperties.getKeepaliveBackoff())
			.readBufferSize((int) DataSize.parse(mqttServerProperties.getReadBufferSize()).getBytes())
			.maxBytesInMessage((int) DataSize.parse(mqttServerProperties.getMaxBytesInMessage()).getBytes())
			.maxClientIdLength(mqttServerProperties.getMaxClientIdLength())
			.nodeName(mqttServerProperties.getNodeName())
			.statEnable(mqttServerProperties.isStatEnable())
			.proxyProtocolEnable(mqttServerProperties.isProxyProtocolOn())
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
			MqttServerProperties.Ssl ssl = mqttSslListener.getSsl();
			serverCreator.enableMqttSsl(sslBuilder -> sslBuilder
				.serverNode(mqttSslListener.getServerNode())
				.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth())
				.build());
		}
		// mqtt websocket 协议
		MqttServerProperties.Listener wsListener = mqttServerProperties.getWsListener();
		if (wsListener.isEnable()) {
			serverCreator.enableMqttWs(builder -> builder.serverNode(wsListener.getServerNode()).build());
		}
		MqttServerProperties.SslListener wssListener = mqttServerProperties.getWssListener();
		if (wssListener.isEnable()) {
			MqttServerProperties.Ssl ssl = wssListener.getSsl();
			serverCreator.enableMqttWss(sslBuilder -> sslBuilder
				.serverNode(wssListener.getServerNode())
				.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth())
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
				}
				if (ssl.isEnable()) {
					builder.useSsl(ssl.getKeystorePath(), ssl.getKeystorePass(), ssl.getTruststorePath(), ssl.getTruststorePass(), ssl.getClientAuth());
				}
				HttpFilter authFilter = context.getBean(HttpFilter.class);
				if (authFilter != null) {
					builder.authFilter(authFilter);
				}
				return builder.build();
			});
		}
		return serverCreator;
	}

	@Override
	public void stop() {
		if (running) {
			MqttServerTemplate mqttServerTemplate = context.getBean(MqttServerTemplate.class);
			mqttServerTemplate.getMqttServer().stop();
			log.info("mqtt server stop...");
		}
	}

	@Data
	@RequiredArgsConstructor
	private static class ExtractorClassTag<T> {
		private final Class<?> clz;
		private final BeanWrap beanWrap;
		private final T anno;
	}

	@Data
	@RequiredArgsConstructor
	private static class ExtractorMethodTag<T> {
		private final BeanWrap bw;
		private final Method method;
		private final T anno;
	}

}
