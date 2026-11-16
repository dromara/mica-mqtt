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
 * Notice broadcast when a client successfully establishes a connection to a broker node.
 * <p>
 * Upon a client's successful connection, this node broadcasts this notice to all other nodes
 * in the cluster so they can update their client-to-node location mappings for routing
 * purposes.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType#CLIENT_CONNECT
 * @since 1.0.0
 */
public class ClientConnectMessage implements ClusterMessage {
	/**
	 * Unique identifier of the connected MQTT client.
	 */
	private String clientId;
	/**
	 * 新连接的清理屏障版本。仅当该连接未携带遗嘱时由服务端填写，
	 * 接收方据此压制迟到的旧遗嘱 add，避免为无遗嘱连接单独广播 WILL_MESSAGE。
	 */
	private long willVersion;
	/**
	 * 与清理屏障版本配对的标识。
	 */
	private String willToken;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.CLIENT_CONNECT;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
		if (willVersion > 0 && willToken != null) {
			headers.put(ClusterMessageSerializer.HEADER_WILL_VERSION, Long.toString(willVersion));
			headers.put(ClusterMessageSerializer.HEADER_WILL_TOKEN, willToken);
		}
	}

	@Override
	public byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		String version = message.getHeader(ClusterMessageSerializer.HEADER_WILL_VERSION);
		this.willVersion = version == null ? 0 : Long.parseLong(version);
		this.willToken = message.getHeader(ClusterMessageSerializer.HEADER_WILL_TOKEN);
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public long getWillVersion() {
		return willVersion;
	}

	public void setWillVersion(long willVersion) {
		this.willVersion = willVersion;
	}

	public String getWillToken() {
		return willToken;
	}

	public void setWillToken(String willToken) {
		this.willToken = willToken;
	}
}
