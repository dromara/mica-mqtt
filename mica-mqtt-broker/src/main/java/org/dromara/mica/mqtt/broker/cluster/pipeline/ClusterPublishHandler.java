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
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 集群消息分发器 - 处理发布消息的跨节点转发
 * <p>
 * 实现 MqttPublishPipelineHandler 接口，在发布管线中拦截消息并转发给远程节点。
 * </p>
 *
 * @author L.cm
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

		// 获取所有订阅者（包括远程）
		List<Subscribe> subscribers = clusterSessionManager.searchAllSubscribe(topic);

		logger.debug("[Cluster] Received publish on topic: {}, subscribers count: {}", topic,
			subscribers == null ? 0 : subscribers.size());

		if (subscribers == null || subscribers.isEmpty()) {
			logger.debug("[Cluster] No subscribers for topic: {}, skip forwarding", topic);
			return true;
		}

		// 找出需要转发的远程节点
		Set<String> remoteNodes = new HashSet<>();
		for (Subscribe sub : subscribers) {
			String clientId = sub.getClientId();
			String node = clusterSessionManager.getClientNode(clientId);
			logger.debug("[Cluster] Client: {}, Node: {}", clientId, node);

			if (node != null && !node.equals(localNodeId)) {
				remoteNodes.add(node);
				logger.debug("[Cluster] Will forward to node: {} for client: {}", node, clientId);
			}
		}

		// 向每个远程节点转发消息（O(1) 网络开销）
		if (!remoteNodes.isEmpty()) {
			logger.debug("[Cluster] Forwarding message on topic: {} to remote nodes: {}", topic, remoteNodes);
			for (String nodeId : remoteNodes) {
				forwardToNode(nodeId, context);
			}
		} else {
			logger.debug("[Cluster] No remote nodes need forwarding for topic: {}", topic);
		}

		return true; // 继续执行 SubscriptionForwardHandler 进行本地分发
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
