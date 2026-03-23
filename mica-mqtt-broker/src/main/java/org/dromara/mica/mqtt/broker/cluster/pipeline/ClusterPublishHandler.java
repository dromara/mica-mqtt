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
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager.RemoteNode;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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
	private final ClusterMqttSessionManager clusterSessionManager;

	public ClusterPublishHandler(MqttClusterManager clusterManager, ClusterMqttSessionManager clusterSessionManager) {
		this.clusterManager = clusterManager;
		this.clusterSessionManager = clusterSessionManager;
	}

	@Override
	public boolean handle(PublishContext context) {
		if (!clusterManager.isClusterEnabled()) {
			return true;
		}

		String topic = context.getTopic();
		String localNodeId = clusterManager.getLocalNodeId();

		// 获取本地订阅者（只有本地，TrieTopicManager 不存储远程订阅）
		List<Subscribe> localSubscribers = clusterSessionManager.searchLocalSubscribe(topic);

		logger.debug("[Cluster] Received publish on topic: {}, local subscribers count: {}", topic,
			localSubscribers == null ? 0 : localSubscribers.size());

		// 获取共享订阅 group -> 远程节点 映射
		Map<String, Set<RemoteNode>> groupNodesMap = clusterSessionManager.getSharedGroupNodes(topic);

		if ((localSubscribers == null || localSubscribers.isEmpty()) && groupNodesMap.isEmpty()) {
			logger.debug("[Cluster] No subscribers for topic: {}, skip forwarding", topic);
			return true;
		}

		// 对每个 group 按 round-robin 选择一个节点
		Map<String, RemoteNode> selectedNodes = new HashMap<>();
		for (Map.Entry<String, Set<RemoteNode>> entry : groupNodesMap.entrySet()) {
			String groupName = entry.getKey();
			Set<RemoteNode> nodes = entry.getValue();
			if (!nodes.isEmpty()) {
				RemoteNode selected = selectNodeForGroup(nodes);
				selectedNodes.put(groupName, selected);
			}
		}

		// 统计需要转发的远程节点
		Set<String> remoteNodesToForward = new HashSet<>();
		for (RemoteNode node : selectedNodes.values()) {
			if (!node.nodeId.equals(localNodeId)) {
				remoteNodesToForward.add(node.nodeId);
			}
		}

		// 向选中的远程节点转发消息
		if (!remoteNodesToForward.isEmpty()) {
			logger.debug("[Cluster] Forwarding message on topic: {} to remote nodes: {}", topic, remoteNodesToForward);
			for (String nodeId : remoteNodesToForward) {
				forwardToNode(nodeId, context);
			}
		} else {
			logger.debug("[Cluster] No remote nodes need forwarding for topic: {}", topic);
		}

		return true; // 继续执行 SubscriptionForwardHandler 进行本地分发
	}

	private RemoteNode selectNodeForGroup(Set<RemoteNode> nodes) {
		String[] nodeIds = nodes.stream().map(n -> n.nodeId).toArray(String[]::new);
		int index = ThreadLocalRandom.current().nextInt(nodeIds.length);
		final String selectedNodeId = nodeIds[index];
		return nodes.stream().filter(n -> n.nodeId.equals(selectedNodeId)).findFirst().orElse(nodes.iterator().next());
	}

	private void forwardToNode(String nodeId, PublishContext context) {
		logger.debug("[Cluster] Forwarding to node: {}, topic: {}", nodeId, context.getTopic());

		// 构建 Message 对象用于集群转发
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
		return 50; // 在 RetainMessageHandler(100) 之前执行，提前进行集群转发
	}

	@Override
	public boolean isCritical() {
		return false; // 集群转发失败不影响本地消息投递
	}
}
