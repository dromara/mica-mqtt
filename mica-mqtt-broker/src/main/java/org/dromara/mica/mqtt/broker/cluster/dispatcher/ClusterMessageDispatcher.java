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

package org.dromara.mica.mqtt.broker.cluster.dispatcher;

import org.dromara.mica.mqtt.broker.cluster.ClusterMqttSessionManager;
import org.dromara.mica.mqtt.broker.cluster.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.message.BaseMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClusterMessageDispatcher extends BaseMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(ClusterMessageDispatcher.class);
	private final MqttClusterManager clusterManager;
	private final ClusterMqttSessionManager clusterSessionManager;

	public ClusterMessageDispatcher(MqttServer mqttServer, MqttClusterManager clusterManager, ClusterMqttSessionManager clusterSessionManager) {
		super(mqttServer);
		this.clusterManager = clusterManager;
		this.clusterSessionManager = clusterSessionManager;
	}

	@Override
	public boolean handle(Message message) {
		if (MessageType.UP_STREAM != message.getMessageType()) {
			return true;
		}

		String topic = message.getTopic();
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
				forwardToNode(nodeId, message);
			}
		} else {
			logger.debug("[Cluster] No remote nodes need forwarding for topic: {}", topic);
		}

		return true; // 继续执行 UpStreamMessageHandler 进行本地分发
	}

	private void forwardToNode(String nodeId, Message msg) {
		logger.debug("[Cluster] Forwarding to node: {}, topic: {}", nodeId, msg.getTopic());

		PublishForwardMessage clusterMsg = new PublishForwardMessage();
		clusterMsg.setMessage(msg);

		clusterManager.sendToNode(nodeId, clusterMsg);
	}

	@Override
	public int getOrder() {
		return 90; // 在 UpStreamMessageHandler (100) 之前执行
	}
}
