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

import org.tio.server.cluster.message.ClusterDataMessage;

import java.util.List;
import java.util.Map;

/**
 * 取消订阅通知消息
 * <p>
 * 当客户端取消订阅时，本节点向集群广播此消息，
 * 通知其他节点同步移除该订阅信息。
 * </p>
 *
 * @author L.cm
 */
public class UnsubscribeNotifyMessage implements BrokerMessage {
	/**
	 * 客户端 ID
	 */
	private String clientId;
	/**
	 * 节点 ID
	 */
	private String nodeId;
	/**
	 * 取消订阅的主题列表
	 */
	private List<String> topics;

	@Override
	public BrokerMessageType getType() {
		return BrokerMessageType.UNSUBSCRIBE_NOTIFY;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(BrokerMessageConverter.HEADER_CLIENT_ID, clientId);
		headers.put(BrokerMessageConverter.HEADER_NODE_ID, nodeId);
	}

	@Override
	public byte[] toPayload() {
		return BrokerMessageConverter.serializeTopics(topics);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(BrokerMessageConverter.HEADER_CLIENT_ID);
		this.nodeId = message.getHeader(BrokerMessageConverter.HEADER_NODE_ID);
		this.topics = BrokerMessageConverter.deserializeTopics(message.getPayload());
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
