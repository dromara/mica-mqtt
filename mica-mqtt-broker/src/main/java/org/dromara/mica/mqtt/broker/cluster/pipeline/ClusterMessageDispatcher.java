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

import net.dreamlu.mica.net.core.ChannelContext;

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
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.pipeline.handler.SubscriptionForwardHandler;
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

	/**
	 * 为客户端发布消息选择本地订阅者，并向远端节点转发普通订阅和选中的共享订阅。
	 *
	 * @param context 发布上下文
	 * @return 本节点需要投递的订阅
	 */
	public List<Subscribe> selectLocalSubscribers(PublishContext context) {
		String topic = context.getTopic();
		MqttProperties properties = context.getProperties();
		if (properties != null && !properties.isEmpty()) {
			properties = SubscriptionForwardHandler.rewriteProperties(context, properties);
			if (properties == null) {
				return new ArrayList<>();
			}
		}
		// 属性中的剩余有效期已扣除本地耗时，远端从转发时刻继续计算
		long forwardedAt = System.currentTimeMillis();
		String localNodeId = clusterManager.getLocalNodeId();
		List<Subscribe> local = new ArrayList<>();
		List<Subscribe> normal = new ArrayList<>();
		Map<String, List<Subscribe>> groups = new HashMap<>();
		for (Subscribe sub : clusterSessionManager.searchAllSubscribe(topic)) {
			if (sub.isNoLocal() && sub.getClientId().equals(context.getClientId())) {
				continue;
			}
			String filter = sub.getTopicFilter();
			String group = null;
			if (filter != null && filter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
				group = TopicFilterType.SHARE_GROUP_PREFIX + TopicFilterType.getShareGroupName(filter);
			} else if (filter != null && filter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
				group = TopicFilterType.SHARE_QUEUE_PREFIX;
			}
			if (group != null) {
				groups.computeIfAbsent(group, key -> new ArrayList<>()).add(sub);
			} else {
				normal.add(sub);
				String node = clusterSessionManager.getClientNode(sub.getClientId());
				if (node == null || node.equals(localNodeId)) {
					local.add(sub);
				}
			}
		}
		Message message = new Message();
		message.setMessageType(MessageType.UP_STREAM);
		message.setFromClientId(context.getClientId());
		message.setFromUsername(context.getUsername());
		message.setTopic(topic);
		message.setPayload(context.getPayload());
		message.setQos(context.getQos().value());
		message.setProperties(properties);
		message.setTimestamp(forwardedAt);
		message.setPublishReceivedAt(forwardedAt);
		deliverNormal(topic, message, localNodeId, normal);
		for (Map.Entry<String, List<Subscribe>> entry : groups.entrySet()) {
			String groupName = entry.getKey().equals(TopicFilterType.SHARE_QUEUE_PREFIX) ? "$queue"
				: entry.getKey().substring(TopicFilterType.SHARE_GROUP_PREFIX.length());
			Subscribe selected = sharedStrategy.pick(groupName, entry.getValue(), localNodeId, message);
			if (selected == null) {
				continue;
			}
			String node = clusterSessionManager.getClientNode(selected.getClientId());
			if (node == null || node.equals(localNodeId)) {
				local.add(selected);
			} else {
				Message selectedMessage = new Message();
				selectedMessage.setMessageType(MessageType.UP_STREAM);
				selectedMessage.setFromClientId(context.getClientId());
				selectedMessage.setTopic(topic);
				selectedMessage.setPayload(context.getPayload());
				selectedMessage.setQos(Math.min(context.getQos().value(), selected.getMqttQoS()));
				selectedMessage.setProperties(properties);
				selectedMessage.setTimestamp(forwardedAt);
				selectedMessage.setPublishReceivedAt(forwardedAt);
				SharedDispatchToClientMessage dispatch = new SharedDispatchToClientMessage();
				dispatch.setClientId(selected.getClientId());
				dispatch.setGroupKey(entry.getKey());
				dispatch.setTopic(topic);
				dispatch.setMessage(selectedMessage);
				clusterManager.sendToNode(node, dispatch);
			}
		}
		return local;
	}

	/**
	 * 路由服务端主动广播的消息，复用客户端发布时的共享订阅选择规则。
	 *
	 * @param context 发布上下文
	 * @return 是否存在匹配订阅
	 */
	public boolean routePublishAll(PublishContext context) {
		if (clusterSessionManager.searchAllSubscribe(context.getTopic()).isEmpty()) {
			return false;
		}
		for (Subscribe sub : selectLocalSubscribers(context)) {
			ChannelContext channel = mqttServer.getChannelContext(sub.getClientId());
			if (channel == null || channel.isClosed() || !mqttServer.publish(channel, sub.getClientId(), context.getTopic(), context.getPayload(),
				context.getQos(), sub.getMqttQoS(), false, context.getProperties())) {
				retryLocalSubscriber(context, sub, context.getProperties());
			}
		}
		return true;
	}

	/**
	 * 本地共享订阅者投递失败时，在同一分组内重新选择。
	 *
	 * @param context 发布上下文
	 * @param subscription 投递失败的订阅
	 * @param properties 转发属性
	 */
	public void retryLocalSubscriber(PublishContext context, Subscribe subscription, MqttProperties properties) {
		String filter = subscription.getTopicFilter();
		String groupKey;
		if (filter != null && filter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
			groupKey = TopicFilterType.SHARE_GROUP_PREFIX + TopicFilterType.getShareGroupName(filter);
		} else if (filter != null && filter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
			groupKey = TopicFilterType.SHARE_QUEUE_PREFIX;
		} else {
			return;
		}
		long now = System.currentTimeMillis();
		Message message = new Message();
		message.setMessageType(MessageType.UP_STREAM);
		message.setFromClientId(context.getClientId());
		message.setTopic(context.getTopic());
		message.setPayload(context.getPayload());
		message.setQos(context.getQos().value());
		message.setProperties(properties);
		message.setTimestamp(now);
		message.setPublishReceivedAt(now);
		SharedDispatchToClientMessage dispatch = new SharedDispatchToClientMessage();
		dispatch.setClientId(subscription.getClientId());
		dispatch.setGroupKey(groupKey);
		dispatch.setTopic(context.getTopic());
		dispatch.setMessage(message);
		clusterManager.retrySharedDispatch(dispatch);
	}

	@Override
	public boolean handle(Message message) {
		if (MessageType.UP_STREAM != message.getMessageType()) {
			return true;
		}

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

