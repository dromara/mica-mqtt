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

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.message.UnsubscribeNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.store.InflightStore;
import org.dromara.mica.mqtt.broker.cluster.store.SessionStore;
import org.dromara.mica.mqtt.broker.cluster.store.SharedSubStore;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttPubRelReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPubReplyMessageVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
	private final ConcurrentHashMap<String, ConcurrentHashMap<String, Subscribe>> routeSubscriptions = new ConcurrentHashMap<>();
	private volatile SessionStore sessionStore;
	private volatile SharedSubStore sharedSubStore;
	private volatile InflightStore inflightStore;
	private volatile long inflightTtlMs;

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
	 * Marks a client as locally owned. Local clients are represented by the
	 * absence of an entry in {@link #clientNodeMap}.
	 *
	 * @param clientId the client that connected to this node
	 */
	public void markLocalClient(String clientId) {
		clientNodeMap.remove(clientId);
	}

	/**
	 * Applies a session ownership change while preserving the fully replicated
	 * subscription routes on every node.
	 */
	public void applySessionMigration(String clientId, String newOwnerNodeId) {
		if (clientId == null || newOwnerNodeId == null) {
			return;
		}
		if (clusterManager.getLocalNodeId().equals(newOwnerNodeId)) {
			markLocalClient(clientId);
		} else {
			clientNodeMap.put(clientId, newOwnerNodeId);
		}
	}

	/**
	 * Removes a remote client registration and cleans up its session.
	 *
	 * @param clientId the client identifier
	 */
	public void removeRemoteClient(String clientId) {
		String node = clientNodeMap.remove(clientId);
		Map<String, Subscribe> removed = routeSubscriptions.remove(clientId);
		delegate.remove(clientId);
		removeSharedMembership(removed, clientId);
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
				Map<String, Subscribe> removed = routeSubscriptions.remove(entry.getKey());
				delegate.remove(entry.getKey());
				removeSharedMembership(removed, entry.getKey());
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
		if (subscriptions == null || subscriptions.isEmpty()) {
			return;
		}
		for (Subscribe sub : subscriptions) {
			delegate.addSubscribe(new TopicFilter(sub.getTopicFilter()), clientId, sub.getMqttQoS(), sub.isNoLocal(),
				sub.isRetainAsPublished(), sub.getRetainHandling(), sub.getSubscriptionId());
			Subscribe tracked = copySubscription(sub);
			tracked.setClientId(clientId);
			trackSubscription(tracked);
			updateSharedGroupOnSubscribe(sub.getTopicFilter(), clientId);
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
			removeTrackedSubscription(clientId, topic);
			updateSharedGroupOnUnsubscribe(topic, clientId);
			logger.debug("[Cluster] Removed remote subscription: client={}, topic={}", clientId, topic);
		}
	}

	@Override
	public boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal,
								boolean retainAsPublished, int retainHandling) {
		return addSubscribe(topicFilter, clientId, mqttQoS, noLocal, retainAsPublished, retainHandling, 0);
	}

	@Override
	public boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal,
								boolean retainAsPublished, int retainHandling, int subscriptionId) {
		boolean newSubscription = delegate.addSubscribe(topicFilter, clientId, mqttQoS, noLocal,
			retainAsPublished, retainHandling, subscriptionId);
		Subscribe subscribe = new Subscribe(topicFilter.getTopic(), clientId, mqttQoS, noLocal,
			retainAsPublished, retainHandling, subscriptionId);
		trackSubscription(subscribe);
		afterSubscribe(topicFilter, clientId, subscribe);
		return newSubscription;
	}

	private void afterSubscribe(TopicFilter topicFilter, String clientId, Subscribe subscribe) {
		if (clusterManager.isClusterEnabled()) {
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
		removeTrackedSubscription(clientId, topicFilter);

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
		while (true) {
			SharedSubStore.SharedSubGroup current = sharedSubStore.get(groupName, underlyingTopic);
			long version = current == null ? 0L : current.getVersion();
			java.util.List<String> members = new java.util.ArrayList<>();
			if (current != null && current.getMembers() != null) {
				members.addAll(current.getMembers());
			}
			if (!members.contains(clientId)) {
				members.add(clientId);
			}
			String[] owners = selectSharedGroupOwners(groupName, underlyingTopic, members);
			SharedSubStore.SharedSubGroup updated = new SharedSubStore.SharedSubGroup(
				groupName, underlyingTopic, members,
				owners[0], owners[1],
				version + 1, System.currentTimeMillis());
			if (sharedSubStore.updateIfVersion(updated, version)) {
				return;
			}
		}
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
		String underlyingTopic = extractUnderlyingTopic(topicFilter);
		while (true) {
			SharedSubStore.SharedSubGroup current = sharedSubStore.get(groupName, underlyingTopic);
			if (current == null || current.getMembers() == null || !current.getMembers().contains(clientId)) {
				return;
			}
			java.util.List<String> members = new java.util.ArrayList<>(current.getMembers());
			members.remove(clientId);
			if (members.isEmpty()) {
				if (sharedSubStore.deleteIfVersion(groupName, underlyingTopic, current.getVersion())) {
					return;
				}
			} else {
				String[] owners = selectSharedGroupOwners(groupName, underlyingTopic, members);
				SharedSubStore.SharedSubGroup updated = new SharedSubStore.SharedSubGroup(
					groupName, current.getTopicFilter(), members,
					owners[0], owners[1],
					current.getVersion() + 1, System.currentTimeMillis());
				if (sharedSubStore.updateIfVersion(updated, current.getVersion())) {
					return;
				}
			}
		}
	}

	private String[] selectSharedGroupOwners(String groupName, String topicFilter, List<String> members) {
		Set<String> nodes = new TreeSet<>();
		for (String member : members) {
			String node = clientNodeMap.get(member);
			nodes.add(node == null ? clusterManager.getLocalNodeId() : node);
		}
		if (nodes.isEmpty()) {
			return new String[]{null, null};
		}
		List<String> ordered = new ArrayList<>(nodes);
		int ownerIndex = Math.floorMod((groupName + '\u0000' + topicFilter).hashCode(), ordered.size());
		String owner = ordered.get(ownerIndex);
		String backup = ordered.size() > 1 ? ordered.get((ownerIndex + 1) % ordered.size()) : null;
		return new String[]{owner, backup};
	}

	private void removeSharedMembership(Map<String, Subscribe> subscriptions, String clientId) {
		if (subscriptions == null) {
			return;
		}
		for (Subscribe subscribe : subscriptions.values()) {
			updateSharedGroupOnUnsubscribe(subscribe.getTopicFilter(), clientId);
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
			clientId, subs, delegate.isCleanStart(clientId),
			delegate.getSessionExpiryInterval(clientId), clusterManager.getLocalNodeId());
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
		if (sessionStore == null) {
			return;
		}
		for (SessionStore.Session session : sessionStore.loadAll()) {
			// A clean session that survived only because the process crashed must not
			// become an offline session after restart.
			if (session.isCleanSession()) {
				sessionStore.delete(session.getClientId());
				continue;
			}
			restoreLocalSession(session);
		}
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

	public void setInflightStore(InflightStore inflightStore, long inflightTtlMs) {
		this.inflightStore = inflightStore;
		this.inflightTtlMs = inflightTtlMs;
	}

	/**
	 * Returns the live clientId→node mapping.  Used by the cluster manager to
	 * find the previous owner of a session during takeover.
	 *
	 * @return the live mapping of clientId to its owning node id
	 */
	public ConcurrentHashMap<String, String> getClientNodeMap() {
		return clientNodeMap;
	}

	/**
	 * Removes the local subscriptions of a client.  Called by the cluster
	 * manager when this node loses ownership of a session (handed off to a
	 * peer via the takeover protocol).
	 *
	 * @param clientId the client identifier whose local subscriptions should
	 *                 be removed; ignored when {@code null}
	 */
	public void clearLocalSubscription(String clientId) {
		if (clientId == null) {
			return;
		}
		List<Subscribe> subs = delegate.getSubscriptions(clientId);
		if (subs == null) {
			return;
		}
		for (Subscribe s : subs) {
			delegate.removeSubscribe(s.getTopicFilter(), clientId);
			// V3 shared-subscription persistence: remove this client from
			// persistent group membership so the strategy won't pick it.
			updateSharedGroupOnUnsubscribe(s.getTopicFilter(), clientId);
		}
		routeSubscriptions.remove(clientId);
	}

	/**
	 * Restores a persisted session into the live local subscription table after
	 * a successful cross-node takeover. This method deliberately avoids emitting
	 * subscribe broadcasts because peers already hold the full replicated route;
	 * the subsequent session-migrated notification only changes its owner node.
	 *
	 * @param session the persisted session returned by the previous owner
	 */
	public void restoreLocalSession(SessionStore.Session session) {
		if (session == null || session.getClientId() == null) {
			return;
		}
		markLocalClient(session.getClientId());
		delegate.setSessionExpiryInterval(session.getClientId(),
			(int) Math.min(Integer.MAX_VALUE, session.getSessionExpirySeconds()), session.isCleanSession());
		List<Subscribe> subscriptions = session.getSubscriptions();
		if (subscriptions == null) {
			return;
		}
		for (Subscribe subscribe : subscriptions) {
			delegate.addSubscribe(new TopicFilter(subscribe.getTopicFilter()), session.getClientId(),
				subscribe.getMqttQoS(), subscribe.isNoLocal(), subscribe.isRetainAsPublished(),
				subscribe.getRetainHandling(), subscribe.getSubscriptionId());
			Subscribe restored = copySubscription(subscribe);
			restored.setClientId(session.getClientId());
			trackSubscription(restored);
		}
	}

	@Override
	public Byte searchSubscribe(String topicName, String clientId) {
		return delegate.searchSubscribe(topicName, clientId);
	}

	@Override
	public List<Subscribe> searchSubscribe(String topic) {
		if (!clusterManager.isClusterEnabled()) {
			return delegate.searchSubscribe(topic);
		}
		List<Subscribe> matches = findMatchingSubscriptions(topic, false, false);
		Map<String, Subscribe> byClient = new LinkedHashMap<>();
		for (Subscribe sub : matches) {
			Subscribe current = byClient.get(sub.getClientId());
			if (current == null) {
				byClient.put(sub.getClientId(), copySubscription(sub));
			} else {
				mergeSubscription(current, sub);
			}
		}
		return new ArrayList<>(byClient.values());
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
		return findMatchingSubscriptions(topic, true, true);
	}

	private List<Subscribe> findMatchingSubscriptions(String topic, boolean includeRemote, boolean includeShared) {
		List<Subscribe> matches = new ArrayList<>();
		for (Map.Entry<String, ConcurrentHashMap<String, Subscribe>> entry : routeSubscriptions.entrySet()) {
			String clientId = entry.getKey();
			if (!includeRemote && clientNodeMap.containsKey(clientId)) {
				continue;
			}
			for (Subscribe sub : entry.getValue().values()) {
				TopicFilter filter = new TopicFilter(sub.getTopicFilter());
				if (!includeShared && (filter.isShared() || filter.isQueue())) {
					continue;
				}
				if (filter.match(topic)) {
					matches.add(copySubscription(sub));
				}
			}
		}
		return matches;
	}

	private void trackSubscription(Subscribe subscribe) {
		if (subscribe == null || subscribe.getClientId() == null || subscribe.getTopicFilter() == null) {
			return;
		}
		routeSubscriptions.computeIfAbsent(subscribe.getClientId(), key -> new ConcurrentHashMap<>())
			.put(subscribe.getTopicFilter(), copySubscription(subscribe));
	}

	private void removeTrackedSubscription(String clientId, String topicFilter) {
		ConcurrentHashMap<String, Subscribe> subscriptions = routeSubscriptions.get(clientId);
		if (subscriptions == null) {
			return;
		}
		subscriptions.remove(topicFilter);
		if (subscriptions.isEmpty()) {
			routeSubscriptions.remove(clientId, subscriptions);
		}
	}

	private static Subscribe copySubscription(Subscribe source) {
		return new Subscribe(source.getTopicFilter(), source.getClientId(), source.getMqttQoS(),
			source.isNoLocal(), source.isRetainAsPublished(), source.getRetainHandling(), source.getSubscriptionId());
	}

	private static void mergeSubscription(Subscribe target, Subscribe source) {
		target.setMqttQoS(Math.max(target.getMqttQoS(), source.getMqttQoS()));
		target.setNoLocal(target.isNoLocal() && source.isNoLocal());
		target.setRetainAsPublished(target.isRetainAsPublished() || source.isRetainAsPublished());
		if (target.getSubscriptionId() == 0) {
			target.setSubscriptionId(source.getSubscriptionId());
		}
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
		if (clientNodeMap == null) {
			return;
		}
		this.clientNodeMap.putAll(clientNodeMap);
		if (subscriptionMap == null) {
			return;
		}
		for (Map.Entry<String, List<Subscribe>> entry : subscriptionMap.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			for (Subscribe sub : entry.getValue()) {
				delegate.addSubscribe(new TopicFilter(sub.getTopicFilter()), entry.getKey(), sub.getMqttQoS(), sub.isNoLocal(),
					sub.isRetainAsPublished(), sub.getRetainHandling());
				Subscribe tracked = copySubscription(sub);
				tracked.setClientId(entry.getKey());
				trackSubscription(tracked);
				updateSharedGroupOnSubscribe(sub.getTopicFilter(), entry.getKey());
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
		if (inflightStore != null && pendingPublish != null && pendingPublish.getMessage() != null) {
			inflightStore.put(clientId, messageId, System.currentTimeMillis() + inflightTtlMs,
				pendingPublish.getMessage().variableHeader().topicName(), pendingPublish.getMessage().payload(),
				pendingPublish.getMessage().fixedHeader().qosLevel().value());
		}
	}

	@Override
	public MqttPendingPublish getPendingPublish(String clientId, int messageId) {
		return delegate.getPendingPublish(clientId, messageId);
	}

	@Override
	public void removePendingPublish(String clientId, int messageId) {
		delegate.removePendingPublish(clientId, messageId);
		if (inflightStore != null) {
			inflightStore.remove(clientId, messageId);
		}
	}

	@Override
	public void markPendingPublishPubRel(String clientId, int messageId) {
		delegate.markPendingPublishPubRel(clientId, messageId);
		if (inflightStore != null) {
			inflightStore.updatePhase(clientId, messageId, InflightStore.PHASE_PUBREL);
		}
	}

	/**
	 * Replays durable QoS 1/2 PUBLISH records after a client reconnects. The
	 * original packet identifier is preserved and DUP is set as required by MQTT.
	 *
	 * @param context the newly connected client channel
	 * @param clientId the reconnecting client identifier
	 * @param taskService timer used for subsequent retransmissions
	 * @return number of PUBLISH/PUBREL packets submitted to the channel
	 */
	public int replayInflight(ChannelContext context, String clientId, TimerTaskService taskService) {
		if (clientId == null || inflightStore == null) {
			return 0;
		}
		return replayInflight(context, clientId, inflightStore.listByClient(clientId), taskService);
	}

	/**
	 * Replays records supplied by a previous cluster owner during session takeover.
	 */
	public int replayInflight(ChannelContext context, String clientId,
						  List<InflightStore.InflightEntry> entries, TimerTaskService taskService) {
		if (context == null || context.isClosed() || clientId == null || taskService == null) {
			return 0;
		}
		List<InflightStore.InflightEntry> clientEntries = new ArrayList<>();
		if (entries != null) {
			for (InflightStore.InflightEntry entry : entries) {
				if (entry != null && clientId.equals(entry.getClientId())) {
					clientEntries.add(entry);
				}
			}
		}
		List<MqttMessage> messages = restoreInflight(clientEntries, System.currentTimeMillis());
		int replayed = 0;
		for (MqttMessage message : messages) {
			int packetId = getPacketId(message);
			MqttPendingPublish pending = delegate.getPendingPublish(clientId, packetId);
			if (pending == null) {
				continue;
			}
			if (message.fixedHeader().messageType() == MqttMessageType.PUBREL) {
				pending.startPubRelRetransmissionTimer(taskService, context);
			} else {
				pending.startPublishRetransmissionTimer(taskService, context);
			}
			if (Tio.send(context, message)) {
				replayed++;
			}
		}
		return replayed;
	}

	private static int getPacketId(MqttMessage message) {
		if (message.variableHeader() instanceof MqttPublishVariableHeader) {
			return ((MqttPublishVariableHeader) message.variableHeader()).packetId();
		}
		return ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
	}

	/**
	 * Rebuilds the in-memory pending table from durable records. Kept package
	 * visible so the packet-id, expiry and DUP semantics can be unit-tested
	 * without opening a network channel.
	 */
	List<MqttMessage> restoreInflight(List<InflightStore.InflightEntry> entries, long nowMs) {
		List<MqttMessage> messages = new ArrayList<>();
		if (entries == null) {
			return messages;
		}
		for (InflightStore.InflightEntry entry : entries) {
			if (entry == null || entry.getClientId() == null) {
				continue;
			}
			if (entry.getExpireAt() > 0 && entry.getExpireAt() <= nowMs) {
				if (inflightStore != null) {
					inflightStore.remove(entry.getClientId(), entry.getPacketId());
				}
				continue;
			}
			if (entry.getPacketId() < 1 || entry.getPacketId() > 65_535
				|| (entry.getQos() != MqttQoS.QOS1.value() && entry.getQos() != MqttQoS.QOS2.value())
				|| (entry.getPhase() != InflightStore.PHASE_PUBLISH && entry.getPhase() != InflightStore.PHASE_PUBREL)
				|| (entry.getPhase() == InflightStore.PHASE_PUBREL && entry.getQos() != MqttQoS.QOS2.value())
				|| entry.getTopic() == null || entry.getPayload() == null) {
				logger.warn("[Cluster] Ignoring invalid inflight record clientId={} packetId={} qos={}",
					entry.getClientId(), entry.getPacketId(), entry.getQos());
				continue;
			}
			MqttPendingPublish existing = delegate.getPendingPublish(entry.getClientId(), entry.getPacketId());
			if (existing != null) {
				existing.onPubAckReceived();
				existing.onPubCompReceived();
			}
			MqttQoS qos = MqttQoS.valueOf(entry.getQos());
			MqttPublishMessage message = MqttPublishMessage.builder()
				.topicName(entry.getTopic())
				.payload(entry.getPayload())
				.qos(qos)
				.isDup(true)
				.messageId(entry.getPacketId())
				.build();
			MqttPendingPublish pending = new MqttPendingPublish(message, qos);
			MqttMessage replayMessage = message;
			if (entry.getPhase() == InflightStore.PHASE_PUBREL) {
				MqttFixedHeader fixedHeader = new MqttFixedHeader(
					MqttMessageType.PUBREL, true, MqttQoS.QOS1, false, 0);
				MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(
					entry.getPacketId(), MqttPubRelReasonCode.SUCCESS.value(), MqttProperties.NO_PROPERTIES);
				replayMessage = new MqttMessage(fixedHeader, variableHeader);
				pending.setPubRelMessage(replayMessage);
			}
			delegate.addPendingPublish(entry.getClientId(), entry.getPacketId(), pending);
			if (inflightStore != null) {
				inflightStore.put(entry);
			}
			messages.add(replayMessage);
		}
		return messages;
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
	public void setClientReceiveMaximum(String clientId, int receiveMaximum) {
		delegate.setClientReceiveMaximum(clientId, receiveMaximum);
	}

	@Override
	public void setSessionExpiryInterval(String clientId, int sessionExpirySeconds, boolean cleanStart) {
		delegate.setSessionExpiryInterval(clientId, sessionExpirySeconds, cleanStart);
		persistSession(clientId);
	}

	@Override
	public int getSessionExpiryInterval(String clientId) {
		return delegate.getSessionExpiryInterval(clientId);
	}

	@Override
	public boolean isCleanStart(String clientId) {
		return delegate.isCleanStart(clientId);
	}

	@Override
	public int getClientReceiveMaximum(String clientId) {
		return delegate.getClientReceiveMaximum(clientId);
	}

	@Override
	public int getPendingPublishCount(String clientId) {
		return delegate.getPendingPublishCount(clientId);
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
		routeSubscriptions.remove(clientId);
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
		routeSubscriptions.clear();
	}
}
