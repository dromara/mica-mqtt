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

package org.dromara.mica.mqtt.broker.cluster.core;

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.message.*;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.Node;
import org.tio.server.cluster.core.ClusterApi;
import org.tio.server.cluster.core.ClusterConfig;
import org.tio.server.cluster.core.ClusterImpl;
import org.tio.server.cluster.message.ClusterDataMessage;

import java.util.*;

/**
 * Manager for MQTT broker cluster operations and inter-node communication.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Starting and stopping the t-io cluster foundation</li>
 *   <li>Broadcasting and point-to-point cluster messages</li>
 *   <li>Handling incoming cluster messages and dispatching to appropriate handlers</li>
 *   <li>Coordinating session synchronization across cluster nodes</li>
 * </ul>
 * </p>
 *
 * @author L.cm
 * @since 1.0.0
 */
public class MqttClusterManager {
	private static final Logger logger = LoggerFactory.getLogger(MqttClusterManager.class);

	private ClusterApi cluster;
	private MqttServer mqttServer;
	private final MqttClusterConfig config;
	private final String localNodeId;

	/**
	 * Constructs a new cluster manager with the specified configuration.
	 *
	 * @param config the cluster configuration
	 * @param localNodeId the unique identifier for this local node in the cluster
	 */
	public MqttClusterManager(MqttClusterConfig config, String localNodeId) {
		this.config = config;
		this.localNodeId = localNodeId;
	}

	/**
	 * Sets the MQTT server instance to be managed by this cluster manager.
	 *
	 * @param mqttServer the MQTT server instance
	 */
	public void setMqttServer(MqttServer mqttServer) {
		this.mqttServer = mqttServer;
	}

	/**
	 * Checks whether cluster mode is enabled in the configuration.
	 *
	 * @return true if clustering is enabled, false otherwise
	 */
	public boolean isClusterEnabled() {
		return config.isEnabled();
	}

	/**
	 * Starts the cluster manager and optionally the embedded MQTT server.
	 * <p>
	 * If cluster mode is disabled, only the MQTT server is started.
	 * If cluster mode is enabled, both the t-io cluster and MQTT server are started.
	 * </p>
	 *
	 * @throws Exception if any error occurs during startup
	 */
	public void start() throws Exception {
		if (mqttServer != null) {
			mqttServer.start();
			logger.info("MQTT Server started");
		}

		if (!config.isEnabled()) {
			return;
		}

		ClusterConfig clusterConfig = new ClusterConfig(
			config.getClusterHost(),
			config.getClusterPort(),
			this::handleClusterMessage
		);

		for (String seed : config.getSeedMembers()) {
			String[] parts = seed.split(":");
			clusterConfig.addSeedMember(parts[0], Integer.parseInt(parts[1]));
		}

		cluster = new ClusterImpl(clusterConfig);
		cluster.start();

		logger.info("Mqtt cluster manager started on {}:{} with nodeName {}", config.getClusterHost(), config.getClusterPort(), localNodeId);
	}

	private void handleClusterMessage(ClusterDataMessage message) {
		try {
			byte[] payload = message.getPayload();
			if (payload == null || payload.length == 0) {
				return;
			}
			ClusterMessage clusterMsg = ClusterMessageSerializer.fromClusterData(message);
			if (clusterMsg != null) {
				logger.debug("Received cluster message of type: {}", clusterMsg.getType());
				String sourceNode = ClusterMessageSerializer.getSourceNode(message);
				handleClusterMessageInternal(clusterMsg, sourceNode);
			}
		} catch (Exception e) {
			logger.error("Error handling cluster message", e);
		}
	}

	private void handleClusterMessageInternal(ClusterMessage clusterMsg, String sourceNode) {
		ClusterMqttSessionManager sessionManager = (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
		switch (clusterMsg.getType()) {
			case PUBLISH_FORWARD: {
				PublishForwardMessage pfm = (PublishForwardMessage) clusterMsg;
				mqttServer.publishAll(pfm.getMessage().getTopic(), pfm.getMessage().getPayload(), MqttQoS.valueOf(pfm.getMessage().getQos()), pfm.getMessage().isRetain());
				break;
			}
			case SUBSCRIBE_NOTIFY: {
				SubscribeNotifyMessage snm = (SubscribeNotifyMessage) clusterMsg;
				sessionManager.syncRemoteSubscriptions(snm.getClientId(), snm.getNodeId(), snm.getSubscriptions());
				break;
			}
			case UNSUBSCRIBE_NOTIFY: {
				UnsubscribeNotifyMessage unm = (UnsubscribeNotifyMessage) clusterMsg;
				sessionManager.removeRemoteSubscriptions(unm.getClientId(), unm.getTopics());
				break;
			}
			case CLIENT_CONNECT: {
				ClientConnectMessage ccm = (ClientConnectMessage) clusterMsg;
				sessionManager.registerRemoteClient(ccm.getClientId(), sourceNode);
				break;
			}
			case CLIENT_DISCONNECT: {
				ClientDisconnectMessage cdm = (ClientDisconnectMessage) clusterMsg;
				sessionManager.removeRemoteClient(cdm.getClientId());
				break;
			}
			case STATE_SYNC_REQUEST:
				handleStateSyncRequest(sourceNode);
				break;
			case STATE_SYNC_RESPONSE: {
				StateSyncResponseMessage ssm = (StateSyncResponseMessage) clusterMsg;
				sessionManager.syncFullState(ssm.getClientNodeMap(), ssm.getSubscriptionMap());
				logger.info("State sync completed, received {} client mappings", ssm.getClientNodeMap().size());
				break;
			}
			case NODE_LEAVE: {
				sessionManager.clearNodeClientsAndSubscriptions(sourceNode);
				logger.info("Node {} left cluster, cleaned up its clients and subscriptions", sourceNode);
				break;
			}
			case WILL_MESSAGE: {
				WillMessageNotifyMessage wmm = (WillMessageNotifyMessage) clusterMsg;
				IMqttMessageStore messageStore = mqttServer.getServerCreator().getMessageStore();
				if (messageStore != null && wmm.getWillMessage() != null) {
					messageStore.addWillMessage(wmm.getClientId(), wmm.getWillMessage());
					logger.debug("[Cluster] Received and stored will message for clientId: {} from node: {}", wmm.getClientId(), sourceNode);
				}
				break;
			}
			case RETAIN_MESSAGE: {
				RetainMessageNotifyMessage rmm = (RetainMessageNotifyMessage) clusterMsg;
				IMqttMessageStore messageStore = mqttServer.getServerCreator().getMessageStore();
				if (messageStore != null) {
					if (rmm.getRetainMessage() != null) {
						messageStore.addRetainMessage(rmm.getTopic(), rmm.getTimeout(), rmm.getRetainMessage());
						logger.debug("[Cluster] Received and stored retain message for topic: {} from node: {}", rmm.getTopic(), sourceNode);
					} else {
						messageStore.clearRetainMessage(rmm.getTopic());
						logger.debug("[Cluster] Received retain clear for topic: {} from node: {}", rmm.getTopic(), sourceNode);
					}
				}
				break;
			}
			default:
				logger.warn("Unknown cluster message type: {}", clusterMsg.getType());
				break;
		}
	}

	private void handleStateSyncRequest(String requestNodeId) {
		ClusterMqttSessionManager sessionManager = (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();

		Map<String, String> clientNodeMap = sessionManager.getRemoteClientNodeMap();
		Map<String, List<Subscribe>> subscriptionMap = new HashMap<>();
		for (Map.Entry<String, String> entry : clientNodeMap.entrySet()) {
			String clientId = entry.getKey();
			List<Subscribe> subs = sessionManager.getClientSubscriptions(clientId);
			if (subs != null && !subs.isEmpty()) {
				subscriptionMap.put(clientId, subs);
			}
		}

		StateSyncResponseMessage response = new StateSyncResponseMessage();
		response.setClientNodeMap(clientNodeMap);
		response.setSubscriptionMap(subscriptionMap);

		try {
			ClusterDataMessage data = ClusterMessageSerializer.toClusterData(response, localNodeId);
			String[] parts = requestNodeId.split(":");
			Node node = new Node(parts[0], Integer.parseInt(parts[1]));
			cluster.send(node, data);
			logger.info("Sent state sync response to node: {}", requestNodeId);
		} catch (Exception e) {
			logger.error("Failed to send state sync response to: {}", requestNodeId, e);
		}
	}

	public void sendToNode(String nodeId, ClusterMessage clusterMsg) {
		if (!config.isEnabled() || cluster == null) {
			return;
		}
		try {
			String[] parts = nodeId.split(":");
			if (parts.length == 2) {
				Node node = new Node(parts[0], Integer.parseInt(parts[1]));
				cluster.send(node, ClusterMessageSerializer.toClusterData(clusterMsg, localNodeId));
			}
		} catch (Exception e) {
			logger.error("Failed to send message to node: {}", nodeId, e);
		}
	}

	public void broadcast(ClusterMessage clusterMsg) {
		if (!config.isEnabled() || cluster == null) {
			return;
		}
		try {
			cluster.broadcast(ClusterMessageSerializer.toClusterData(clusterMsg, localNodeId));
		} catch (Exception e) {
			logger.error("Failed to broadcast message", e);
		}
	}

	public void stop() {
		if (cluster != null) {
			NodeLeaveMessage leaveMsg = new NodeLeaveMessage();
			broadcast(leaveMsg);

			cluster.stop();
			logger.info("Mqtt cluster manager stopped");
		}

		if (mqttServer != null) {
			mqttServer.stop();
			logger.info("MQTT Server stopped");
		}
	}

	public void publish(String topic, byte[] payload, int qos, boolean retain) {
		if (mqttServer == null) {
			return;
		}

		mqttServer.publishAll(topic, payload, MqttQoS.valueOf(qos), retain);

		if (config.isEnabled() && cluster != null) {
			ClusterMqttSessionManager sessionManager = (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
			List<Subscribe> allSubs = sessionManager.searchAllSubscribe(topic);

			if (allSubs != null && !allSubs.isEmpty()) {
				Set<String> targetNodes = new HashSet<>();
				for (Subscribe sub : allSubs) {
					String node = sessionManager.getClientNode(sub.getClientId());
					if (node != null && !node.equals(localNodeId)) {
						targetNodes.add(node);
					}
				}

				if (!targetNodes.isEmpty()) {
					PublishForwardMessage clusterMsg = new PublishForwardMessage();
					Message message = new Message();
					message.setMessageType(MessageType.UP_STREAM);
					message.setTopic(topic);
					message.setPayload(payload);
					message.setQos(qos);
					message.setRetain(retain);
					clusterMsg.setMessage(message);

					for (String node : targetNodes) {
						sendToNode(node, clusterMsg);
					}
				}
			}
		}
	}

	public String getLocalNodeId() {
		return localNodeId;
	}
}
