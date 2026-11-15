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
import org.dromara.mica.mqtt.broker.cluster.store.InflightStore;
import org.dromara.mica.mqtt.broker.cluster.store.SessionStore;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.pipeline.handler.SubscriptionForwardHandler;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
	private static final int WILL_DELIVERY_ATTEMPTS = 3;

	private ClusterApi cluster;
	private MqttServer mqttServer;
	private final MqttClusterConfig config;
	private final String localNodeId;
	private SharedSubscriptionStrategy sharedStrategy;
	private ClusterStorage clusterStorage;
	private ClusterMqttSessionManager sessionManager;
	private final ClusterMetrics metrics = new ClusterMetrics();
	private final AtomicBoolean stopped = new AtomicBoolean();
	/**
	 * 最近一次观察到节点在线的时间，用于故障检测（nodeId:host:port -> 时间戳）。
	 */
	private final Map<String, Long> nodeLastSeen = new ConcurrentHashMap<>();
	/**
	 * 节点存活监控是否已启动，避免重复 schedule。
	 */
	private final AtomicBoolean nodeMonitorStarted = new AtomicBoolean();
	/**
	 * 已调度故障遗嘱代发的下线节点，避免重复调度。
	 */
	private final Set<String> willFailoverScheduled = ConcurrentHashMap.newKeySet();

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
	 * Looks up the local channel of a client by its MQTT client id.
	 * <p>
	 * The lookup goes through the t-io business id, which the server binds to the
	 * {@code uniqueId} of the connection. With the default
	 * {@code DefaultMqttServerUniqueIdServiceImpl} that value is the MQTT client id
	 * itself, so the lookup resolves the client's own connection. A custom
	 * {@code IMqttServerUniqueIdService} that maps the client id to something else
	 * makes this lookup miss, and the displaced connection would then stay open.
	 * </p>
	 *
	 * @param clientId the MQTT client id
	 * @return the local channel context, or {@code null} when there is none
	 */
	private ChannelContext localClientContext(String clientId) {
		return mqttServer == null ? null : Tio.getByBsId(mqttServer.getServerConfig(), clientId);
	}

	boolean hasLocalClient(String clientId) {
		ChannelContext context = localClientContext(clientId);
		return context != null && context.isAccepted() && !context.isClosed() && !context.isRemoved();
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

		// 节点存活监控：t-io 的成员链接断开不会回调业务层，这里按配置的
		// heartbeatInterval/nodeTimeout 主动巡检，发现失联节点后清理其客户端
		// 并代发备份遗嘱。
		startNodeMonitor();
		startWillTombstoneCleaner();
	}

	/**
	 * 周期性清理遗嘱墓碑，保证只接收通知的副本节点在长时间空闲时也能回收。
	 */
	private void startWillTombstoneCleaner() {
		if (mqttServer == null) {
			return;
		}
		IMqttMessageStore messageStore = getMessageStore();
		if (!(messageStore instanceof ClusterMqttMessageStore)) {
			return;
		}
		ClusterApi currentCluster = this.cluster;
		if (currentCluster == null) {
			return;
		}
		currentCluster.schedule(() -> {
			try {
				((ClusterMqttMessageStore) messageStore).pruneWillVersions();
			} catch (Throwable e) {
				logger.error("Failed to prune will version tombstones", e);
			}
		}, TimeUnit.MINUTES.toMillis(1));
	}

	private void startNodeMonitor() {
		if (!nodeMonitorStarted.compareAndSet(false, true)) {
			return;
		}
		long interval = Math.max(1000L, config.getHeartbeatInterval());
		cluster.schedule(() -> {
			try {
				checkNodeLiveness();
			} catch (Throwable e) {
				logger.error("Failed to check cluster node liveness", e);
			}
		}, interval);
	}

	/**
	 * 巡检在线节点：节点从「见过在线」变为「持续不可见」超过 nodeTimeout 后，
	 * 视为故障下线，清理其客户端/订阅并触发备份遗嘱代发。
	 */
	void checkNodeLiveness() {
		ClusterApi currentCluster = this.cluster;
		if (currentCluster == null || stopped.get()) {
			return;
		}
		long now = System.currentTimeMillis();
		Set<String> online = new HashSet<>();
		for (Node node : currentCluster.getOnlineMembers()) {
			String nodeId = node.getPeerHost();
			online.add(nodeId);
			nodeLastSeen.put(nodeId, now);
		}
		long timeout = Math.max(config.getNodeTimeout(), config.getHeartbeatInterval());
		for (Map.Entry<String, Long> entry : nodeLastSeen.entrySet()) {
			String nodeId = entry.getKey();
			if (online.contains(nodeId) || now - entry.getValue() <= timeout) {
				continue;
			}
			if (nodeLastSeen.remove(nodeId, entry.getValue())) {
				logger.warn("Cluster node {} unreachable for {}ms, treating as left", nodeId, now - entry.getValue());
				handleNodeLeave(nodeId, true);
				// 广播出去，让尚未完成检测的节点也清理该节点状态并代发备份遗嘱
				NodeLeaveMessage leaveMessage = new NodeLeaveMessage();
				leaveMessage.setNodeId(nodeId);
				leaveMessage.setFailover(true);
				broadcast(leaveMessage);
			}
		}
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
				deliverForwardedNormal(fwdMsg, sessionManager);
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
				ChannelContext previous = localClientContext(ccm.getClientId());
				sessionManager.registerRemoteClient(ccm.getClientId(), sourceNode);
				if (previous != null && previous.isAccepted() && !previous.isClosed()) {
					// Publish ownership before closing: the old socket's cleanup must not
					// remove the new route or broadcast a disconnect for the replacement.
					Tio.remove(previous, "MQTT session taken over by cluster node " + sourceNode,
						ChannelContext.CloseCode.KICK_EACH_OTHER);
					// 旧连接被踢时服务端会跳过 session 清理（避免误删刚订阅的 topic），
					// 这里只清掉本地连接态；订阅副本要留着，它正是本节点把该 clientId
					// 的消息转发给新 owner 的依据。
					sessionManager.removeLocalSessionState(ccm.getClientId());
				}
				break;
			}
			case CLIENT_DISCONNECT: {
				ClientDisconnectMessage cdm = (ClientDisconnectMessage) clusterMsg;
				sessionManager.removeRemoteClient(cdm.getClientId(), sourceNode);
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
				NodeLeaveMessage leaveMessage = (NodeLeaveMessage) clusterMsg;
				// 正常下线时 nodeId 为空，以消息来源为准；故障代播时显式携带真正失效的节点
				String leavingNode = leaveMessage.getNodeId();
				if (leavingNode == null || leavingNode.isEmpty()) {
					leavingNode = sourceNode;
				}
				nodeLastSeen.remove(leavingNode);
				handleNodeLeave(leavingNode, leaveMessage.isFailover());
				logger.info("Node {} left cluster, cleaned up its clients and subscriptions", leavingNode);
				break;
			}
			case WILL_MESSAGE: {
				WillMessageNotifyMessage wmm = (WillMessageNotifyMessage) clusterMsg;
				IMqttMessageStore messageStore = mqttServer.getServerCreator().getMessageStore();
				// 使用 Local 方法直接存储到 delegate，避免通过 ClusterMqttMessageStore 再次广播
				if (messageStore instanceof ClusterMqttMessageStore) {
					((ClusterMqttMessageStore) messageStore).applyWillMessageLocal(wmm);
					logger.debug("[Cluster] Applied will notification for clientId: {} from node: {}", wmm.getClientId(), sourceNode);
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
	 * 远端节点只投递普通订阅；共享订阅由发布节点单独选择。
	 */
	private void deliverForwardedNormal(Message message, ClusterMqttSessionManager sessions) {
		MqttProperties properties = forwardedProperties(message);
		if (message.getProperties() != null && properties == null) {
			return;
		}
		for (Subscribe sub : sessions.searchAllSubscribe(message.getTopic())) {
			String filter = sub.getTopicFilter();
			if ((filter != null && (filter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)
				|| filter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)))
				|| (sub.isNoLocal() && sub.getClientId().equals(message.getFromClientId()))) {
				continue;
			}
			String node = sessions.getClientNode(sub.getClientId());
			if (node != null && !node.equals(localNodeId)) {
				continue;
			}
			ChannelContext context = mqttServer.getChannelContext(sub.getClientId());
			if (context != null && !context.isClosed()) {
				mqttServer.publish(context, sub.getClientId(), message.getTopic(), message.getPayload(),
					MqttQoS.valueOf(message.getQos()), sub.getMqttQoS(), false, properties);
			}
		}
	}

	/**
	 * 重写跨节点消息属性并检查消息是否过期。
	 *
	 * @param message 集群转发消息
	 * @return 可转发的属性；null 也可能表示消息已过期
	 */
	private MqttProperties forwardedProperties(Message message) {
		MqttProperties properties = message.getProperties();
		if (properties == null || properties.isEmpty()) {
			return properties;
		}
		PublishContext context = PublishContext.builder()
			.properties(properties)
			.timestamp(message.getTimestamp())
			.publishReceivedAt(message.getPublishReceivedAt())
			.topic(message.getTopic())
			.build();
		return SubscriptionForwardHandler.rewriteProperties(context, properties);
	}

	/**
	 * 重新处理本地共享订阅投递，失败时按原分组选择其他客户端。
	 *
	 * @param message 共享订阅投递消息
	 */
	public void retrySharedDispatch(SharedDispatchToClientMessage message) {
		handleSharedDispatchToClient(message, sessionManager);
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

		MqttProperties properties = forwardedProperties(msg);
		if (msg.getProperties() != null && properties == null) {
			return;
		}
		// Try to deliver to the selected client locally.
		ChannelContext target = mqttServer.getChannelContext(clientId);
		Byte subscriptionQos = sessionManager.searchSubscribe(topic, clientId);
		boolean delivered = target != null && !target.isClosed() && subscriptionQos != null
			&& mqttServer.publish(target, clientId, topic, msg.getPayload(), MqttQoS.valueOf(msg.getQos()),
				subscriptionQos, false, properties);

		if (delivered) {
			logger.debug("[Cluster] Shared dispatch delivered to client={} topic={}", clientId, topic);
			return;
		}

		// Target client is no longer local — re-pick from the local shared-subscription table.
		logger.warn("[Cluster] Shared dispatch target client={} not found on this node for topic={}, re-picking",
			clientId, topic);

		if (sharedStrategy == null || sdm.getGroupKey() == null || sdm.getRetryCount() >= 3) {
			logger.debug("[Cluster] Cannot re-pick shared subscriber, group={} retries={} topic={}",
				sdm.getGroupKey(), sdm.getRetryCount(), topic);
			return;
		}

		List<Subscribe> candidates = sessionManager.searchAllSubscribe(topic);
		if (candidates == null || candidates.isEmpty()) {
			logger.debug("[Cluster] No subscribers remain for topic={}, dropping", topic);
			return;
		}

		// Narrow to shared subscribers only, group the same way the dispatcher does.
		Map<String, List<Subscribe>> sharedGroups = new HashMap<>();
		for (Subscribe sub : candidates) {
			if (clientId.equals(sub.getClientId()) || (sub.isNoLocal() && sub.getClientId().equals(msg.getFromClientId()))) {
				continue;
			}
			String topicFilter = sub.getTopicFilter();
			if (topicFilter == null) {
				continue;
			}
			if (topicFilter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
				String groupName = TopicFilterType.SHARE_GROUP_PREFIX + TopicFilterType.getShareGroupName(topicFilter);
				sharedGroups.computeIfAbsent(groupName, k -> new ArrayList<>()).add(sub);
			} else if (topicFilter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
				sharedGroups.computeIfAbsent(TopicFilterType.SHARE_QUEUE_PREFIX, k -> new ArrayList<>()).add(sub);
			}
		}

		for (Map.Entry<String, List<Subscribe>> entry : sharedGroups.entrySet()) {
			if (!entry.getKey().equals(sdm.getGroupKey())) {
				continue;
			}
			String groupName = entry.getKey().equals(TopicFilterType.SHARE_QUEUE_PREFIX) ? "$queue"
				: entry.getKey().substring(TopicFilterType.SHARE_GROUP_PREFIX.length());
			Subscribe rePicked = sharedStrategy.pick(groupName, entry.getValue(), localNodeId, msg);
			if (rePicked == null) {
				continue;
			}
			String rePickedNode = sessionManager.getClientNode(rePicked.getClientId());
			boolean isLocal = rePickedNode == null || rePickedNode.equals(localNodeId);
			if (isLocal) {
				ChannelContext channel = mqttServer.getChannelContext(rePicked.getClientId());
				if (channel != null && !channel.isClosed() && mqttServer.publish(channel, rePicked.getClientId(), topic, msg.getPayload(),
					MqttQoS.valueOf(msg.getQos()), rePicked.getMqttQoS(), false, properties)) {
					logger.debug("[Cluster] Re-pick delivered to client={} topic={}", rePicked.getClientId(), topic);
				} else {
					SharedDispatchToClientMessage retry = new SharedDispatchToClientMessage();
					retry.setClientId(rePicked.getClientId());
					retry.setGroupKey(sdm.getGroupKey());
					retry.setRetryCount(sdm.getRetryCount() + 1);
					retry.setTopic(topic);
					retry.setMessage(msg);
					handleSharedDispatchToClient(retry, sessionManager);
				}
			} else {
				SharedDispatchToClientMessage retry = new SharedDispatchToClientMessage();
				retry.setClientId(rePicked.getClientId());
				retry.setGroupKey(sdm.getGroupKey());
				retry.setRetryCount(sdm.getRetryCount() + 1);
				retry.setTopic(topic);
				retry.setMessage(msg);
				sendToNode(rePickedNode, retry);
				logger.debug("[Cluster] Re-pick forwarded to node={} client={} topic={}", rePickedNode, rePicked.getClientId(), topic);
			}
		}
	}

	/**
	 * 处理节点下线：清理该节点的客户端/订阅，并在故障下线时按节点顺序延迟代发备份遗嘱。
	 * <p>
	 * 所有存活节点都会收到 NODE_LEAVE，并各自持有该节点客户端的备份遗嘱。为兼顾
	 * 「不重复」和「代发节点失败后可接手」，这里不做一次性的选主，而是按 nodeId
	 * 排序计算固定间隔的延迟：rank 0 最先发布并广播 clear，后续节点醒来时本地
	 * 遗嘱通常已被清除，直接跳过；若 rank 0 在发布前失败，rank 1 的定时任务
	 * 仍会执行并接手。网络分区导致成员视图不一致时退化为 at-least-once
	 * （可能重复发布）；重试有上限，全部失败时保留备份并记录错误，不承诺
	 * 无限期必达。这是无共识集群下的明确取舍。
	 * </p>
	 *
	 * @param leavingNode 实际离开集群的节点
	 * @param failover    是否为故障下线；正常下线时由离开节点自行投递遗嘱
	 */
	private void handleNodeLeave(String leavingNode, boolean failover) {
		ClusterMqttSessionManager sessionManager = this.sessionManager != null
			? this.sessionManager
			: (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
		List<String> orphanedClients = sessionManager.clearNodeClientsAndSubscriptions(leavingNode);
		if (!failover || orphanedClients.isEmpty()) {
			return;
		}
		// 捕获当前的遗嘱对象：重试期间客户端可能重连并写入新遗嘱，
		// 只能继续处理这次下线对应的旧对象，不能把新遗嘱当成故障遗嘱发布。
		IMqttMessageStore messageStore = getMessageStore();
		Map<String, Message> wills = new LinkedHashMap<>();
		for (String clientId : orphanedClients) {
			Message willMessage = messageStore.getWillMessage(clientId);
			if (willMessage != null && willMessage.getTopic() != null) {
				wills.put(clientId, willMessage);
			}
		}
		if (!wills.isEmpty()) {
			scheduleFailoverWillPublish(leavingNode, wills);
		}
	}

	/**
	 * 按 rank 延迟调度故障遗嘱代发。rank 越小越早执行；更晚的节点作为接管者。
	 */
	private void scheduleFailoverWillPublish(String leavingNode, Map<String, Message> wills) {
		if (!willFailoverScheduled.add(leavingNode)) {
			return;
		}
		int rank = failoverRank(leavingNode);
		long step = Math.max(1000L, config.getHeartbeatInterval());
		long delay = rank * step;
		logger.info("Scheduled failover will publish for node {} rank {} delay {}ms", leavingNode, rank, delay);
		scheduleFailoverTask(() -> publishOrphanedWills(leavingNode, wills, 1), delay);
	}

	/**
	 * 计算本节点在剩余存活节点中的排序，用于错开遗嘱代发时间。
	 * 离开节点本身不参与比较。
	 *
	 * @param leavingNode 下线节点
	 * @return 从 0 开始的 rank
	 */
	int failoverRank(String leavingNode) {
		Set<String> members = new HashSet<>();
		ClusterApi currentCluster = this.cluster;
		if (currentCluster != null) {
			// 使用配置/发现到的成员（而非瞬时在线视图）计算 rank，减少视图抖动
			for (Node node : currentCluster.getRemoteMembers()) {
				members.add(node.getPeerHost());
			}
		}
		return failoverRank(localNodeId, leavingNode, members);
	}

	/**
	 * 按 nodeId 排序计算 rank。所有节点使用同一排序规则，视图一致时得到相同顺序。
	 *
	 * @param localNodeId 本节点
	 * @param leavingNode 下线节点
	 * @param onlineNodeIds 本节点看到的在线节点
	 * @return 从 0 开始的 rank
	 */
	static int failoverRank(String localNodeId, String leavingNode, Collection<String> onlineNodeIds) {
		Set<String> candidates = new TreeSet<>();
		candidates.add(localNodeId);
		if (onlineNodeIds != null) {
			for (String nodeId : onlineNodeIds) {
				if (nodeId != null && !nodeId.equals(leavingNode)) {
					candidates.add(nodeId);
				}
			}
		}
		int rank = 0;
		for (String nodeId : candidates) {
			if (nodeId.equals(localNodeId)) {
				return rank;
			}
			rank++;
		}
		return rank;
	}

	private void publishOrphanedWills(String leavingNode, Map<String, Message> wills, int attempt) {
		if (stopped.get()) {
			willFailoverScheduled.remove(leavingNode);
			return;
		}
		IMqttMessageStore messageStore = getMessageStore();
		Map<String, Message> failed = new LinkedHashMap<>();
		for (Map.Entry<String, Message> entry : wills.entrySet()) {
			String clientId = entry.getKey();
			Message willMessage = entry.getValue();
			// 已被其他节点发布/清除，或客户端已重连写入新遗嘱：本次下线不再处理
			if (messageStore.getWillMessage(clientId) != willMessage) {
				continue;
			}
			if (!publishFailoverWill(clientId, willMessage)) {
				failed.put(clientId, willMessage);
			}
		}
		if (!failed.isEmpty() && attempt < WILL_DELIVERY_ATTEMPTS) {
			scheduleFailoverTask(() -> publishOrphanedWills(leavingNode, failed, attempt + 1), 1000L);
			return;
		}
		if (!failed.isEmpty()) {
			logger.error("Failed to publish failover wills for clients:{} after {} attempts", failed.keySet(), attempt);
		}
		willFailoverScheduled.remove(leavingNode);
	}

	/**
	 * 发布一条故障遗嘱；走与正常遗嘱相同的 message pipeline，
	 * 只有确认投递成功（或没有订阅者这种正常空操作）时才清除备份。
	 *
	 * @param clientId 客户端标识
	 * @param willMessage 遗嘱
	 * @return 是否已成功处理
	 */
	private boolean publishFailoverWill(String clientId, Message willMessage) {
		String topic = willMessage.getTopic();
		try {
			willMessage.setMessageType(MessageType.DOWN_STREAM);
			// 与 MqttServerAioListener 的遗嘱路径保持一致：经过 message pipeline，
			// 后置 handler 的异常不会造成重复投递；投递 handler 抛异常则返回 false 重试。
			boolean delivered = mqttServer.getServerCreator().getMessagePipeline().handle(willMessage);
			if (!delivered) {
				logger.warn("Failover will delivery failed, will retry clientId:{} topic:{}", clientId, topic);
				return false;
			}
			getMessageStore().clearWillMessage(clientId, willMessage);
			logger.info("Published failover will for clientId:{} topic:{}", clientId, topic);
			return true;
		} catch (Throwable e) {
			logger.error("Failed to publish failover will for clientId:{} topic:{}", clientId, topic, e);
			return false;
		}
	}

	private IMqttMessageStore getMessageStore() {
		return mqttServer.getServerCreator().getMessageStore();
	}

	private void scheduleFailoverTask(Runnable task, long delay) {
		ClusterApi currentCluster = this.cluster;
		if (currentCluster == null) {
			task.run();
			return;
		}
		// 即使 rank 0 也交给时间轮执行，避免在集群消息处理线程里同步发布大量遗嘱
		currentCluster.scheduleOnce(task, Math.max(1L, delay));
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
		try {
			if (mqttServer != null) {
				mqttServer.stop();
			}
		} finally {
			// MqttServer.stop 会通过 stopListener 调用 stopCluster；若其内部提前抛异常
			// （如 executor shutdown 失败），这里兜底释放集群连接和持久化存储。
			stopCluster();
		}
	}

	/**
	 * 停止集群连接并关闭持久化存储。
	 */
	public void stopCluster() {
		if (!stopped.compareAndSet(false, true)) {
			return;
		}
		nodeLastSeen.clear();
		try {
			if (cluster != null) {
				NodeLeaveMessage leaveMsg = new NodeLeaveMessage();
				broadcast(leaveMsg);

				cluster.stop();
				logger.info("Mqtt cluster manager stopped");
			}
		} finally {
			if (clusterStorage != null) {
				clusterStorage.stop();
			}
		}
	}

	public void publish(String topic, byte[] payload, int qos, boolean retain) {
		if (mqttServer != null) {
			mqttServer.publishAll(topic, payload, MqttQoS.valueOf(qos), retain);
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
