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

import java.util.Map;

/**
 * Cluster message contract for inter-node communication within an MQTT broker cluster.
 * <p>
 * This interface defines the serialization contract for cluster messages that need to be
 * transmitted between broker nodes via the underlying t-io cluster framework.
 * Each message type implements this interface to provide its specific serialization logic.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessageType
 * @see ClusterMessageSerializer
 * @since 1.0.0
 */
public interface ClusterMessage {

	/**
	 * Returns the message type identifier for this cluster message.
	 *
	 * @return the {@link ClusterMessageType} enum value identifying this message
	 */
	ClusterMessageType getType();

	/**
	 * Serializes message-specific data into the cluster message headers.
	 * <p>
	 * Subclasses should override this method to populate any headers required
	 * for message routing or identification in the cluster.
	 * </p>
	 *
	 * @param headers the mutable map of headers to populate with message data
	 */
	void toClusterData(Map<String, String> headers);

	/**
	 * Serializes the message payload for network transmission.
	 *
	 * @return the serialized byte array of the message payload, never null
	 */
	byte[] toPayload();

	/**
	 * Deserializes this message from a t-io cluster data message.
	 *
	 * @param message the source {@link ClusterDataMessage} to deserialize from
	 */
	void fromClusterData(ClusterDataMessage message);
}