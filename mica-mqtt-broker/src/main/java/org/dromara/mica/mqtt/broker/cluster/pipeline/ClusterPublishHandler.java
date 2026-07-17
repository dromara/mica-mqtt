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
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline handler for cross-node message forwarding during publish operations.
 * <p>
 * This handler implements {@link MqttPublishPipelineHandler} and intercepts
 * published messages in the pipeline. If subscribers exist on remote cluster
 * nodes, it forwards the message to those nodes for local delivery.
 * </p>
 *
 * @author L.cm
 * @since 1.0.0
 */
public class ClusterPublishHandler implements MqttPublishPipelineHandler {
	private static final Logger logger = LoggerFactory.getLogger(ClusterPublishHandler.class);

	private final MqttServer mqttServer;
	private final MqttClusterManager clusterManager;
	private final ClusterMqttSessionManager sessionManager;
	private final SharedSubscriptionStrategy sharedStrategy;

	public ClusterPublishHandler(MqttServer mqttServer, MqttClusterManager clusterManager,
								 ClusterMqttSessionManager sessionManager,
								 SharedSubscriptionStrategy sharedStrategy) {
		this.mqttServer = mqttServer;
		this.clusterManager = clusterManager;
		this.sessionManager = sessionManager;
		this.sharedStrategy = sharedStrategy;
	}

	@Override
	public boolean handle(PublishContext context) {
		if (!clusterManager.isClusterEnabled()) {
			return true;
		}

		List<Subscribe> allSubscribers = sessionManager.searchAllSubscribe(context.getTopic());
		if (allSubscribers.isEmpty()) {
			return true;
		}

		List<Subscribe> normalSubscribers = new ArrayList<>();
		Map<String, List<Subscribe>> sharedGroups = new HashMap<>();
		for (Subscribe subscribe : allSubscribers) {
			TopicFilter filter = new TopicFilter(subscribe.getTopicFilter());
			if ((filter.isShared() || filter.isQueue()) && subscribe.isNoLocal()
				&& subscribe.getClientId().equals(context.getClientId())) {
				continue;
			}
			if (filter.isShared()) {
				sharedGroups.computeIfAbsent(filter.getShareGroupName(), key -> new ArrayList<>()).add(subscribe);
			} else if (filter.isQueue()) {
				sharedGroups.computeIfAbsent("$queue", key -> new ArrayList<>()).add(subscribe);
			} else {
				normalSubscribers.add(subscribe);
			}
		}

		dispatchNormal(context, normalSubscribers);
		dispatchShared(context, sharedGroups);

		return true; // continue to local delivery
	}

	private void dispatchNormal(PublishContext context, List<Subscribe> subscribers) {
		Set<String> remoteNodes = new HashSet<>();
		for (Subscribe subscribe : subscribers) {
			String nodeId = sessionManager.getClientNode(subscribe.getClientId());
			if (nodeId != null && !nodeId.equals(clusterManager.getLocalNodeId())) {
				remoteNodes.add(nodeId);
			}
		}
		for (String nodeId : remoteNodes) {
			forwardToNode(nodeId, context);
			clusterManager.getMetrics().publishForwardSentInc();
		}
	}

	private void dispatchShared(PublishContext context, Map<String, List<Subscribe>> sharedGroups) {
		Message message = buildMessage(context);
		for (Map.Entry<String, List<Subscribe>> entry : sharedGroups.entrySet()) {
			List<Subscribe> remaining = new ArrayList<>(entry.getValue());
			boolean delivered = false;
			while (!remaining.isEmpty()) {
				Subscribe picked = sharedStrategy.pick(entry.getKey(), remaining,
					clusterManager.getLocalNodeId(), message);
				if (picked == null) {
					break;
				}
				String nodeId = sessionManager.getClientNode(picked.getClientId());
				if (nodeId == null || nodeId.equals(clusterManager.getLocalNodeId())) {
					mqttServer.publish(picked.getClientId(), context.getTopic(), context.getPayload(),
						context.getQos(), context.isRetain(), context.getProperties());
					delivered = true;
					break;
				}
				SharedDispatchToClientMessage dispatch = new SharedDispatchToClientMessage();
				dispatch.setClientId(picked.getClientId());
				dispatch.setTopic(context.getTopic());
				dispatch.setMessage(message);
				if (clusterManager.sendToNode(nodeId, dispatch)) {
					clusterManager.getMetrics().sharedDispatchSentInc();
					delivered = true;
					break;
				}
				// The transport rejected the send before enqueueing it. Remove this
				// subscriber and re-pick; no blind retry means no duplicate delivery.
				remaining.removeIf(candidate -> picked.getClientId().equals(candidate.getClientId()));
			}
			if (!delivered) {
				clusterManager.getMetrics().sharedDispatchDroppedInc();
			}
		}
	}

	private void forwardToNode(String nodeId, PublishContext context) {
		logger.debug("[Cluster] Forwarding to node: {}, topic: {}", nodeId, context.getTopic());

		PublishForwardMessage clusterMsg = new PublishForwardMessage();
		clusterMsg.setMessage(buildMessage(context));

		clusterManager.sendToNode(nodeId, clusterMsg);
	}

	private static Message buildMessage(PublishContext context) {
		Message message = new Message();
		message.setMessageType(MessageType.UP_STREAM);
		message.setFromClientId(context.getClientId());
		message.setFromUsername(context.getUsername());
		message.setTopic(context.getTopic());
		message.setPayload(context.getPayload());
		message.setQos(context.getQos().value());
		message.setDup(context.isDup());
		message.setRetain(context.isRetain());
		message.setProperties(context.getProperties());
		message.setTimestamp(context.getTimestamp());
		return message;
	}

	@Override
	public int getOrder() {
		return 50; // execute before RetainMessageHandler(100) for early cluster forwarding
	}

	@Override
	public boolean isCritical() {
		return false; // cluster forwarding failure does not affect local delivery
	}
}
