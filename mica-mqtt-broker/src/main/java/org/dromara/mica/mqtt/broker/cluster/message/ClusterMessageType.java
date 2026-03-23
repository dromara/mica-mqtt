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

/**
 * Enumeration of cluster message types for inter-node communication.
 * <p>
 * Each enum constant represents a distinct message category used in MQTT broker cluster
 * synchronization, including client lifecycle events, subscription management, and message
 * forwarding operations between broker nodes.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @since 1.0.0
 */
public enum ClusterMessageType {
	/**
	 * Client connection notification broadcast.
	 * <p>
	 * Sent when a client successfully connects to a broker node. All other nodes
	 * update their client-to-node mapping for routing purposes.
	 * </p>
	 */
	CLIENT_CONNECT(1),

	/**
	 * Client disconnection notification broadcast.
	 * <p>
	 * Sent when a client disconnects from a broker node. All other nodes
	 * clean up their remote client registry for this client.
	 * </p>
	 */
	CLIENT_DISCONNECT(2),

	/**
	 * Subscription synchronization notification.
	 * <p>
	 * Sent when a client subscribes to a topic. Other nodes update their
	 * subscription tables to enable cross-node message forwarding.
	 * </p>
	 */
	SUBSCRIBE_NOTIFY(3),

	/**
	 * Unsubscription synchronization notification.
	 * <p>
	 * Sent when a client unsubscribes from a topic. Other nodes remove the
	 * corresponding subscription records.
	 * </p>
	 */
	UNSUBSCRIBE_NOTIFY(4),

	/**
	 * Cross-node message forwarding request.
	 * <p>
	 * Used when a publisher is on one node but subscribers exist on different nodes.
	 * The message payload is forwarded to nodes where subscribers are located.
	 * </p>
	 */
	PUBLISH_FORWARD(5),

	/**
	 * Node departure notification.
	 * <p>
	 * Broadcast when a node shuts down or becomes unreachable. All other nodes
	 * clean up clients and subscriptions associated with the departing node.
	 * </p>
	 */
	NODE_LEAVE(6),

	/**
	 * Full state synchronization request.
	 * <p>
	 * Initiated by a newly joined node to request complete state from existing nodes,
	 * including client-to-node mappings and subscription tables.
	 * </p>
	 *
	 * @see #STATE_SYNC_RESPONSE
	 */
	STATE_SYNC_REQUEST(7),

	/**
	 * Full state synchronization response.
	 * <p>
	 * Contains the complete state data (client mappings and subscriptions) sent
	 * in response to a state sync request from a joining node.
	 * </p>
	 *
	 * @see #STATE_SYNC_REQUEST
	 */
	STATE_SYNC_RESPONSE(8),

	/**
	 * Will message synchronization notification.
	 * <p>
	 * Broadcast when a client sets or updates its will message. Other nodes
	 * store a backup of the will message for delivery if the client disconnects unexpectedly.
	 * </p>
	 */
	WILL_MESSAGE(9),

	/**
	 * Retained message synchronization notification.
	 * <p>
	 * Broadcast when a retained message is published or cleared. All nodes
	 * update their retained message store to maintain consistency.
	 * </p>
	 */
	RETAIN_MESSAGE(10);

	private final int code;

	ClusterMessageType(int code) {
		this.code = code;
	}

	/**
	 * Returns the message type corresponding to the given numeric code.
	 *
	 * @param code the numeric code of the message type
	 * @return the corresponding {@link ClusterMessageType} enum constant
	 * @throws IllegalArgumentException if the code does not match any known message type
	 */
	public static ClusterMessageType fromCode(int code) {
		for (ClusterMessageType type : values()) {
			if (type.code == code) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown message type code: " + code);
	}

	/**
	 * Returns the numeric code for this message type.
	 *
	 * @return the integer code used in serialization
	 */
	public int getCode() {
		return code;
	}
}