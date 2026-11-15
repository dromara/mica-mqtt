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

import java.util.Map;

/**
 * Notice broadcast when a cluster node departs or becomes unreachable.
 * <p>
 * When a node shuts down gracefully or is detected as unavailable, this notice is broadcast
 * to all remaining nodes so they can clean up all client sessions and subscriptions
 * associated with the departing node.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType#NODE_LEAVE
 * @since 1.0.0
 */
public class NodeLeaveMessage implements ClusterMessage {
	/**
	 * 实际离开集群的节点标识。
	 * <p>
	 * 正常下线时由离开节点广播，该值为空，接收方回退到消息来源节点；
	 * 故障检测时由存活节点代播，必须显式携带真正的失效节点，否则接收方
	 * 会把「检测者」当成离开者，清理错对象。
	 */
	private String nodeId;
	/**
	 * 是否为故障检测触发的下线。
	 * <p>
	 * 正常下线时离开节点还有机会自行投递遗嘱；只有故障下线才需要由存活节点
	 * 代发备份遗嘱，避免正常停机时重复发布。
	 */
	private boolean failover;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.NODE_LEAVE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		if (nodeId != null && !nodeId.isEmpty()) {
			headers.put(ClusterMessageSerializer.HEADER_NODE_ID, nodeId);
		}
		if (failover) {
			headers.put(ClusterMessageSerializer.HEADER_FAILOVER, Boolean.TRUE.toString());
		}
	}

	@Override
	public byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.nodeId = message.getHeader(ClusterMessageSerializer.HEADER_NODE_ID);
		this.failover = Boolean.parseBoolean(message.getHeader(ClusterMessageSerializer.HEADER_FAILOVER));
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public boolean isFailover() {
		return failover;
	}

	public void setFailover(boolean failover) {
		this.failover = failover;
	}
}
