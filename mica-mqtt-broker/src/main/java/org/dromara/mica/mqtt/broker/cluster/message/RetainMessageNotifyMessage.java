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
 * Notice for retained message synchronization across cluster nodes.
 * <p>
 * When a retained message is published or cleared, this notice is broadcast to all nodes
 * so they can maintain a consistent retained message store across the cluster.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType#RETAIN_MESSAGE
 * @since 1.0.0
 */
public class RetainMessageNotifyMessage implements ClusterMessage {
	/**
	 * The topic for which the retained message applies.
	 */
	private String topic;
	/**
	 * Timeout in seconds after which the retained message expires. Zero means no expiration.
	 */
	private int timeout;
	/**
	 * The retained message payload, or null if this notice represents a clear operation.
	 */
	private Message retainMessage;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.RETAIN_MESSAGE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_TOPIC, topic);
		headers.put(ClusterMessageSerializer.HEADER_TIMEOUT, String.valueOf(timeout));
	}

	@Override
	public byte[] toPayload() {
		if (retainMessage == null) {
			return new byte[0];
		}
		return DefaultMessageSerializer.INSTANCE.serialize(retainMessage);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.topic = message.getHeader(ClusterMessageSerializer.HEADER_TOPIC);
		String timeoutStr = message.getHeader(ClusterMessageSerializer.HEADER_TIMEOUT);
		this.timeout = timeoutStr != null ? Integer.parseInt(timeoutStr) : 0;
		byte[] payload = message.getPayload();
		if (payload != null && payload.length > 0) {
			this.retainMessage = DefaultMessageSerializer.INSTANCE.deserialize(payload);
		}
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public Message getRetainMessage() {
		return retainMessage;
	}

	public void setRetainMessage(Message retainMessage) {
		this.retainMessage = retainMessage;
	}
}
