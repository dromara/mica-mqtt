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

package org.dromara.mica.mqtt.broker.cluster.message;

import net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage;

import java.util.List;
import java.util.Map;

/**
 * Notice broadcast when a client unsubscribes from one or more topics.
 * <p>
 * When a client unsubscribes from topics, this node broadcasts this notice to all other nodes
 * so they can remove the corresponding entries from their subscription tables.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType#UNSUBSCRIBE_NOTIFY
 * @since 1.0.0
 */
public class UnsubscribeNotifyMessage implements ClusterMessage {
	/**
	 * Unique identifier of the unsubscribing MQTT client.
	 */
	private String clientId;
	/**
	 * Identifier of the node where the client is connected.
	 */
	private String nodeId;
	/**
	 * List of topics from which the client has unsubscribed.
	 */
	private List<String> topics;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.UNSUBSCRIBE_NOTIFY;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
		headers.put(ClusterMessageSerializer.HEADER_NODE_ID, nodeId);
	}

	@Override
	public byte[] toPayload() {
		return ClusterMessageSerializer.serializeTopics(topics);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		this.nodeId = message.getHeader(ClusterMessageSerializer.HEADER_NODE_ID);
		this.topics = ClusterMessageSerializer.deserializeTopics(message.getPayload());
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public List<String> getTopics() {
		return topics;
	}

	public void setTopics(List<String> topics) {
		this.topics = topics;
	}
}
