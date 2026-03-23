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

import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
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

	private final MqttClusterManager clusterManager;

	public ClusterPublishHandler(MqttClusterManager clusterManager) {
		this.clusterManager = clusterManager;
	}

	@Override
	public boolean handle(PublishContext context) {
		if (!clusterManager.isClusterEnabled()) {
			return true;
		}

		String topic = context.getTopic();
		String localNodeId = clusterManager.getLocalNodeId();

		// Get all remote nodes that have subscribers for this topic
		Set<String> remoteNodes = clusterManager.getRemoteNodesWithSubscriber(topic);

		if (remoteNodes.isEmpty()) {
			logger.debug("[Cluster] No remote subscribers for topic: {}, skip forwarding", topic);
			return true;
		}

		logger.debug("[Cluster] Forwarding message on topic: {} to remote nodes: {}", topic, remoteNodes);
		for (String nodeId : remoteNodes) {
			forwardToNode(nodeId, context);
		}

		return true; // continue to local delivery
	}

	private void forwardToNode(String nodeId, PublishContext context) {
		logger.debug("[Cluster] Forwarding to node: {}, topic: {}", nodeId, context.getTopic());

		Message message = new Message();
		message.setMessageType(MessageType.UP_STREAM);
		message.setFromClientId(context.getClientId());
		message.setFromUsername(context.getUsername());
		message.setTopic(context.getTopic());
		message.setPayload(context.getPayload());
		message.setQos(context.getQos().value());
		message.setDup(context.isDup());
		message.setRetain(context.isRetain());
		message.setTimestamp(System.currentTimeMillis());

		PublishForwardMessage clusterMsg = new PublishForwardMessage();
		clusterMsg.setMessage(message);

		clusterManager.sendToNode(nodeId, clusterMsg);
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
