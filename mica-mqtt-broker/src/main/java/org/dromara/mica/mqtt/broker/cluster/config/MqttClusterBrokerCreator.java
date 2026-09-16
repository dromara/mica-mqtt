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

package org.dromara.mica.mqtt.broker.cluster.config;

import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttConnectStatusListener;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttMessageStore;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterStorage;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.pipeline.ClusterPublishHandler;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.HashClientStrategy;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.LocalFirstStrategy;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.RandomStrategy;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.RoundRobinStrategy;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.SharedSubscriptionStrategy;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.StickyStrategy;
import org.dromara.mica.mqtt.broker.rule.RuleEngine;
import org.dromara.mica.mqtt.broker.rule.RuleManager;
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.codec.PayloadCodecFactory;
import org.dromara.mica.mqtt.broker.rule.ext.template.AlertCenter;
import org.dromara.mica.mqtt.broker.rule.ext.template.TemplateActionFactory;
import org.dromara.mica.mqtt.broker.rule.ext.template.TemplateServices;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcherFactory;
import org.dromara.mica.mqtt.broker.rule.store.InMemoryRuleStore;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionMessageListener;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Builder for creating MQTT broker instances with cluster mode support.
 * <p>
 * This class provides a fluent API to configure and create an {@link MqttServer}
 * that operates in cluster mode. When cluster mode is disabled via
 * {@link MqttClusterConfig#enabled(boolean)}, it delegates to the standard
 * {@link MqttServerCreator#build()}.
 * </p>
 * In cluster mode, this builder:
 * <ol>
 *   <li>Creates a {@link MqttClusterManager} to manage inter-node communication</li>
 *   <li>Wraps the session manager with {@link ClusterMqttSessionManager} for cross-node session tracking</li>
 *   <li>Wraps the message store with {@link ClusterMqttMessageStore} for will/retain message sync</li>
 *   <li>Decorates the connect status listener with {@link ClusterMqttConnectStatusListener}</li>
 *   <li>Adds pipeline handlers for message forwarding and dispatching</li>
 * </ol>
 * 无论是否开启集群模式，都会装配规则引擎。
 *
 * @author L.cm
 * @see MqttServerCreator
 * @see MqttClusterConfig
 * @since 1.0.0
 */
public class MqttClusterBrokerCreator {
	private static final Logger logger = LoggerFactory.getLogger(MqttClusterBrokerCreator.class);

	private final MqttServerCreator serverCreator;
	private MqttClusterConfig clusterConfig;
	private MqttClusterManager clusterManager;
	private ClusterMqttSessionManager clusterSessionManager;
	private ClusterStorage clusterStorage;
	private RuleManager ruleManager;
	private RuleEngine ruleEngine;
	private TemplateServices templateServices;

	/**
	 * SPI 加载的工厂（每次 build 的元素为该实例独有，模板 action 的依赖注入不能跨实例共享）。
	 */
	private final List<ActionFactory> actionFactories = new ArrayList<>();

	/**
	 * 构造。
	 *
	 * @param serverCreator the underlying MQTT server creator
	 */
	public MqttClusterBrokerCreator(MqttServerCreator serverCreator) {
		this.serverCreator = serverCreator;
	}

	/**
	 * Sets the cluster configuration.
	 *
	 * @param config the cluster configuration
	 * @return this builder for method chaining
	 */
	public MqttClusterBrokerCreator clusterConfig(MqttClusterConfig config) {
		this.clusterConfig = config;
		return this;
	}

	/**
	 * 加载规则相关的 SPI 工厂。
	 * <p>
	 * SPI（{@code META-INF/services}）是内置工厂的唯一注册来源：早期实现同时做了一份
	 * 手工注册清单，但那份清单是 SPI 的子集（缺 4 个模板工厂），一旦 SPI 失效不会报错，
	 * 只会静默丢掉部分 action。需要程序化注册时可调用
	 * {@link RuleManager#registerActionFactory(ActionFactory)}。
	 * </p>
	 */
	private void loadRuleSpi(RuleManager rm) {
		for (ActionFactory factory : ServiceLoader.load(ActionFactory.class)) {
			actionFactories.add(factory);
			rm.registerActionFactory(factory);
		}
		if (actionFactories.isEmpty()) {
			logger.error("No ActionFactory found via SPI, action rules will fail at runtime. "
				+ "Check META-INF/services/{}", ActionFactory.class.getName());
		}
		int matcherCount = 0;
		for (RuleMatcherFactory factory : ServiceLoader.load(RuleMatcherFactory.class)) {
			rm.registerMatcherFactory(factory);
			matcherCount++;
		}
		int codecCount = 0;
		for (PayloadCodecFactory factory : ServiceLoader.load(PayloadCodecFactory.class)) {
			rm.registerCodecFactory(factory);
			codecCount++;
		}
		logger.debug("Rule factories loaded: actions={} matchers={} codecs={}",
			actionFactories.size(), matcherCount, codecCount);
	}

	/**
	 * 装配规则引擎：必须在 serverCreator.build() 之前替换 messageListener。
	 */
	private void setupRuleEngine() {
		if (ruleManager != null) {
			return;
		}
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		loadRuleSpi(rm);
		this.ruleManager = rm;

		// 用新的 MqttFunctionManager 接管 messageListener（关键修正：在 build 之前替换）
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		final Object prevListener = serverCreator.getMessageListener();
		if (prevListener instanceof IMqttMessageListener) {
			final IMqttMessageListener delegate = (IMqttMessageListener) prevListener;
			// 老的 listener 透传，注册到 #
			fnMgr.register(new String[]{"#"},
				(ctx, clientId, topic, qos, message) -> {
					try {
						delegate.onMessage(ctx, clientId, topic, qos, message);
					} catch (Exception e) {
						logger.warn("Previous IMqttMessageListener failed for topic: {}", topic, e);
					}
				});
		} else if (prevListener != null) {
			logger.warn("Ignored previous message listener of unsupported type: {}",
				prevListener.getClass().getName());
		}
		serverCreator.messageListener(new MqttFunctionMessageListener(fnMgr));

		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		this.ruleEngine = engine;
	}

	/**
	 * 把服务端运行期对象注入模板 action，并注册关闭钩子。
	 * <p>
	 * 模板 action 依赖 {@code publish} 函数 / store / 告警中心，必须在服务端构建完成后
	 * 才能装配；同时规则引擎与 V3 存储的关闭也挂到服务端生命周期上，保证
	 * {@code MqttServer#stop()} 能释放 MqttAction 持有的 MqttClient 等资源。
	 * </p>
	 */
	private void installRuntimeServices(MqttServer server) {
		TemplateServices services = new TemplateServices(
			(topic, payload, qos, retain) -> server.publishAll(topic, payload, MqttQoS.valueOf(qos), retain),
			null,
			new AlertCenter());
		this.templateServices = services;
		for (ActionFactory factory : actionFactories) {
			if (factory instanceof TemplateActionFactory) {
				((TemplateActionFactory) factory).setTemplateServices(services);
			}
		}
		serverCreator.addShutdownHook(this::shutdown);
	}

	/**
	 * broker 关闭：释放规则引擎、模板 action 依赖与 V3 存储。
	 */
	private void shutdown() {
		if (ruleEngine != null) {
			ruleEngine.stop();
		}
		if (templateServices != null) {
			templateServices.close();
		}
		ClusterStorage storage = this.clusterStorage;
		if (storage != null) {
			storage.stop();
		}
	}

	/**
	 * Builds the MQTT server, applying cluster decorations if cluster mode is enabled.
	 *
	 * @return the configured {@link MqttServer} instance
	 */
	public MqttServer build() {
		// 0. 规则引擎装配必须最先做：替换 serverCreator 的 messageListener（关键时序）
		setupRuleEngine();

		if (clusterConfig == null || !clusterConfig.isEnabled()) {
			MqttServer server = serverCreator.build();
			installRuntimeServices(server);
			ruleEngine.start();
			return server;
		}

		// nodeId 为 host:port 格式，用于节点间点对点通信寻址，如果为空，设置成集群节点
		String nodeId = serverCreator.getNodeName();
		String clusterHost = clusterConfig.getClusterHost();
		int clusterPort = clusterConfig.getClusterPort();
		if (StrUtil.isBlank(nodeId) || !nodeId.contains(":")) {
			nodeId = clusterHost + ':' + clusterPort;
		}
		serverCreator.nodeName(nodeId);
		clusterManager = new MqttClusterManager(clusterConfig, nodeId);

		// Initialize V3 persistence layer if enabled.  Failure to open the H2 file
		// is logged and the broker continues in pure-memory mode (INV-6).
		MqttStorageConfig storageConfig = clusterConfig.getStorageConfig();
		if (storageConfig != null && storageConfig.isEnabled()) {
			clusterStorage = new ClusterStorage(storageConfig);
			clusterStorage.start();
		}
		if (clusterStorage != null) {
			clusterManager.setClusterStorage(clusterStorage);
		}

		IMqttSessionManager delegateSessionManager = serverCreator.getSessionManager();
		if (delegateSessionManager == null) {
			delegateSessionManager = new InMemoryMqttSessionManager();
		}

		clusterSessionManager = new ClusterMqttSessionManager(delegateSessionManager, clusterManager);
		clusterManager.setSessionManager(clusterSessionManager);
		serverCreator.sessionManager(clusterSessionManager);

		// Wire V3 storage into session/message stores when enabled.
		if (clusterStorage != null) {
			clusterSessionManager.setSessionStore(clusterStorage.getSessionStore());
			clusterSessionManager.setSharedSubStore(clusterStorage.getSharedSubStore());
			clusterSessionManager.setInflightStore(
				clusterStorage.getInflightStore(), storageConfig.getInflightTtlMs()
			);
		}

		if (serverCreator.getMessageStore() != null) {
			ClusterMqttMessageStore clusterMessageStore = new ClusterMqttMessageStore(
				serverCreator.getMessageStore(), clusterManager
			);
			if (clusterStorage != null) {
				clusterMessageStore.setRetainIndex(clusterStorage.getRetainIndex());
			}
			serverCreator.messageStore(clusterMessageStore);
		}

		ClusterMqttConnectStatusListener clusterConnectStatusListener = new ClusterMqttConnectStatusListener(
			serverCreator.getConnectStatusListener(), clusterManager
		);
		clusterConnectStatusListener.setSessionManager(clusterSessionManager);
		serverCreator.connectStatusListener(clusterConnectStatusListener);

		MqttServer mqttServer = serverCreator.build();
		clusterManager.setMqttServer(mqttServer);
		clusterConnectStatusListener.setTaskService(serverCreator.getTaskService());

		SharedSubscriptionStrategy strategy = createStrategy(clusterConfig, clusterSessionManager);
		clusterManager.setSharedStrategy(strategy);

		ClusterPublishHandler publishHandler = new ClusterPublishHandler(
			mqttServer, clusterManager, clusterSessionManager, strategy
		);
		serverCreator.addPublishPipelineHandler(publishHandler);

		installRuntimeServices(mqttServer);
		// 启动规则引擎：把已加载规则挂到 functionManager
		ruleEngine.start();

		return mqttServer;
	}

	/**
	 * Builds and starts the MQTT server.
	 *
	 * @return the started {@link MqttServer} instance
	 * @throws RuntimeException if startup fails
	 */
	public MqttServer start() {
		MqttServer mqttServer = this.build();
		try {
			if (clusterManager != null) {
				clusterManager.start();
			} else {
				mqttServer.start();
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to start MqttClusterBroker", e);
		}
		return mqttServer;
	}

	public MqttServerCreator getServerCreator() {
		return serverCreator;
	}

	public MqttClusterManager getClusterManager() {
		return clusterManager;
	}

	public ClusterMqttSessionManager getClusterSessionManager() {
		return clusterSessionManager;
	}

	public ClusterStorage getClusterStorage() {
		return clusterStorage;
	}

	/**
	 * 获取规则管理器（用于运行时增删规则）。
	 *
	 * @return RuleManager
	 */
	public RuleManager getRuleManager() {
		return ruleManager;
	}

	/**
	 * 获取规则引擎。
	 *
	 * @return RuleEngine
	 */
	public RuleEngine getRuleEngine() {
		return ruleEngine;
	}

	/**
	 * Creates the shared subscription strategy based on the cluster configuration.
	 * Falls back to {@link LocalFirstStrategy} for unrecognised names.
	 */
	private SharedSubscriptionStrategy createStrategy(MqttClusterConfig config,
													  ClusterMqttSessionManager sessionManager) {
		String strategyName = config.getSharedSubStrategy();
		if (strategyName == null) {
			strategyName = "local_first";
		}
		switch (strategyName.toLowerCase()) {
			case "random":
				return new RandomStrategy();
			case "round_robin":
				return new RoundRobinStrategy();
			case "hash_client":
				return new HashClientStrategy();
			case "sticky":
				return new StickyStrategy();
			case "local_first":
			default:
				return new LocalFirstStrategy(sessionManager::getClientNode);
		}
	}
}
