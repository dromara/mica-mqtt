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

import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.server.cluster.core.ClusterApi;
import net.dreamlu.mica.net.server.cluster.core.ClusterConfig;
import net.dreamlu.mica.net.server.cluster.core.ClusterImpl;
import net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.message.*;
import org.dromara.mica.mqtt.broker.cluster.metrics.ClusterMetrics;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.SharedSubscriptionStrategy;
import org.dromara.mica.mqtt.broker.cluster.store.InflightStore;
import org.dromara.mica.mqtt.broker.cluster.store.SessionStore;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manager for MQTT broker cluster operations and inter-node communication.
 * This class is responsible for:
 * <ul>
 *   <li>Starting and stopping the t-io cluster foundation</li>
 *   <li>Broadcasting and point-to-point cluster messages</li>
 *   <li>Handling incoming cluster messages and dispatching to appropriate handlers</li>
 *   <li>Coordinating session synchronization across cluster nodes</li>
 * </ul>
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
	private SharedSubscriptionStrategy sharedStrategy;
	private ClusterStorage clusterStorage;
	private ClusterMqttSessionManager sessionManager;
	private final ClusterMetrics metrics = new ClusterMetrics();

	/**
	 * Constructs a new cluster manager with the specified configuration.
	 *
	 * @param config the cluster configuration
	 * @param localNodeId the unique identifier for this local node in the cluster
	 */
	public MqttClusterManager(MqttClusterConfig config, String localNodeId) {
		this(config, localNodeId, null);
	}

	public MqttClusterManager(MqttClusterConfig config, String localNodeId, ClusterMqttSessionManager sessionManager) {
		this.config = config;
		this.localNodeId = localNodeId;
		this.sessionManager = sessionManager;
	}

	/**
	 * Sets the shared subscription strategy used when re-picking a subscriber after a
	 * {@link ClusterMessageType#SHARED_DISPATCH_TO_CLIENT} target is found offline.
	 *
	 * @param sharedStrategy the shared subscription strategy to use
	 */
	public void setSharedStrategy(SharedSubscriptionStrategy sharedStrategy) {
		this.sharedStrategy = sharedStrategy;
	}

	/**
	 * Sets the V3 persistence coordinator.  May be {@code null} when storage
	 * is disabled (in which case the broker runs in V1/V2 in-memory mode).
	 *
	 * @param clusterStorage the storage coordinator; may be {@code null}
	 */
	public void setClusterStorage(ClusterStorage clusterStorage) {
		this.clusterStorage = clusterStorage;
	}

	/**
	 * Returns the V3 persistence coordinator, or {@code null} when storage is
	 * disabled.
	 *
	 * @return the storage coordinator, or {@code null} when storage is disabled
	 */
	public ClusterStorage getClusterStorage() {
		return clusterStorage;
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

		// 新节点加入集群后，向已有节点请求全量状态同步
		StateSyncRequestMessage syncRequest = new StateSyncRequestMessage();
		broadcast(syncRequest);
		logger.info("Sent state sync request to cluster");
	}

	private void handleClusterMessage(ClusterDataMessage message) {
		try {
			ClusterMessage clusterMsg = ClusterMessageSerializer.fromClusterData(message);
			if (clusterMsg != null) {
				logger.debug("Received cluster message of type: {}", clusterMsg.getType());
				String sourceNode = ClusterMessageSerializer.getSourceNode(message);
				handleClusterMessageInternal(clusterMsg, sourceNode);
			} else {
				logger.debug("Skipped unknown or unsupported cluster message, type header: {}",
					message.getHeader(ClusterMessageSerializer.HEADER_TYPE));
			}
		} catch (Exception e) {
			logger.error("Error handling cluster message", e);
		}
	}

	private void handleClusterMessageInternal(ClusterMessage clusterMsg, String sourceNode) {
		ClusterMqttSessionManager sessionManager = this.sessionManager != null
			? this.sessionManager
			: (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
		switch (clusterMsg.getType()) {
			case PUBLISH_FORWARD: {
				PublishForwardMessage pfm = (PublishForwardMessage) clusterMsg;
				// retain 存储由 RETAIN_MESSAGE 单独同步，此处仅投递给本地订阅者，避免通过
				// ClusterMqttMessageStore 再次广播导致无限循环
				Message fwdMsg = pfm.getMessage();
				mqttServer.publishAll(fwdMsg.getTopic(), fwdMsg.getPayload(), MqttQoS.valueOf(fwdMsg.getQos()), false);
				// V3 inflight persistence (P2.3): record QoS 1/2 forwards so the TTL
				// cleaner can detect stuck deliveries even before per-client ACK is wired.
				recordInflightForward(fwdMsg, sourceNode);
				break;
			}
			case SESSION_TAKEOVER_REQUEST: {
				handleSessionTakeoverRequest((SessionTakeoverRequestMessage) clusterMsg, sourceNode);
				break;
			}
			case SESSION_TAKEOVER_RESPONSE: {
				handleSessionTakeoverResponse((SessionTakeoverResponseMessage) clusterMsg, sourceNode);
				break;
			}
			case SESSION_MIGRATED_NOTIFY: {
				handleSessionMigratedNotify((SessionMigratedNotifyMessage) clusterMsg);
				break;
			}
			case SESSION_DELETE_NOTIFY: {
				handleSessionDeleteNotify((SessionDeleteNotifyMessage) clusterMsg);
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
				logger.info("State sync completed, received {} client mappings", ssm.getClientNodeMap());
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
				// 使用 Local 方法直接存储到 delegate，避免通过 ClusterMqttMessageStore 再次广播
				if (messageStore instanceof ClusterMqttMessageStore && wmm.getWillMessage() != null) {
					((ClusterMqttMessageStore) messageStore).addWillMessageLocal(wmm.getClientId(), wmm.getWillMessage());
					logger.debug("[Cluster] Received and stored will message for clientId: {} from node: {}", wmm.getClientId(), sourceNode);
				}
				break;
			}
			case RETAIN_MESSAGE: {
				RetainMessageNotifyMessage rmm = (RetainMessageNotifyMessage) clusterMsg;
				IMqttMessageStore messageStore = mqttServer.getServerCreator().getMessageStore();
				// 使用 Local 方法直接存储到 delegate，避免通过 ClusterMqttMessageStore 再次广播
				if (messageStore instanceof ClusterMqttMessageStore) {
					ClusterMqttMessageStore clusterStore = (ClusterMqttMessageStore) messageStore;
					if (rmm.getRetainMessage() != null) {
						clusterStore.addRetainMessageLocal(rmm.getTopic(), rmm.getTimeout(), rmm.getRetainMessage());
						logger.debug("[Cluster] Received and stored retain message for topic: {} from node: {}", rmm.getTopic(), sourceNode);
					} else {
						clusterStore.clearRetainMessageLocal(rmm.getTopic());
						logger.debug("[Cluster] Received retain clear for topic: {} from node: {}", rmm.getTopic(), sourceNode);
					}
				}
				break;
			}
			case SHARED_DISPATCH_TO_CLIENT: {
				SharedDispatchToClientMessage sdm = (SharedDispatchToClientMessage) clusterMsg;
				handleSharedDispatchToClient(sdm, sessionManager);
				break;
			}
			default:
				logger.warn("Unknown cluster message type: {}", clusterMsg.getType());
				break;
		}
	}

	/**
	 * Handles a SHARED_DISPATCH_TO_CLIENT message received from another node.
	 * <p>
	 * The publisher's node has already selected this client as the single recipient
	 * for a shared-subscription delivery.  This node delivers the message to the
	 * local client.
	 * </p>
	 * <p>
	 * If the target client is no longer connected (race between disconnect broadcast
	 * and this message), this node performs a local re-pick using the same strategy
	 * and delivers to a still-active subscriber (plan B from the design doc).
	 * </p>
	 */
	private void handleSharedDispatchToClient(SharedDispatchToClientMessage sdm,
											  ClusterMqttSessionManager sessionManager) {
		String clientId = sdm.getClientId();
		String topic = sdm.getTopic();
		Message msg = sdm.getMessage();

		if (msg == null) {
			logger.warn("[Cluster] Received SHARED_DISPATCH_TO_CLIENT with null message, clientId={}", clientId);
			return;
		}

		// Try to deliver to the selected client locally.
		boolean delivered = mqttServer.publish(clientId, msg.getTopic(),
			msg.getPayload(), MqttQoS.valueOf(msg.getQos()));

		if (delivered) {
			logger.debug("[Cluster] Shared dispatch delivered to client={} topic={}", clientId, topic);
			return;
		}

		// Target client is no longer local — re-pick from the local shared-subscription table.
		logger.warn("[Cluster] Shared dispatch target client={} not found on this node for topic={}, re-picking",
			clientId, topic);

		if (sharedStrategy == null) {
			logger.debug("[Cluster] No shared strategy configured, dropping re-pick for topic={}", topic);
			return;
		}

		List<Subscribe> candidates = sessionManager.searchAllSubscribe(topic);
		if (candidates == null || candidates.isEmpty()) {
			logger.debug("[Cluster] No subscribers remain for topic={}, dropping", topic);
			return;
		}

		// Narrow to shared subscribers only, group the same way the dispatcher does.
		java.util.Map<String, java.util.List<Subscribe>> sharedGroups = new java.util.HashMap<>();
		for (Subscribe sub : candidates) {
			String topicFilter = sub.getTopicFilter();
			if (topicFilter == null) {
				continue;
			}
			if (topicFilter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
				String groupName = TopicFilterType.getShareGroupName(topicFilter);
				sharedGroups.computeIfAbsent(groupName, k -> new java.util.ArrayList<>()).add(sub);
			} else if (topicFilter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
				sharedGroups.computeIfAbsent("$queue", k -> new java.util.ArrayList<>()).add(sub);
			}
		}

		for (java.util.Map.Entry<String, java.util.List<Subscribe>> entry : sharedGroups.entrySet()) {
			Subscribe rePicked = sharedStrategy.pick(entry.getKey(), entry.getValue(), localNodeId, msg);
			if (rePicked == null) {
				continue;
			}
			String rePickedNode = sessionManager.getClientNode(rePicked.getClientId());
			boolean isLocal = rePickedNode == null || rePickedNode.equals(localNodeId);
			if (isLocal) {
				mqttServer.publish(rePicked.getClientId(), msg.getTopic(),
					msg.getPayload(), MqttQoS.valueOf(msg.getQos()));
				logger.debug("[Cluster] Re-pick delivered to client={} topic={}", rePicked.getClientId(), topic);
			} else {
				SharedDispatchToClientMessage retry = new SharedDispatchToClientMessage();
				retry.setClientId(rePicked.getClientId());
				retry.setTopic(topic);
				retry.setMessage(msg);
				sendToNode(rePickedNode, retry);
				logger.debug("[Cluster] Re-pick forwarded to node={} client={} topic={}", rePickedNode, rePicked.getClientId(), topic);
			}
		}
	}

	private void handleStateSyncRequest(String requestNodeId) {
		ClusterMqttSessionManager sessionManager = this.sessionManager != null
			? this.sessionManager
			: (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();

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
			if (parts.length != 2) {
				logger.warn("Invalid node id format for state sync request: {}", requestNodeId);
				return;
			}
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
			Set<String> remoteNodes = getRemoteNodesWithSubscriber(topic);
			if (!remoteNodes.isEmpty()) {
				PublishForwardMessage clusterMsg = new PublishForwardMessage();
				Message message = new Message();
				message.setMessageType(MessageType.UP_STREAM);
				message.setTopic(topic);
				message.setPayload(payload);
				message.setQos(qos);
				message.setRetain(retain);
				clusterMsg.setMessage(message);

				for (String node : remoteNodes) {
					sendToNode(node, clusterMsg);
				}
			}
		}
	}

	/**
	 * Returns remote nodes that have subscribers for the given topic.
	 * Uses searchAllSubscribe which returns all local + remote subscriptions
	 * due to the full replication strategy (V1).
	 *
	 * @param topic the published topic
	 * @return set of remote node identifiers that have subscribers
	 */
	public Set<String> getRemoteNodesWithSubscriber(String topic) {
		Set<String> remoteNodes = new HashSet<>();
		if (!config.isEnabled() || cluster == null) {
			return remoteNodes;
		}
		ClusterMqttSessionManager sessionManager = this.sessionManager != null
			? this.sessionManager
			: (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
		List<Subscribe> allSubs = sessionManager.searchAllSubscribe(topic);
		if (allSubs != null) {
			for (Subscribe sub : allSubs) {
				String node = sessionManager.getClientNode(sub.getClientId());
				if (node != null && !node.equals(localNodeId)) {
					remoteNodes.add(node);
				}
			}
		}
		return remoteNodes;
	}

	public String getLocalNodeId() {
		return localNodeId;
	}

	/**
	 * Records an inflight entry for a forwarded QoS 1/2 message.
	 * <p>
	 * The V3 inflight store is the durable record of "messages in the process of
	 * being delivered".  This broker records one row per (client, packetId); the
	 * TTL cleaner (see {@link org.dromara.mica.mqtt.broker.cluster.store.InflightTtlCleaner})
	 * eventually removes entries that have not been ACKed.  Packet IDs are
	 * synthesized from a per-message counter because the wire protocol between
	 * cluster nodes does not carry the destination clientId + packetId; per-client
	 * tracking is left for a future revision.
	 * </p>
	 *
	 * @param message the forwarded message
	 * @param sourceNode the originating node id
	 */
	private void recordInflightForward(Message message, String sourceNode) {
		if (clusterStorage == null || !clusterStorage.isActive()) {
			return;
		}
		if (message == null) {
			return;
		}
		int qos = message.getQos();
		if (qos < 1) {
			// QoS 0 has no ACK and no inflight semantics.
			return;
		}
		InflightStore inflight = clusterStorage.getInflightStore();
		if (inflight == null) {
			return;
		}
		long ttl = clusterStorage.getConfig().getInflightTtlMs();
		long expireAt = System.currentTimeMillis() + ttl;
		// Synthesize a clientId: cross-node forwards don't carry one, so we
		// bucket by (topic, sourceNode) for purposes of TTL cleanup.
		String pseudoClient = "fwd:" + sourceNode;
		// packetId 0 is the well-known inflight bucket for non-Acked forwards.
		inflight.put(pseudoClient, 0, expireAt,
			message.getTopic(), message.getPayload(), qos);
	}

	// -----------------------------------------------------------------------
	// Session takeover protocol (P2.1)
	// -----------------------------------------------------------------------

	/**
	 * Initiates a session takeover from a remote node.
	 * <p>
	 * Called by the connect-status listener when a new MQTT CONNECT arrives for a
	 * clientId that is already owned by a remote node.  Sends
	 * {@link SessionTakeoverRequestMessage} to that node; the response drives
	 * {@link #handleSessionTakeoverResponse}.
	 * </p>
	 * <p>
	 * If the previous owner never responds, the caller is expected to fall back to
	 * the V1 behavior of treating the session as a fresh start after
	 * {@code timeoutMs}.  A best-effort cleanup is logged but no exception is
	 * propagated so a missing owner cannot block new connections.
	 * </p>
	 *
	 * @param clientId the client whose session should be taken over
	 * @param previousOwnerNode the node that currently owns the session
	 * @param timeoutMs how long the new owner waits for a response
	 */
	public void initiateSessionTakeover(String clientId, String previousOwnerNode, long timeoutMs) {
		if (cluster == null || clientId == null || previousOwnerNode == null) {
			return;
		}
		if (previousOwnerNode.equals(localNodeId)) {
			return;
		}
		SessionTakeoverRequestMessage request = new SessionTakeoverRequestMessage();
		request.setClientId(clientId);
		request.setAttemptId(System.nanoTime());
		request.setTimeoutMs(timeoutMs);
		logger.info("[Cluster] Initiating session takeover: client={} from={} to={}",
			clientId, previousOwnerNode, localNodeId);
		sendToNode(previousOwnerNode, request);
	}

	/**
	 * Handles an incoming session-takeover request from another node.
	 * <p>
	 * Reads the session bytes from the local V3 store and replies with
	 * {@link SessionTakeoverResponseMessage}.  If storage is disabled or the
	 * session is not present locally, returns {@code not_found}.
	 * </p>
	 */
	private void handleSessionTakeoverRequest(SessionTakeoverRequestMessage request, String sourceNode) {
		SessionTakeoverResponseMessage response = new SessionTakeoverResponseMessage();
		response.setClientId(request.getClientId());
		response.setAttemptId(request.getAttemptId());
		if (clusterStorage == null || !clusterStorage.isActive()) {
			response.setStatus(SessionTakeoverResponseMessage.STATUS_NOT_FOUND);
		} else {
			byte[] sessionBytes = clusterStorage.getSessionStore().loadRaw(request.getClientId());
			if (sessionBytes == null) {
				response.setStatus(SessionTakeoverResponseMessage.STATUS_NOT_FOUND);
			} else {
				response.setStatus(SessionTakeoverResponseMessage.STATUS_OK);
				response.setSessionBytes(sessionBytes);
			}
		}
		if (cluster != null) {
			sendToNode(sourceNode, response);
		}
	}

	/**
	 * Handles an incoming session-takeover response.
	 * <p>
	 * On {@code ok}, the session bytes are installed into the local store and a
	 * {@link SessionMigratedNotifyMessage} is broadcast.  Other statuses are
	 * logged for the operator.
	 * </p>
	 */
	private void handleSessionTakeoverResponse(SessionTakeoverResponseMessage response, String sourceNode) {
		String clientId = response.getClientId();
		if (clientId == null) {
			return;
		}
		String status = response.getStatus();
		if (!SessionTakeoverResponseMessage.STATUS_OK.equals(status)) {
			logger.warn("[Cluster] Session takeover response not ok: client={} status={} from={}",
				clientId, status, sourceNode);
			return;
		}
		if (clusterStorage == null || !clusterStorage.isActive()) {
			return;
		}
		SessionStore.Session installed = clusterStorage.getSessionStore()
			.restoreRaw(clientId, response.getSessionBytes());
		if (installed == null) {
			logger.warn("[Cluster] Takeover restore returned null: client={}", clientId);
			return;
		}
		logger.info("[Cluster] Session takeover ok: client={} from={} subs={}",
			clientId, sourceNode,
			installed.getSubscriptions() == null ? 0 : installed.getSubscriptions().size());
		// Broadcast the new ownership to all peers.
		SessionMigratedNotifyMessage notify = new SessionMigratedNotifyMessage();
		notify.setClientId(clientId);
		notify.setNewOwnerNodeId(localNodeId);
		notify.setPreviousOwnerNodeId(sourceNode);
		broadcast(notify);
	}

	/**
	 * Handles a session-migrated broadcast: a client is now owned by a different
	 * node.  Updates the in-memory client→node mapping so messages route correctly.
	 */
	private void handleSessionMigratedNotify(SessionMigratedNotifyMessage notify) {
		String clientId = notify.getClientId();
		String newOwner = notify.getNewOwnerNodeId();
		if (clientId == null || newOwner == null) {
			return;
		}
		if (sessionManager != null) {
			sessionManager.getClientNodeMap().put(clientId, newOwner);
		}
		// If we used to own this session, drop local subscriptions and the
		// durable record.
		if (localNodeId.equals(notify.getPreviousOwnerNodeId()) && sessionManager != null) {
			sessionManager.clearLocalSubscription(clientId);
		}
		logger.info("[Cluster] Session migrated: client={} newOwner={} prev={}",
			clientId, newOwner, notify.getPreviousOwnerNodeId());
	}

	/**
	 * Handles a session-delete broadcast: a session was permanently removed.
	 * Cleans up the in-memory mapping and the durable record on every node.
	 */
	private void handleSessionDeleteNotify(SessionDeleteNotifyMessage notify) {
		String clientId = notify.getClientId();
		if (clientId == null) {
			return;
		}
		if (sessionManager != null) {
			sessionManager.getClientNodeMap().remove(clientId);
		}
		if (clusterStorage != null && clusterStorage.isActive()) {
			clusterStorage.getSessionStore().delete(clientId);
		}
	}

	/**
	 * Public hook for {@link ClusterMqttConnectStatusListener} to trigger a takeover.
	 *
	 * @param sessionManager the session manager instance
	 */
	public void setSessionManager(ClusterMqttSessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	/**
	 * Returns the live metrics instance.  Callers may read counters directly
	 * via the {@code get*} accessors, or call {@link ClusterMetrics#snapshot()}
	 * to get a map for export.
	 *
	 * @return the shared {@link ClusterMetrics} instance for this manager
	 */
	public ClusterMetrics getMetrics() {
		return metrics;
	}
}
