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
 * This class is wired as the server's {@code publishAllRouter} and as the
 * collaborator of {@link ClusterPublishHandler}; it is <strong>not</strong>
 * registered in the message pipeline.  Normal (non-shared) publishes are
 * forwarded per remote node, shared subscriptions are dispatched to exactly one
 * selected subscriber.
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
 * @see SharedSubscriptionStrategy
 * @since 2.6.0
 */
public class ClusterMessageDispatcher {
	private static final Logger logger = LoggerFactory.getLogger(ClusterMessageDispatcher.class);

	private final MqttServer mqttServer;
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
		this.mqttServer = mqttServer;
		this.clusterManager = clusterManager;
		this.clusterSessionManager = clusterSessionManager;
		this.sharedStrategy = sharedStrategy;
	}

	/**
	 * Normal (non-shared) subscription delivery.
	 * <p>
	 * Groups remote subscribers by node and sends one {@link PublishForwardMessage}
	 * per distinct remote node.  Local delivery is handled by the caller.
	 * </p>
	 *
	 * @return 成功发送到的远端节点数
	 */
	private int deliverNormal(String topic, Message message, String localNodeId, List<Subscribe> normalSubs) {
		if (normalSubs.isEmpty()) {
			return 0;
		}
		Set<String> remoteNodes = new HashSet<>();
		for (Subscribe sub : normalSubs) {
			String nodeId = clusterSessionManager.getClientNode(sub.getClientId());
			if (nodeId != null && !nodeId.equals(localNodeId)) {
				remoteNodes.add(nodeId);
			}
		}
		int delivered = 0;
		for (String nodeId : remoteNodes) {
			logger.debug("[Cluster] Forwarding normal publish on topic: {} to remote node: {}", topic, nodeId);
			PublishForwardMessage forward = new PublishForwardMessage();
			forward.setMessage(message);
			if (clusterManager.sendToNode(nodeId, forward)) {
				delivered++;
			}
		}
		return delivered;
	}

	/**
	 * 为客户端发布消息选择本地订阅者，并向远端节点转发普通订阅和选中的共享订阅。
	 *
	 * @param context 发布上下文
	 * @return 本节点需要投递的订阅
	 */
	public List<Subscribe> selectLocalSubscribers(PublishContext context) {
		return selectLocalSubscribers(context, null);
	}

	/**
	 * 选择本地订阅者，并记录远端转发结果。
	 *
	 * @param context 发布上下文
	 * @param tracker 远端投递统计，可为 null
	 * @return 本节点需要投递的订阅
	 */
	private List<Subscribe> selectLocalSubscribers(PublishContext context, DeliveryTracker tracker) {
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
		int remoteDelivered = deliverNormal(topic, message, localNodeId, normal);
		int remoteTargets = countRemoteTargets(normal, localNodeId);
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
				remoteTargets++;
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
				if (clusterManager.sendToNode(node, dispatch)) {
					remoteDelivered++;
				}
			}
		}
		if (tracker != null) {
			tracker.remoteTargets += remoteTargets;
			tracker.remoteDelivered += remoteDelivered;
		}
		return local;
	}

	private int countRemoteTargets(List<Subscribe> normalSubs, String localNodeId) {
		int targets = 0;
		for (Subscribe sub : normalSubs) {
			String nodeId = clusterSessionManager.getClientNode(sub.getClientId());
			if (nodeId != null && !nodeId.equals(localNodeId)) {
				targets++;
			}
		}
		return targets;
	}

	/**
	 * 远端投递统计。
	 */
	private static final class DeliveryTracker {
		private int remoteTargets;
		private int remoteDelivered;
	}

	/**
	 * 路由服务端主动广播的消息，复用客户端发布时的共享订阅选择规则。
	 *
	 * @param context 发布上下文
	 * @return 投递结果
	 */
	public MqttServer.DeliveryResult routePublishAll(PublishContext context) {
		if (clusterSessionManager.searchAllSubscribe(context.getTopic()).isEmpty()) {
			return MqttServer.DeliveryResult.NO_SUBSCRIBERS;
		}
		DeliveryTracker tracker = new DeliveryTracker();
		List<Subscribe> localSubscribers = selectLocalSubscribers(context, tracker);
		boolean localDelivered = false;
		int localTargets = localSubscribers.size();
		for (Subscribe sub : localSubscribers) {
			ChannelContext channel = mqttServer.getChannelContext(sub.getClientId());
			boolean success = channel != null && !channel.isClosed()
				&& mqttServer.publish(channel, sub.getClientId(), context.getTopic(), context.getPayload(),
					context.getQos(), sub.getMqttQoS(), false, context.getProperties());
			if (!success) {
				// 共享订阅失败时在同一分组内重新选择，普通订阅没有补偿目标
				success = retryLocalSubscriber(context, sub, context.getProperties());
			}
			localDelivered |= success;
		}
		if (localDelivered || tracker.remoteDelivered > 0) {
			return MqttServer.DeliveryResult.DELIVERED;
		}
		// 有目标但全部发送失败才返回失败；属性过期等「没有可投递目标」的情况属于正常空操作
		return localTargets > 0 || tracker.remoteTargets > 0
			? MqttServer.DeliveryResult.FAILED : MqttServer.DeliveryResult.NO_SUBSCRIBERS;
	}

	/**
	 * 本地共享订阅者投递失败时，在同一分组内重新选择。
	 *
	 * @param context 发布上下文
	 * @param subscription 投递失败的订阅
	 * @param properties 转发属性
	 * @return 是否重新投递成功
	 */
	public boolean retryLocalSubscriber(PublishContext context, Subscribe subscription, MqttProperties properties) {
		String filter = subscription.getTopicFilter();
		String groupKey;
		if (filter != null && filter.startsWith(TopicFilterType.SHARE_GROUP_PREFIX)) {
			groupKey = TopicFilterType.SHARE_GROUP_PREFIX + TopicFilterType.getShareGroupName(filter);
		} else if (filter != null && filter.startsWith(TopicFilterType.SHARE_QUEUE_PREFIX)) {
			groupKey = TopicFilterType.SHARE_QUEUE_PREFIX;
		} else {
			return false;
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
		return clusterManager.retrySharedDispatch(dispatch);
	}

}

