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
 * V3 cluster message: a session has been permanently deleted.
 * <p>
 * Triggered when a client disconnects with {@code cleanSession=true} or when
 * a session expiry (MQTT 5.0 {@code session-expiry-interval}) elapses.  All
 * cluster nodes remove their copies of the session state so that a future
 * CONNECT for the same {@code clientId} starts a fresh session.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessageType#SESSION_DELETE_NOTIFY
 * @since 2.6.0
 */
public class SessionDeleteNotifyMessage implements ClusterMessage {

	private String clientId;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.SESSION_DELETE_NOTIFY;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
	}

	@Override
	public byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
}