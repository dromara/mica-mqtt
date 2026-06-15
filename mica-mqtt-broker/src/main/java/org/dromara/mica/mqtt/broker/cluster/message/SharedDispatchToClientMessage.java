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
 * Cluster message that delivers a shared-subscription message to a specific client
 * on a target node.
 * <p>
 * This is the core message of the V2 dispatcher model (EMQX-style).  After the
 * publisher's node runs the configured {@link org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.SharedSubscriptionStrategy}
 * and selects exactly one subscriber from the group, it sends this message to the
 * node that hosts the selected client.  The receiving node delivers the message
 * locally to that client only, eliminating the duplicate delivery that occurred
 * with the V1 full-broadcast approach.
 * </p>
 * <p>
 * If the target client is no longer connected when this message arrives, the
 * receiving node performs a local re-pick from its own shared-subscription table
 * (which is a full replica due to V1 synchronization) rather than sending a NACK
 * back to the originating node.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessageType#SHARED_DISPATCH_TO_CLIENT
 * @since 1.0.0
 */
public class SharedDispatchToClientMessage implements ClusterMessage {

	/**
	 * The client identifier of the selected subscriber.
	 */
	private String clientId;

	/**
	 * The original published topic (not the shared-subscription topic filter).
	 */
	private String topic;

	/**
	 * The MQTT message payload to be delivered to the selected client.
	 */
	private Message message;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.SHARED_DISPATCH_TO_CLIENT;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
		headers.put(ClusterMessageSerializer.HEADER_TOPIC, topic);
	}

	@Override
	public byte[] toPayload() {
		if (message == null) {
			return new byte[0];
		}
		return DefaultMessageSerializer.INSTANCE.serialize(message);
	}

	@Override
	public void fromClusterData(ClusterDataMessage data) {
		this.clientId = data.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		this.topic = data.getHeader(ClusterMessageSerializer.HEADER_TOPIC);
		byte[] payload = data.getPayload();
		if (payload != null && payload.length > 0) {
			this.message = DefaultMessageSerializer.INSTANCE.deserialize(payload);
		}
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}
}
