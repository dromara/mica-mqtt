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
import org.dromara.mica.mqtt.broker.cluster.message.SharedDispatchToClientMessage;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.SharedSubscriptionStrategy;
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.message.BaseMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V2 cluster message dispatcher — routes MQTT PUBLISH messages across cluster nodes.
 * <p>
 * This handler sits in the message pipeline (order = 90) before the local
 * {@code UpStreamMessageHandler} (order = 100) and is responsible for cross-node
 * delivery of messages published on this node.
 * </p>
 *
 * <h2>Normal subscriptions (non-shared)</h2>
 * <p>
 * The V1 full-replica strategy is preserved: every node holds a complete copy of
 * the subscription table.  When a remote node has subscribers for a topic, the
 * publisher's node sends one {@link PublishForwardMessage} <em>per remote node</em>
 * (not per subscriber), and each remote node performs local delivery to its own
 * subscribers.
 * </p>
 *
 * <h2>Shared subscriptions ({@code $share/&lt;group&gt;/…} and {@code $queue/…})</h2>
 * <p>
 * V1 broadcast causes duplicate delivery: every node that has a subscriber in the
 * group receives the message and delivers it locally, so all subscribers get the
 * message instead of just one.
 * </p>
 * <p>
 * V2 (this class) uses the <strong>EMQX dispatcher model</strong> to fix this:
 * </p>
 * <ol>
 *   <li>All candidates for a group are gathered (local + remote, full replica).</li>
 *   <li>The configured {@link SharedSubscriptionStrategy} picks exactly <em>one</em>
 *       subscriber.</li>
 *   <li>If the selected subscriber is local, it is delivered directly without any
 *       cluster message.</li>
 *   <li>If the selected subscriber is remote, a single
 *       {@link SharedDispatchToClientMessage} is sent to that subscriber's node.
 *       The remote node delivers to the specific client only.</li>
 * </ol>
 * <p>
 * This eliminates duplicate delivery while still using the full-replica table
 * (no per-group owner node, no single point of failure).
 * </p>
 *
 * @author L.cm
 * @see BaseMessageHandler
 * @see SharedSubscriptionStrategy
 * @since 2.6.0
 */
public class ClusterMessageDispatcher extends BaseMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(ClusterMessageDispatcher.class);

	private final MqttClusterManager clusterManager;
	private final ClusterMqttSessionManager clusterSessionManager;
	private final SharedSubscriptionStrategy sharedStrategy;

	/**
	 * Constructs a dispatcher that uses the supplied strategy for shared subscriptions.
	 *
	 * @param mqttServer the embedded MQTT server
	 * @param clusterManager the cluster manager for inter-node messaging
	 * @param clusterSessionManager the cluster session manager for subscriber and node lookup
	 * @param sharedStrategy the strategy to select one subscriber per shared-subscription group
	 */
	public ClusterMessageDispatcher(MqttServer mqttServer,
									MqttClusterManager clusterManager,
									ClusterMqttSessionManager clusterSessionManager,
									SharedSubscriptionStrategy sharedStrategy) {
		super(mqttServer);
		this.clusterManager = clusterManager;
		this.clusterSessionManager = clusterSessionManager;
		this.sharedStrategy = sharedStrategy;
	}

	@Override
	public MessageType[] messageTypes() {
		return new MessageType[]{MessageType.UP_STREAM};
	}

	@Override
	public boolean handle(Message message) {
		String topic = message.getTopic();
		String localNodeId = clusterManager.getLocalNodeId();

		List<Subscribe> allSubscribers = clusterSessionManager.searchAllSubscribe(topic);

		logger.debug("[Cluster] Publish on topic: {}, total subscriber count: {}", topic,
			allSubscribers == null ? 0 : allSubscribers.size());

		if (allSubscribers == null || allSubscribers.isEmpty()) {
			return true;
		}

		// Split subscribers into normal vs shared subscriptions.
		List<Subscribe> normalSubs = new ArrayList<>();
		// group name -> candidate list for shared subscriptions
		Map<String, List<Subscribe>> sharedGroups = new HashMap<>();

		for (Subscribe sub : allSubscribers) {
			String topicFilter = sub.getTopicFilter();
			if (topicFilter == null) {
				normalSubs.add(sub);
				continue;
			}
			if (topicFilter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
				// $share/<group>/<topic>
				String groupName = TopicFilterType.getShareGroupName(topicFilter);
				sharedGroups.computeIfAbsent(groupName, k -> new ArrayList<>()).add(sub);
			} else if (topicFilter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
				// $queue/<topic> — treated as a single unnamed group
				sharedGroups.computeIfAbsent("$queue", k -> new ArrayList<>()).add(sub);
			} else {
				normalSubs.add(sub);
			}
		}

		// Deliver normal subscriptions via the existing V1 per-node broadcast.
		deliverNormal(topic, message, localNodeId, normalSubs);

		// Deliver shared subscriptions via V2 single-pick dispatcher.
		deliverShared(topic, message, localNodeId, sharedGroups);

		return true;
	}

	/**
	 * Normal (non-shared) subscription delivery.
	 * <p>
	 * Groups remote subscribers by node and sends one {@link PublishForwardMessage}
	 * per distinct remote node.  Local delivery is handled by the downstream pipeline
	 * handler and is not triggered here.
	 * </p>
	 */
	private void deliverNormal(String topic, Message message, String localNodeId, List<Subscribe> normalSubs) {
		if (normalSubs.isEmpty()) {
			return;
		}
		Set<String> remoteNodes = new HashSet<>();
		for (Subscribe sub : normalSubs) {
			String nodeId = clusterSessionManager.getClientNode(sub.getClientId());
			if (nodeId != null && !nodeId.equals(localNodeId)) {
				remoteNodes.add(nodeId);
			}
		}
		for (String nodeId : remoteNodes) {
			logger.debug("[Cluster] Forwarding normal publish on topic: {} to remote node: {}", topic, nodeId);
			PublishForwardMessage forward = new PublishForwardMessage();
			forward.setMessage(message);
			clusterManager.sendToNode(nodeId, forward);
		}
	}

	/**
	 * Shared subscription delivery using the dispatcher model.
	 * <p>
	 * For each group, exactly one subscriber is selected by the configured strategy.
	 * If the selected subscriber is local it is not explicitly re-delivered here (the
	 * downstream local handler takes care of it).  If it is remote, a
	 * {@link SharedDispatchToClientMessage} is sent to the target node only.
	 * </p>
	 */
	private void deliverShared(String topic, Message message, String localNodeId,
								Map<String, List<Subscribe>> sharedGroups) {
		if (sharedGroups.isEmpty()) {
			return;
		}
		for (Map.Entry<String, List<Subscribe>> entry : sharedGroups.entrySet()) {
			String groupName = entry.getKey();
			List<Subscribe> candidates = entry.getValue();

			if (candidates.isEmpty()) {
				continue;
			}

			Subscribe picked = sharedStrategy.pick(groupName, candidates, localNodeId, message);
			if (picked == null) {
				logger.debug("[Cluster] Shared group {} has no active subscriber, dropping message on topic: {}",
					groupName, topic);
				continue;
			}

			String targetNodeId = clusterSessionManager.getClientNode(picked.getClientId());
			boolean isLocal = targetNodeId == null || targetNodeId.equals(localNodeId);

			if (isLocal) {
				// Local delivery — let the downstream pipeline handle it as usual.
				logger.debug("[Cluster] Shared dispatch group={} client={} -> local delivery on topic: {}",
					groupName, picked.getClientId(), topic);
			} else {
				// Remote delivery — send exactly one SharedDispatchToClientMessage.
				logger.debug("[Cluster] Shared dispatch group={} client={} -> remote node: {} topic: {}",
					groupName, picked.getClientId(), targetNodeId, topic);
				SharedDispatchToClientMessage dispatch = new SharedDispatchToClientMessage();
				dispatch.setClientId(picked.getClientId());
				dispatch.setTopic(topic);
				dispatch.setMessage(message);
				clusterManager.sendToNode(targetNodeId, dispatch);
			}
		}
	}

	@Override
	public int getOrder() {
		return 90;
	}
}
