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
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.serializer.DefaultMessageSerializer;

import java.util.Map;

/**
 * Notice for will message synchronization across cluster nodes.
 * <p>
 * When a client sets or updates its will message, this notice is broadcast to all nodes
 * so they can maintain a backup. If the client disconnects unexpectedly, the will message
 * will be delivered from the backup stored on the node where the client was connected.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType#WILL_MESSAGE
 * @since 1.0.0
 */
public class WillMessageNotifyMessage implements ClusterMessage {
	/**
	 * Unique identifier of the MQTT client that owns the will message.
	 */
	private String clientId;
	/**
	 * The will message to be stored as a backup on remote nodes.
	 */
	private Message willMessage;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.WILL_MESSAGE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
	}

	@Override
	public byte[] toPayload() {
		if (willMessage == null) {
			return new byte[0];
		}
		return DefaultMessageSerializer.INSTANCE.serialize(willMessage);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		byte[] payload = message.getPayload();
		if (payload != null && payload.length > 0) {
			this.willMessage = DefaultMessageSerializer.INSTANCE.deserialize(payload);
		}
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public Message getWillMessage() {
		return willMessage;
	}

	public void setWillMessage(Message willMessage) {
		this.willMessage = willMessage;
	}
}
