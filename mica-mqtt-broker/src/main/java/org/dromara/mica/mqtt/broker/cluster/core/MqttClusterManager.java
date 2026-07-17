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
import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.server.cluster.core.ClusterApi;
import net.dreamlu.mica.net.server.cluster.core.ClusterConfig;
import net.dreamlu.mica.net.server.cluster.core.ClusterImpl;
import net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.message.*;
import org.dromara.mica.mqtt.broker.cluster.metrics.ClusterMetrics;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.SharedSubscriptionStrategy;
import org.dromara.mica.mqtt.broker.cluster.store.LocalKvStore;
import org.dromara.mica.mqtt.broker.cluster.store.RetainShardRouter;
import org.dromara.mica.mqtt.broker.cluster.store.RetainIndex;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
	private final Map<String, PendingTakeover> pendingTakeovers = new ConcurrentHashMap<>();
	private final Map<String, TakeoverGrant> takeoverGrants = new ConcurrentHashMap<>();
	private final Map<String, PendingRetainQuery> pendingRetainQueries = new ConcurrentHashMap<>();
	private final AtomicLong retainQuerySequence = new AtomicLong();
	private final RetainShardRouter retainShardRouter = new RetainShardRouter();
	private final Set<String> knownRemoteNodes = ConcurrentHashMap.newKeySet();
	private final Map<String, Long> lastNodeSeen = new ConcurrentHashMap<>();
	private final ScheduledExecutorService takeoverTimeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "mica-mqtt-session-takeover-timeout");
		thread.setDaemon(true);
		return thread;
	});
	private final ScheduledExecutorService membershipMonitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "mica-mqtt-cluster-membership-monitor");
		thread.setDaemon(true);
		return thread;
	});

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
		long monitorInterval = Math.max(1_000L, config.getHeartbeatInterval());
		pollClusterMembership();
		membershipMonitorExecutor.scheduleWithFixedDelay(
			this::pollClusterMembership, monitorInterval, monitorInterval, TimeUnit.MILLISECONDS);

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
				String sourceNode = ClusterMessageSerializer.getSourceNode(message);
				markNodeSeen(sourceNode);
				if (clusterMsg.getType() == ClusterMessageType.HEARTBEAT) {
					return;
				}
				metrics.clusterMessagesReceivedInc();
				logger.debug("Received cluster message of type: {}", clusterMsg.getType());
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
		if (clusterMsg.getType() == ClusterMessageType.HEARTBEAT) {
			return;
		}
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
				handleNodeDeparture(sourceNode);
				break;
			}
			case WILL_MESSAGE: {
				WillMessageNotifyMessage wmm = (WillMessageNotifyMessage) clusterMsg;
				IMqttMessageStore messageStore = mqttServer.getServerCreator().getMessageStore();
				// 使用 Local 方法直接存储到 delegate，避免通过 ClusterMqttMessageStore 再次广播
				if (messageStore instanceof ClusterMqttMessageStore) {
					ClusterMqttMessageStore clusterStore = (ClusterMqttMessageStore) messageStore;
					if (wmm.getWillMessage() == null) {
						clusterStore.clearWillMessageLocal(wmm.getClientId());
					} else {
						clusterStore.addWillMessageLocal(wmm.getClientId(), wmm.getWillMessage());
						logger.debug("[Cluster] Received and stored will message for clientId: {} from node: {}", wmm.getClientId(), sourceNode);
					}
				}
				break;
			}
			case RETAIN_MESSAGE: {
				RetainMessageNotifyMessage rmm = (RetainMessageNotifyMessage) clusterMsg;
				long sentAt = rmm.getSentAtMillis();
				metrics.retainReplicaReceived(sentAt <= 0L
					? -1L : Math.max(0L, System.currentTimeMillis() - sentAt));
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
			case RETAIN_QUERY:
				handleRetainQuery((RetainQueryMessage) clusterMsg, sourceNode);
				break;
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
			metrics.sharedDispatchReceivedInc();
			logger.debug("[Cluster] Shared dispatch delivered to client={} topic={}", clientId, topic);
			return;
		}

		// Target client is no longer local — re-pick from the local shared-subscription table.
		logger.warn("[Cluster] Shared dispatch target client={} not found on this node for topic={}, re-picking",
			clientId, topic);
		metrics.sharedDispatchRepickInc();

		if (sharedStrategy == null) {
			metrics.sharedDispatchDroppedInc();
			logger.debug("[Cluster] No shared strategy configured, dropping re-pick for topic={}", topic);
			return;
		}

		List<Subscribe> candidates = sessionManager.searchAllSubscribe(topic);
		if (candidates == null || candidates.isEmpty()) {
			metrics.sharedDispatchDroppedInc();
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
			rebalanceRetain(requestNodeId, null);
		} catch (Exception e) {
			logger.error("Failed to send state sync response to: {}", requestNodeId, e);
		}
	}

	public boolean sendToNode(String nodeId, ClusterMessage clusterMsg) {
		if (!config.isEnabled() || cluster == null) {
			return false;
		}
		try {
			String[] parts = nodeId.split(":");
			if (parts.length == 2) {
				Node node = new Node(parts[0], Integer.parseInt(parts[1]));
				if (cluster.send(node, ClusterMessageSerializer.toClusterData(clusterMsg, localNodeId))) {
					metrics.clusterMessagesSentInc();
					return true;
				} else {
					metrics.clusterSendErrorsInc();
					logger.debug("Cluster channel is not available for node: {}", nodeId);
				}
			} else {
				metrics.clusterSendErrorsInc();
				logger.warn("Invalid cluster node id: {}", nodeId);
			}
		} catch (Exception e) {
			metrics.clusterSendErrorsInc();
			logger.error("Failed to send message to node: {}", nodeId, e);
		}
		return false;
	}

	public boolean isRetainShardingEnabled() {
		return config.isRetainShardingEnabled();
	}

	public List<String> getRetainReplicaNodes(String topic) {
		return retainShardRouter.replicasOf(topic, getClusterNodeIds(), config.getRetainReplicationFactor());
	}

	public long getRetainQueryTimeoutMs() {
		return config.getRetainQueryTimeoutMs();
	}

	public Set<String> getClusterNodeIds() {
		Set<String> nodeIds = new HashSet<>();
		nodeIds.add(localNodeId);
		nodeIds.addAll(knownRemoteNodes);
		return nodeIds;
	}

	private Set<String> currentRemoteNodeIds() {
		Set<String> nodeIds = new HashSet<>();
		if (cluster == null) {
			return nodeIds;
		}
		Collection<Node> remoteMembers = cluster.getRemoteMembers();
		if (remoteMembers != null) {
			for (Node node : remoteMembers) {
				nodeIds.add(node.getPeerHost());
			}
		}
		return nodeIds;
	}

	private void pollClusterMembership() {
		try {
			long now = System.currentTimeMillis();
			for (String nodeId : currentRemoteNodeIds()) {
				if (probeNode(nodeId)) {
					markNodeSeen(nodeId);
					continue;
				}
				Long lastSeen = lastNodeSeen.putIfAbsent(nodeId, now);
				if (knownRemoteNodes.contains(nodeId) && lastSeen != null
					&& now - lastSeen >= Math.max(1L, config.getNodeTimeout())) {
					handleNodeDeparture(nodeId);
				}
			}
		} catch (Throwable e) {
			logger.warn("[Cluster] Membership monitor failed", e);
		}
	}

	private boolean probeNode(String nodeId) {
		try {
			String[] parts = nodeId.split(":");
			if (parts.length != 2) {
				return false;
			}
			Node node = new Node(parts[0], Integer.parseInt(parts[1]));
			return cluster.send(node, ClusterMessageSerializer.toClusterData(new HeartbeatMessage(), localNodeId));
		} catch (Exception e) {
			return false;
		}
	}

	private void markNodeSeen(String nodeId) {
		if (nodeId == null || nodeId.equals(localNodeId)) {
			return;
		}
		lastNodeSeen.put(nodeId, System.currentTimeMillis());
		if (knownRemoteNodes.add(nodeId)) {
			rebalanceRetain(nodeId, null);
			logger.info("[Cluster] Detected joined node: {}", nodeId);
		}
	}

	private void handleNodeDeparture(String nodeId) {
		if (knownRemoteNodes.remove(nodeId)) {
			metrics.nodeDeparturesInc();
		}
		lastNodeSeen.remove(nodeId);
		List<String> departedClients = new ArrayList<>();
		if (sessionManager != null) {
			for (Map.Entry<String, String> entry : sessionManager.getClientNodeMap().entrySet()) {
				if (nodeId.equals(entry.getValue())) {
					departedClients.add(entry.getKey());
				}
			}
			sessionManager.clearNodeClientsAndSubscriptions(nodeId);
		}
		publishDepartedNodeWills(nodeId, departedClients);
		rebalanceRetain(null, nodeId);
		logger.info("[Cluster] Node {} left, cleaned routes and rebalanced retained replicas", nodeId);
	}

	private void publishDepartedNodeWills(String departedNode, List<String> clientIds) {
		if (clientIds.isEmpty() || mqttServer == null) {
			return;
		}
		Set<String> survivors = getClusterNodeIds();
		survivors.remove(departedNode);
		String coordinator = survivors.stream().sorted().findFirst().orElse(localNodeId);
		if (!localNodeId.equals(coordinator)) {
			return;
		}
		IMqttMessageStore messageStore = mqttServer.getServerCreator().getMessageStore();
		for (String clientId : clientIds) {
			Message will = messageStore.getWillMessage(clientId);
			if (will == null) {
				continue;
			}
			mqttServer.getServerCreator().getMqttExecutor().execute(() -> {
				try {
					mqttServer.getServerCreator().getMessagePipeline().handle(will);
					messageStore.clearWillMessage(clientId);
				} catch (Throwable e) {
					logger.error("[Cluster] Failed to publish will for departed node client={}", clientId, e);
				}
			});
		}
	}

	private void rebalanceRetain(String additionalNode, String removedNode) {
		if (!isRetainShardingEnabled() || mqttServer == null) {
			return;
		}
		IMqttMessageStore messageStore = mqttServer.getServerCreator().getMessageStore();
		if (!(messageStore instanceof ClusterMqttMessageStore)) {
			return;
		}
		Set<String> nodes = getClusterNodeIds();
		if (additionalNode != null) {
			nodes.add(additionalNode);
		}
		if (removedNode != null) {
			nodes.remove(removedNode);
		}
		long now = System.currentTimeMillis();
		for (RetainIndex.RetainEntry entry
			: ((ClusterMqttMessageStore) messageStore).getAllRetainEntriesLocal()) {
			Message retained = entry.getMessage();
			List<String> replicas = retainShardRouter.replicasOf(
				retained.getTopic(), nodes, config.getRetainReplicationFactor());
			RetainMessageNotifyMessage notification = new RetainMessageNotifyMessage();
			notification.setTopic(retained.getTopic());
			notification.setRetainMessage(retained);
			notification.setTimeout(entry.remainingTimeoutSeconds(now));
			for (String replica : replicas) {
				if (!localNodeId.equals(replica)) {
					sendToNode(replica, notification);
				}
			}
		}
	}

	public List<Message> queryRemoteRetain(String topicFilter, long timeoutMs) {
		if (!isRetainShardingEnabled() || cluster == null) {
			return Collections.emptyList();
		}
		Set<String> remoteNodes;
		if (topicFilter.indexOf('#') < 0 && topicFilter.indexOf('+') < 0) {
			remoteNodes = new HashSet<>(getRetainReplicaNodes(topicFilter));
		} else {
			remoteNodes = getClusterNodeIds();
		}
		remoteNodes.remove(localNodeId);
		if (remoteNodes.isEmpty()) {
			return Collections.emptyList();
		}
		String requestId = localNodeId + ':' + retainQuerySequence.incrementAndGet();
		PendingRetainQuery pending = new PendingRetainQuery(remoteNodes.size());
		pendingRetainQueries.put(requestId, pending);
		metrics.retainQueryRequestsInc();
		RetainQueryMessage query = new RetainQueryMessage();
		query.setRequestId(requestId);
		query.setTopicFilter(topicFilter);
		for (String nodeId : remoteNodes) {
			sendToNode(nodeId, query);
		}
		try {
			if (!pending.await(Math.max(1L, timeoutMs))) {
				metrics.retainQueryTimedOutInc();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			pendingRetainQueries.remove(requestId, pending);
		}
		return pending.messages();
	}

	private void handleRetainQuery(RetainQueryMessage query, String sourceNode) {
		if (query.isResponse()) {
			PendingRetainQuery pending = pendingRetainQueries.get(query.getRequestId());
			if (pending != null) {
				pending.accept(sourceNode, query.getMessages());
			}
			return;
		}
		List<Message> retained = Collections.emptyList();
		IMqttMessageStore messageStore = mqttServer == null ? null : mqttServer.getServerCreator().getMessageStore();
		if (messageStore instanceof ClusterMqttMessageStore) {
			retained = ((ClusterMqttMessageStore) messageStore).getRetainMessageLocal(query.getTopicFilter());
		} else if (messageStore != null) {
			retained = messageStore.getRetainMessage(query.getTopicFilter());
		}
		RetainQueryMessage response = new RetainQueryMessage();
		response.setRequestId(query.getRequestId());
		response.setTopicFilter(query.getTopicFilter());
		response.setResponse(true);
		response.setMessages(retained);
		sendToNode(sourceNode, response);
	}

	public void broadcast(ClusterMessage clusterMsg) {
		if (!config.isEnabled() || cluster == null) {
			return;
		}
		try {
			cluster.broadcast(ClusterMessageSerializer.toClusterData(clusterMsg, localNodeId));
			metrics.clusterMessagesSentInc();
		} catch (Exception e) {
			metrics.clusterSendErrorsInc();
			logger.error("Failed to broadcast message", e);
		}
	}

	public void stop() {
		takeoverTimeoutExecutor.shutdownNow();
		membershipMonitorExecutor.shutdownNow();
		pendingTakeovers.clear();
		knownRemoteNodes.clear();
		lastNodeSeen.clear();
		pendingRetainQueries.clear();
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
		long attemptId = System.nanoTime();
		request.setAttemptId(attemptId);
		request.setTimeoutMs(timeoutMs);
		PendingTakeover pending = new PendingTakeover(attemptId, previousOwnerNode);
		if (pendingTakeovers.putIfAbsent(clientId, pending) != null) {
			logger.debug("[Cluster] Session takeover already pending: client={}", clientId);
			return;
		}
		metrics.sessionTakeoverStartedInc();
		long effectiveTimeoutMs = Math.max(1L, timeoutMs);
		takeoverTimeoutExecutor.schedule(() -> {
			if (pendingTakeovers.remove(clientId, pending)) {
				metrics.sessionTakeoverTimedOutInc();
				logger.warn("[Cluster] Session takeover timed out: client={} previousOwner={} attempt={}",
					clientId, previousOwnerNode, attemptId);
			}
		}, effectiveTimeoutMs, TimeUnit.MILLISECONDS);
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
		if (!tryGrantTakeover(request.getClientId(), sourceNode, request.getAttemptId(), request.getTimeoutMs())) {
			response.setStatus(SessionTakeoverResponseMessage.STATUS_TIMEOUT);
		} else if (clusterStorage == null || !clusterStorage.isActive() || clusterStorage.getSessionStore() == null) {
			response.setStatus(SessionTakeoverResponseMessage.STATUS_NOT_FOUND);
		} else {
			byte[] sessionBytes = clusterStorage.getSessionStore().loadRaw(request.getClientId());
			if (sessionBytes == null) {
				response.setStatus(SessionTakeoverResponseMessage.STATUS_NOT_FOUND);
			} else {
				response.setStatus(SessionTakeoverResponseMessage.STATUS_OK);
				response.setSessionBytes(sessionBytes);
				if (clusterStorage.getInflightStore() != null) {
					response.setInflightEntries(clusterStorage.getInflightStore().listByClient(request.getClientId()));
				}
			}
		}
		if (!SessionTakeoverResponseMessage.STATUS_OK.equals(response.getStatus())) {
			releaseTakeoverGrant(request.getClientId(), sourceNode, request.getAttemptId());
		}
		if (cluster != null) {
			sendToNode(sourceNode, response);
		}
	}

	boolean tryGrantTakeover(String clientId, String requesterNode, long attemptId, long timeoutMs) {
		if (clientId == null || requesterNode == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		long expiresAt = now + Math.max(1L, timeoutMs);
		TakeoverGrant candidate = new TakeoverGrant(requesterNode, attemptId, expiresAt);
		TakeoverGrant granted = takeoverGrants.compute(clientId, (key, current) ->
			current == null || current.expiresAt <= now ? candidate : current);
		return granted == candidate
			|| (granted.attemptId == attemptId && granted.requesterNode.equals(requesterNode));
	}

	private void releaseTakeoverGrant(String clientId, String requesterNode, long attemptId) {
		takeoverGrants.computeIfPresent(clientId, (key, grant) ->
			grant.attemptId == attemptId && grant.requesterNode.equals(requesterNode) ? null : grant);
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
		PendingTakeover pending = pendingTakeovers.get(clientId);
		if (pending == null || pending.attemptId != response.getAttemptId()
			|| !pending.previousOwnerNode.equals(sourceNode)) {
			logger.warn("[Cluster] Ignoring stale session takeover response: client={} attempt={} from={}",
				clientId, response.getAttemptId(), sourceNode);
			return;
		}
		pendingTakeovers.remove(clientId, pending);
		String status = response.getStatus();
		if (!SessionTakeoverResponseMessage.STATUS_OK.equals(status)) {
			metrics.sessionTakeoverFailedInc();
			logger.warn("[Cluster] Session takeover response not ok: client={} status={} from={}",
				clientId, status, sourceNode);
			return;
		}
		if (clusterStorage == null || !clusterStorage.isActive() || clusterStorage.getSessionStore() == null) {
			metrics.sessionTakeoverFailedInc();
			return;
		}
		SessionStore.Session installed = clusterStorage.getSessionStore()
			.restoreRaw(clientId, response.getSessionBytes());
		if (installed == null) {
			metrics.sessionTakeoverFailedInc();
			logger.warn("[Cluster] Takeover restore returned null: client={}", clientId);
			return;
		}
		installed.setOwnerNodeId(localNodeId);
		clusterStorage.getSessionStore().save(clientId, installed);
		if (sessionManager != null) {
			sessionManager.restoreLocalSession(installed);
			ChannelContext context = Tio.getByBsId(mqttServer.getServerConfig(), clientId);
			int replayed = sessionManager.replayInflight(context, clientId,
				response.getInflightEntries(), mqttServer.getServerCreator().getTaskService());
			metrics.inflightReplayedAdd(replayed);
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
		metrics.sessionTakeoverSucceededInc();
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
			sessionManager.applySessionMigration(clientId, newOwner);
		}
		if (localNodeId.equals(notify.getPreviousOwnerNodeId())) {
			takeoverGrants.remove(clientId);
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
		if (clusterStorage != null && clusterStorage.isActive() && clusterStorage.getSessionStore() != null) {
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

	/**
	 * Exports cluster counters together with storage health and capacity gauges.
	 * The returned text can be served directly from an application-owned
	 * Prometheus scrape endpoint.
	 *
	 * @return metrics in Prometheus text exposition format
	 */
	public String toPrometheus() {
		StringBuilder output = new StringBuilder(metrics.toPrometheus());
		ClusterStorage storage = clusterStorage;
		LocalKvStore.StoreStats stats = storage == null
			? new LocalKvStore.StoreStats(-1L, 0L, false)
			: storage.getStats();
		appendGauge(output, "mqtt_cluster_storage_healthy", stats.isHealthy() ? 1L : 0L);
		appendGauge(output, "mqtt_cluster_storage_file_size_bytes", stats.getFileSizeBytes());
		appendGauge(output, "mqtt_cluster_storage_entries", stats.getEntryCount());
		appendCounter(output, "mqtt_cluster_storage_read_operations_total", stats.getReadOperations());
		appendCounter(output, "mqtt_cluster_storage_write_operations_total", stats.getWriteOperations());
		appendGauge(output, "mqtt_cluster_storage_startup_duration_millis",
			storage == null ? 0L : storage.getStartupDurationMillis());
		long inflightCount = storage != null && storage.getInflightStore() != null
			? storage.getInflightStore().count() : 0L;
		appendGauge(output, "mqtt_cluster_inflight_entries", inflightCount);
		long inflightExpired = storage != null && storage.getInflightCleaner() != null
			? storage.getInflightCleaner().getRemovedExpiredCount() : 0L;
		appendCounter(output, "mqtt_cluster_inflight_expired_total", inflightExpired);
		return output.toString();
	}

	private static void appendGauge(StringBuilder output, String name, long value) {
		output.append("# TYPE ").append(name).append(" gauge\n");
		output.append(name).append(' ').append(value).append('\n');
	}

	private static void appendCounter(StringBuilder output, String name, long value) {
		output.append("# TYPE ").append(name).append(" counter\n");
		output.append(name).append(' ').append(value).append('\n');
	}

	private static class PendingTakeover {
		private final long attemptId;
		private final String previousOwnerNode;

		private PendingTakeover(long attemptId, String previousOwnerNode) {
			this.attemptId = attemptId;
			this.previousOwnerNode = previousOwnerNode;
		}
	}

	private static class TakeoverGrant {
		private final String requesterNode;
		private final long attemptId;
		private final long expiresAt;

		private TakeoverGrant(String requesterNode, long attemptId, long expiresAt) {
			this.requesterNode = requesterNode;
			this.attemptId = attemptId;
			this.expiresAt = expiresAt;
		}
	}

	private static class PendingRetainQuery {
		private final CountDownLatch responses;
		private final Set<String> respondedNodes = ConcurrentHashMap.newKeySet();
		private final List<Message> retained = Collections.synchronizedList(new ArrayList<>());

		private PendingRetainQuery(int expectedResponses) {
			this.responses = new CountDownLatch(expectedResponses);
		}

		private void accept(String nodeId, List<Message> messages) {
			if (!respondedNodes.add(nodeId)) {
				return;
			}
			if (messages != null) {
				retained.addAll(messages);
			}
			responses.countDown();
		}

		private boolean await(long timeoutMs) throws InterruptedException {
			return responses.await(timeoutMs, TimeUnit.MILLISECONDS);
		}

		private List<Message> messages() {
			synchronized (retained) {
				return new ArrayList<>(retained);
			}
		}
	}
}
