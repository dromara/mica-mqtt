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

import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttConnectStatusListener;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttMessageStore;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.pipeline.ClusterMessageDispatcher;
import org.dromara.mica.mqtt.broker.cluster.pipeline.ClusterPublishHandler;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;

/**
 * Builder for creating MQTT broker instances with cluster mode support.
 * <p>
 * This class provides a fluent API to configure and create an {@link MqttServer}
 * that operates in cluster mode. When cluster mode is disabled via
 * {@link MqttClusterConfig#enabled(boolean)}, it delegates to the standard
 * {@link MqttServerCreator#build()}.
 * </p>
 * <p>
 * In cluster mode, this builder:
 * <ol>
 *   <li>Creates a {@link MqttClusterManager} to manage inter-node communication</li>
 *   <li>Wraps the session manager with {@link ClusterMqttSessionManager} for cross-node session tracking</li>
 *   <li>Wraps the message store with {@link ClusterMqttMessageStore} for will/retain message sync</li>
 *   <li>Decorates the connect status listener with {@link ClusterMqttConnectStatusListener}</li>
 *   <li>Adds pipeline handlers for message forwarding and dispatching</li>
 * </ol>
 * </p>
 *
 * @author L.cm
 * @see MqttServerCreator
 * @see MqttClusterConfig
 * @since 1.0.0
 */
public class MqttClusterBrokerCreator {
	private final MqttServerCreator serverCreator;
	private MqttClusterConfig clusterConfig;
	private MqttClusterManager clusterManager;
	private ClusterMqttSessionManager clusterSessionManager;

	/**
	 * Constructs a new cluster broker creator wrapping the specified server creator.
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
	 * Builds the MQTT server, applying cluster decorations if cluster mode is enabled.
	 *
	 * @return the configured {@link MqttServer} instance
	 */
	public MqttServer build() {
		if (clusterConfig == null || !clusterConfig.isEnabled()) {
			return serverCreator.build();
		}

		clusterManager = new MqttClusterManager(clusterConfig, serverCreator.getNodeName());

		IMqttSessionManager delegateSessionManager = serverCreator.getSessionManager();
		if (delegateSessionManager == null) {
			delegateSessionManager = new InMemoryMqttSessionManager();
		}

		clusterSessionManager = new ClusterMqttSessionManager(delegateSessionManager, clusterManager);
		serverCreator.sessionManager(clusterSessionManager);

		if (serverCreator.getMessageStore() != null) {
			ClusterMqttMessageStore clusterMessageStore = new ClusterMqttMessageStore(
				serverCreator.getMessageStore(), clusterManager
			);
			serverCreator.messageStore(clusterMessageStore);
		}

		ClusterMqttConnectStatusListener clusterConnectStatusListener = new ClusterMqttConnectStatusListener(
			serverCreator.getConnectStatusListener(), clusterManager
		);
		serverCreator.connectStatusListener(clusterConnectStatusListener);

		ClusterPublishHandler publishHandler = new ClusterPublishHandler(clusterManager, clusterSessionManager);
		serverCreator.addPublishPipelineHandler(publishHandler);

		MqttServer mqttServer = serverCreator.build();
		clusterManager.setMqttServer(mqttServer);

		ClusterMessageDispatcher dispatcher = new ClusterMessageDispatcher(mqttServer, clusterManager, clusterSessionManager);
		serverCreator.addMessagePipelineHandler(dispatcher);

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
}
