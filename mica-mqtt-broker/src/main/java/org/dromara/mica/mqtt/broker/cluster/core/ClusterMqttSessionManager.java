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

import org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.message.UnsubscribeNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.store.SessionStore;
import org.dromara.mica.mqtt.broker.cluster.store.SharedSubStore;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cluster-aware session manager that decorates an underlying {@link IMqttSessionManager}.
 * This class extends the session management capabilities to support MQTT broker clustering by:
 * <ul>
 *   <li>Tracking which cluster node each client is connected to</li>
 *   <li>Synchronizing subscription state across cluster nodes via broadcast messages</li>
 *   <li>Providing unified local + remote subscription lookup for message routing</li>
 *   <li>Coordinating session cleanup when nodes depart the cluster</li>
 *   <li>Persisting session state to V3 storage (P2.1) when wired in</li>
 * </ul>
 *
 * @author L.cm
 * @see IMqttSessionManager
 * @since 1.0.0
 */
public class ClusterMqttSessionManager implements IMqttSessionManager {
	private static final Logger logger = LoggerFactory.getLogger(ClusterMqttSessionManager.class);
	private final IMqttSessionManager delegate;
	private final MqttClusterManager clusterManager;
	private final ConcurrentHashMap<String, String> clientNodeMap = new ConcurrentHashMap<>();
	private volatile SessionStore sessionStore;
	private volatile SharedSubStore sharedSubStore;

	/**
	 * Constructs a cluster session manager wrapping the specified delegate.
	 *
	 * @param delegate the underlying session manager to which operations are delegated
	 * @param clusterManager the cluster manager for broadcasting synchronization messages
	 */
	public ClusterMqttSessionManager(IMqttSessionManager delegate, MqttClusterManager clusterManager) {
		this.delegate = delegate;
		this.clusterManager = clusterManager;
	}

	/**
	 * Returns the cluster node identifier where the specified client is connected.
	 *
	 * @param clientId the client identifier
	 * @return the node identifier, or null if the client is not registered as a remote client
	 */
	public String getClientNode(String clientId) {
		return clientNodeMap.get(clientId);
	}

	/**
	 * Registers a remote client as being connected to a specific cluster node.
	 *
	 * @param clientId the client identifier
	 * @param nodeId the identifier of the node where the client is connected
	 */
	public void registerRemoteClient(String clientId, String nodeId) {
		clientNodeMap.put(clientId, nodeId);
		logger.debug("[Cluster] Registered remote client: {} -> node: {}", clientId, nodeId);
	}

	/**
	 * Removes a remote client registration and cleans up its session.
	 *
	 * @param clientId the client identifier
	 */
	public void removeRemoteClient(String clientId) {
		String node = clientNodeMap.remove(clientId);
		delegate.remove(clientId);
		logger.debug("[Cluster] Removed remote client: {} from node: {}", clientId, node);
	}

	/**
	 * Clears all clients and their subscriptions associated with a departing node.
	 *
	 * @param nodeId the identifier of the node that has left the cluster
	 */
	public void clearNodeClientsAndSubscriptions(String nodeId) {
		clientNodeMap.entrySet().removeIf(entry -> {
			if (entry.getValue().equals(nodeId)) {
				delegate.remove(entry.getKey());
				return true;
			}
			return false;
		});
	}

	/**
	 * Synchronizes remote subscriptions from a client connected to another node.
	 * <p>
	 * V1 全量复制方案：所有远程订阅都添加到本地 TrieTopicManager，
	 * 保证各节点订阅状态完全一致，searchAllSubscribe 可查到所有订阅者。
	 * </p>
	 *
	 * @param clientId the client identifier
	 * @param nodeId the identifier of the node where the client is connected
	 * @param subscriptions the list of subscriptions to synchronize
	 */
	public void syncRemoteSubscriptions(String clientId, String nodeId, List<Subscribe> subscriptions) {
		registerRemoteClient(clientId, nodeId);
		for (Subscribe sub : subscriptions) {
			delegate.addSubscribe(new TopicFilter(sub.getTopicFilter()), clientId, sub.getMqttQoS(), sub.isNoLocal());
			logger.debug("[Cluster] Synced remote subscription: client={}, topic={}, node={}",
				clientId, sub.getTopicFilter(), nodeId);
		}
	}

	/**
	 * Removes synchronized subscriptions for a remote client.
	 *
	 * @param clientId the client identifier
	 * @param topics the list of topics to unsubscribe from
	 */
	public void removeRemoteSubscriptions(String clientId, List<String> topics) {
		for (String topic : topics) {
			delegate.removeSubscribe(topic, clientId);
			logger.debug("[Cluster] Removed remote subscription: client={}, topic={}", clientId, topic);
		}
	}

	@Override
	public void addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal) {
		delegate.addSubscribe(topicFilter, clientId, mqttQoS, noLocal);

		if (clusterManager.isClusterEnabled()) {
			Subscribe subscribe = new Subscribe(topicFilter.getTopic(), clientId, mqttQoS, noLocal);
			SubscribeNotifyMessage notifyMessage = new SubscribeNotifyMessage();
			notifyMessage.setClientId(clientId);
			notifyMessage.setNodeId(clusterManager.getLocalNodeId());
			notifyMessage.setSubscriptions(Collections.singletonList(subscribe));

			logger.debug("[Cluster] Broadcasting subscription: client={}, topic={}, node={}",
				clientId, topicFilter.getTopic(), clusterManager.getLocalNodeId());

			clusterManager.broadcast(notifyMessage);
		}

		// V3 shared-subscription persistence (P2.2): add this member to the
		// persistent group snapshot so a backup can recover membership.
		updateSharedGroupOnSubscribe(topicFilter.getTopic(), clientId);

		// V3 session persistence (P2.1): record the new subscription state so a
		// future takeover can recover it.
		persistSession(clientId);
	}

	@Override
	public void removeSubscribe(String topicFilter, String clientId) {
		delegate.removeSubscribe(topicFilter, clientId);

		if (clusterManager.isClusterEnabled()) {
			UnsubscribeNotifyMessage notifyMessage = new UnsubscribeNotifyMessage();
			notifyMessage.setClientId(clientId);
			notifyMessage.setNodeId(clusterManager.getLocalNodeId());
			notifyMessage.setTopics(Collections.singletonList(topicFilter));
			clusterManager.broadcast(notifyMessage);
		}

		// V3 shared-subscription persistence: drop this member from the group.
		updateSharedGroupOnUnsubscribe(topicFilter, clientId);

		// V3 session persistence: refresh the persisted session so the latest
		// subscription set is on disk.
		persistSession(clientId);
	}

	/**
	 * Updates the persistent shared-subscription group when a new member joins.
	 */
	private void updateSharedGroupOnSubscribe(String topicFilter, String clientId) {
		if (sharedSubStore == null) {
			return;
		}
		if (topicFilter == null) {
			return;
		}
		String groupName = extractGroupName(topicFilter);
		if (groupName == null) {
			return;
		}
		String underlyingTopic = extractUnderlyingTopic(topicFilter);
		SharedSubStore.SharedSubGroup current = sharedSubStore.get(groupName);
		long version = current == null ? 0L : current.getVersion();
		java.util.List<String> members = new java.util.ArrayList<>();
		if (current != null && current.getMembers() != null) {
			members.addAll(current.getMembers());
		}
		if (!members.contains(clientId)) {
			members.add(clientId);
		}
		SharedSubStore.SharedSubGroup updated = new SharedSubStore.SharedSubGroup(
			groupName, underlyingTopic, members,
			current == null ? clusterManager.getLocalNodeId() : current.getOwnerNodeId(),
			current == null ? null : current.getBackupNodeId(),
			version + 1, System.currentTimeMillis());
		sharedSubStore.save(updated);
	}

	/**
	 * Updates the persistent shared-subscription group when a member leaves.
	 * If the group becomes empty, the group record is deleted.
	 */
	private void updateSharedGroupOnUnsubscribe(String topicFilter, String clientId) {
		if (sharedSubStore == null) {
			return;
		}
		if (topicFilter == null) {
			return;
		}
		String groupName = extractGroupName(topicFilter);
		if (groupName == null) {
			return;
		}
		SharedSubStore.SharedSubGroup current = sharedSubStore.get(groupName);
		if (current == null || current.getMembers() == null || !current.getMembers().contains(clientId)) {
			return;
		}
		java.util.List<String> members = new java.util.ArrayList<>(current.getMembers());
		members.remove(clientId);
		if (members.isEmpty()) {
			sharedSubStore.delete(groupName);
		} else {
			SharedSubStore.SharedSubGroup updated = new SharedSubStore.SharedSubGroup(
				groupName, current.getTopicFilter(), members,
				current.getOwnerNodeId(), current.getBackupNodeId(),
				current.getVersion() + 1, System.currentTimeMillis());
			sharedSubStore.save(updated);
		}
	}

	private static String extractGroupName(String topicFilter) {
		if (topicFilter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
			return TopicFilterType.getShareGroupName(topicFilter);
		}
		if (topicFilter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
			return "$queue";
		}
		return null;
	}

	private static String extractUnderlyingTopic(String topicFilter) {
		// $share/<group>/<topic>  ->  <topic>
		// $queue/<topic>          ->  <topic>
		if (topicFilter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
			String withoutPrefix = topicFilter.substring(TopicFilterType.SHARE_GROUP_PREFIX.length());
			int slash = withoutPrefix.indexOf('/');
			if (slash < 0) {
				return withoutPrefix;
			}
			return withoutPrefix.substring(slash + 1);
		}
		if (topicFilter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
			return topicFilter.substring(TopicFilterType.SHARE_QUEUE_PREFIX.length());
		}
		return topicFilter;
	}

	/**
	 * Persists the current local subscriptions of a client to the V3 session store.
	 * <p>
	 * No-op when storage is disabled or when the client is registered as remote
	 * (a remote client's subscriptions live on its owning node, not here).
	 * </p>
	 */
	private void persistSession(String clientId) {
		if (sessionStore == null) {
			return;
		}
		String ownerNode = clientNodeMap.get(clientId);
		if (ownerNode != null) {
			// Remote client — do not persist; its state lives on the owning node.
			return;
		}
		List<Subscribe> subs = delegate.getSubscriptions(clientId);
		SessionStore.Session session = new SessionStore.Session(
			clientId, subs, false, 0L, clusterManager.getLocalNodeId());
		sessionStore.save(clientId, session);
	}

	/**
	 * Wires the V3 session store.  When set, subscription add/remove operations
	 * refresh the persistent session snapshot for the affected client.
	 *
	 * @param sessionStore the session store; may be {@code null} to disable
	 */
	public void setSessionStore(SessionStore sessionStore) {
		this.sessionStore = sessionStore;
	}

	/**
	 * Wires the V3 shared-subscription store.  When set, subscribe/unsubscribe
	 * operations update the persistent group membership.
	 *
	 * @param sharedSubStore the shared-sub store; may be {@code null} to disable
	 */
	public void setSharedSubStore(SharedSubStore sharedSubStore) {
		this.sharedSubStore = sharedSubStore;
	}

	/**
	 * Returns the live clientId→node mapping.  Used by the cluster manager to
	 * find the previous owner of a session during takeover.
	 */
	public ConcurrentHashMap<String, String> getClientNodeMap() {
		return clientNodeMap;
	}

	/**
	 * Removes the local subscriptions of a client.  Called by the cluster
	 * manager when this node loses ownership of a session (handed off to a
	 * peer via the takeover protocol).
	 */
	public void clearLocalSubscription(String clientId) {
		if (clientId == null) {
			return;
		}
		List<Subscribe> subs = delegate.getSubscriptions(clientId);
		if (subs != null) {
			for (Subscribe s : subs) {
				delegate.removeSubscribe(s.getTopicFilter(), clientId);
			}
		}
	}

	@Override
	public Byte searchSubscribe(String topicName, String clientId) {
		return delegate.searchSubscribe(topicName, clientId);
	}

	@Override
	public List<Subscribe> searchSubscribe(String topic) {
		List<Subscribe> allSubscribers = delegate.searchSubscribe(topic);
		if (allSubscribers == null || allSubscribers.isEmpty()) {
			return allSubscribers;
		}
		String localNodeId = clusterManager.getLocalNodeId();
		List<Subscribe> localSubscribers = new ArrayList<>(allSubscribers.size());
		for (Subscribe sub : allSubscribers) {
			String node = clientNodeMap.get(sub.getClientId());
			if (node == null || node.equals(localNodeId)) {
				localSubscribers.add(sub);
			}
		}
		return localSubscribers;
	}

	/**
	 * Returns all subscriptions for a topic, including those from remote cluster nodes.
	 * <p>
	 * Unlike {@link #searchSubscribe(String)} which filters to local subscriptions only,
	 * this method returns the complete set of subscribers across the entire cluster.
	 * </p>
	 *
	 * @param topic the topic to search subscriptions for
	 * @return the list of all subscribers, or null if no subscriptions exist
	 */
	public List<Subscribe> searchAllSubscribe(String topic) {
		return delegate.searchSubscribe(topic);
	}

	/**
	 * Returns the subscriptions for a specific client from the underlying session manager.
	 *
	 * @param clientId the client identifier
	 * @return the list of subscriptions for the client, or null if none exist
	 */
	public List<Subscribe> getClientSubscriptions(String clientId) {
		return delegate.getSubscriptions(clientId);
	}

	/**
	 * Returns the complete mapping of remote clients to their cluster nodes.
	 * <p>
	 * This map is used during state synchronization when a new node joins the cluster.
	 * </p>
	 *
	 * @return a copy of the client-to-node mapping
	 */
	public Map<String, String> getRemoteClientNodeMap() {
		return new HashMap<>(clientNodeMap);
	}

	/**
	 * Performs a full state synchronization from a joining node.
	 * <p>
	 * This populates the remote client registry and recreates all subscriptions
	 * from the synchronized state data.
	 * </p>
	 *
	 * @param clientNodeMap the client-to-node mapping to synchronize
	 * @param subscriptionMap the client-to-subscriptions mapping to synchronize
	 */
	public void syncFullState(Map<String, String> clientNodeMap, Map<String, List<Subscribe>> subscriptionMap) {
		this.clientNodeMap.putAll(clientNodeMap);
		for (Map.Entry<String, List<Subscribe>> entry : subscriptionMap.entrySet()) {
			for (Subscribe sub : entry.getValue()) {
				delegate.addSubscribe(new TopicFilter(sub.getTopicFilter()), entry.getKey(), sub.getMqttQoS(), sub.isNoLocal());
			}
		}
	}

	@Override
	public List<Subscribe> getSubscriptions(String clientId) {
		return delegate.getSubscriptions(clientId);
	}

	@Override
	public void addPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish) {
		delegate.addPendingPublish(clientId, messageId, pendingPublish);
	}

	@Override
	public MqttPendingPublish getPendingPublish(String clientId, int messageId) {
		return delegate.getPendingPublish(clientId, messageId);
	}

	@Override
	public void removePendingPublish(String clientId, int messageId) {
		delegate.removePendingPublish(clientId, messageId);
	}

	@Override
	public void addPendingQos2Publish(String clientId, int messageId, MqttPendingQos2Publish pendingQos2Publish) {
		delegate.addPendingQos2Publish(clientId, messageId, pendingQos2Publish);
	}

	@Override
	public MqttPendingQos2Publish getPendingQos2Publish(String clientId, int messageId) {
		return delegate.getPendingQos2Publish(clientId, messageId);
	}

	@Override
	public void removePendingQos2Publish(String clientId, int messageId) {
		delegate.removePendingQos2Publish(clientId, messageId);
	}

	@Override
	public int getPacketId(String clientId) {
		return delegate.getPacketId(clientId);
	}

	@Override
	public boolean hasSession(String clientId) {
		return delegate.hasSession(clientId) || clientNodeMap.containsKey(clientId);
	}

	@Override
	public boolean expire(String clientId, int sessionExpirySeconds) {
		return delegate.expire(clientId, sessionExpirySeconds);
	}

	@Override
	public boolean active(String clientId) {
		return delegate.active(clientId);
	}

	@Override
	public void remove(String clientId) {
		clientNodeMap.remove(clientId);
		delegate.remove(clientId);
		// V3 session persistence: clear the persistent record on disconnect.
		if (sessionStore != null) {
			sessionStore.delete(clientId);
		}
	}

	@Override
	public void clean() {
		delegate.clean();
		clientNodeMap.clear();
	}
}
