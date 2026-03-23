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

package org.dromara.mica.mqtt.broker.cluster.pipeline;

import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.message.BaseMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager.RemoteNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Intercepts upstream MQTT messages and forwards them to subscribers on remote cluster nodes.
 * <p>
 * This handler is positioned in the message pipeline before the local
 * {@code UpStreamMessageHandler} (order 90 vs 100). When a message is published on
 * a topic that has subscribers on other cluster nodes, this dispatcher sends the
 * message to those remote nodes via the t-io cluster framework.
 * </p>
 * <p>
 * The forwarding algorithm ensures O(1) network overhead per remote node—each remote
 * node receives the message exactly once, regardless of how many local subscribers exist.
 * </p>
 *
 * @author L.cm
 * @see BaseMessageHandler
 * @since 1.0.0
 */
public class ClusterMessageDispatcher extends BaseMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(ClusterMessageDispatcher.class);
	private final MqttClusterManager clusterManager;
	private final ClusterMqttSessionManager clusterSessionManager;

	/**
	 * Constructs a new cluster message dispatcher.
	 *
	 * @param mqttServer the embedded MQTT server
	 * @param clusterManager the cluster manager for sending forwarded messages
	 * @param clusterSessionManager the cluster session manager for subscriber lookup
	 */
	public ClusterMessageDispatcher(MqttServer mqttServer, MqttClusterManager clusterManager, ClusterMqttSessionManager clusterSessionManager) {
		super(mqttServer);
		this.clusterManager = clusterManager;
		this.clusterSessionManager = clusterSessionManager;
	}

	@Override
	public boolean handle(Message message) {
		if (MessageType.UP_STREAM != message.getMessageType()) {
			return true;
		}

		String topic = message.getTopic();
		String localNodeId = clusterManager.getLocalNodeId();

		List<Subscribe> subscribers = clusterSessionManager.searchAllSubscribe(topic);

		logger.debug("[Cluster] Received publish on topic: {}, subscribers count: {}", topic,
			subscribers == null ? 0 : subscribers.size());

		if (subscribers == null || subscribers.isEmpty()) {
			logger.debug("[Cluster] No subscribers for topic: {}, skip forwarding", topic);
			return true;
		}

		// Use getSharedGroupNodes to deduplicate shared subscriptions per group (round-robin)
		Map<String, Set<RemoteNode>> groupNodesMap = clusterSessionManager.getSharedGroupNodes(topic);

		Set<String> remoteNodes = new HashSet<>();
		for (Subscribe sub : subscribers) {
			String clientId = sub.getClientId();
			String node = clusterSessionManager.getClientNode(clientId);
			logger.debug("[Cluster] Client: {}, Node: {}", clientId, node);

			if (node != null && !node.equals(localNodeId)) {
				// For non-shared subscriptions, forward to all remote nodes that have subscribers
				// For shared subscriptions, getSharedGroupNodes already did round-robin selection
				if (groupNodesMap.isEmpty()) {
					// Non-shared: forward to every remote node with a subscriber
					remoteNodes.add(node);
					logger.debug("[Cluster] Will forward to node: {} for client: {}", node, clientId);
				}
			}
		}

		// For shared subscriptions, select one node per group via round-robin
		for (Map.Entry<String, Set<RemoteNode>> entry : groupNodesMap.entrySet()) {
			Set<RemoteNode> nodes = entry.getValue();
			if (!nodes.isEmpty()) {
				RemoteNode selected = selectNodeForGroup(nodes);
				if (!selected.nodeId.equals(localNodeId)) {
					remoteNodes.add(selected.nodeId);
					logger.debug("[Cluster] Shared group '{}': selected node: {} (round-robin)",
						entry.getKey(), selected.nodeId);
				}
			}
		}

		if (!remoteNodes.isEmpty()) {
			logger.debug("[Cluster] Forwarding message on topic: {} to remote nodes: {}", topic, remoteNodes);
			for (String nodeId : remoteNodes) {
				forwardToNode(nodeId, message);
			}
		} else {
			logger.debug("[Cluster] No remote nodes need forwarding for topic: {}", topic);
		}

		return true;
	}

	private RemoteNode selectNodeForGroup(Set<RemoteNode> nodes) {
		String[] nodeIds = nodes.stream().map(n -> n.nodeId).toArray(String[]::new);
		int index = ThreadLocalRandom.current().nextInt(nodeIds.length);
		final String selectedNodeId = nodeIds[index];
		return nodes.stream().filter(n -> n.nodeId.equals(selectedNodeId)).findFirst().orElse(nodes.iterator().next());
	}

	private void forwardToNode(String nodeId, Message msg) {
		logger.debug("[Cluster] Forwarding to node: {}, topic: {}", nodeId, msg.getTopic());

		PublishForwardMessage clusterMsg = new PublishForwardMessage();
		clusterMsg.setMessage(msg);

		clusterManager.sendToNode(nodeId, clusterMsg);
	}

	@Override
	public int getOrder() {
		return 90;
	}
}
