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
 * V3 cluster message: broadcast that a session has migrated to a new owner.
 * <p>
 * Sent by the new owner after it has successfully absorbed a session transferred
 * from a previous owner (typically via the
 * {@link SessionTakeoverRequestMessage}/{@link SessionTakeoverResponseMessage}
 * exchange).  All nodes update their client-to-node mapping for routing.
 * </p>
 * <p>
 * This message is the analog of {@link ClientConnectMessage} but for sessions
 * that crossed nodes during a takeover rather than a fresh connect.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessageType#SESSION_MIGRATED_NOTIFY
 * @since 2.6.0
 */
public class SessionMigratedNotifyMessage implements ClusterMessage {

	private String clientId;
	private String newOwnerNodeId;
	private String previousOwnerNodeId;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.SESSION_MIGRATED_NOTIFY;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
		headers.put(ClusterMessageSerializer.HEADER_NODE_ID, newOwnerNodeId);
		headers.put("previousNode", previousOwnerNodeId == null ? "" : previousOwnerNodeId);
	}

	@Override
	public byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		this.newOwnerNodeId = message.getHeader(ClusterMessageSerializer.HEADER_NODE_ID);
		String prev = message.getHeader("previousNode");
		this.previousOwnerNodeId = (prev == null || prev.isEmpty()) ? null : prev;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getNewOwnerNodeId() {
		return newOwnerNodeId;
	}

	public void setNewOwnerNodeId(String newOwnerNodeId) {
		this.newOwnerNodeId = newOwnerNodeId;
	}

	public String getPreviousOwnerNodeId() {
		return previousOwnerNodeId;
	}

	public void setPreviousOwnerNodeId(String previousOwnerNodeId) {
		this.previousOwnerNodeId = previousOwnerNodeId;
	}
}