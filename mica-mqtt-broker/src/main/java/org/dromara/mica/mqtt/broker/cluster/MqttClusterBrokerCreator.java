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

package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.pipeline.ClusterMessageDispatcher;
import org.dromara.mica.mqtt.broker.cluster.pipeline.ClusterPublishHandler;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;

/**
 * 集群模式的 Broker 创建器
 */
public class MqttClusterBrokerCreator {
	private final MqttServerCreator serverCreator;
	private MqttClusterConfig clusterConfig;
	private MqttClusterManager clusterManager;
	private ClusterMqttSessionManager clusterSessionManager;

	public MqttClusterBrokerCreator(MqttServerCreator serverCreator) {
		this.serverCreator = serverCreator;
	}

	public MqttClusterBrokerCreator clusterConfig(MqttClusterConfig config) {
		this.clusterConfig = config;
		return this;
	}

	public MqttServer build() {
		if (clusterConfig == null || !clusterConfig.isEnabled()) {
			return serverCreator.build();
		}

		// 1. 初始化 Manager (需稍后传入 mqttServer)
		clusterManager = new MqttClusterManager(clusterConfig, serverCreator.getNodeName());

		// 2. 确保 sessionManager 存在，如果为 null 则创建默认的
		IMqttSessionManager delegateSessionManager = serverCreator.getSessionManager();
		if (delegateSessionManager == null) {
			delegateSessionManager = new InMemoryMqttSessionManager();
		}

		// 3. 包装 sessionManager
		clusterSessionManager = new ClusterMqttSessionManager(delegateSessionManager, clusterManager);
		serverCreator.sessionManager(clusterSessionManager);

		// 4. 构建 MqttServer (需修改 ConnectStatusListener)
		ClusterMqttConnectStatusListener clusterConnectStatusListener = new ClusterMqttConnectStatusListener(
			serverCreator.getConnectStatusListener(), clusterManager
		);
		serverCreator.connectStatusListener(clusterConnectStatusListener);

		// 5. 添加发布消息管线处理器（用于集群消息转发）
		ClusterPublishHandler publishHandler = new ClusterPublishHandler(clusterManager, clusterSessionManager);
		serverCreator.addPublishPipelineHandler(publishHandler);

		// 6. 构建 MqttServer
		MqttServer mqttServer = serverCreator.build();
		clusterManager.setMqttServer(mqttServer);

		// 7. 添加消息拦截器/分发器
		ClusterMessageDispatcher dispatcher = new ClusterMessageDispatcher(mqttServer, clusterManager, clusterSessionManager);
		serverCreator.addMessagePipelineHandler(dispatcher);

		return mqttServer;
	}

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
}
