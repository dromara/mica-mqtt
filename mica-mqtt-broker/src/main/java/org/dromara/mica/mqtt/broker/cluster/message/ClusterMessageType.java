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
	RETAIN_MESSAGE(10),

	// V2 Routing messages (11-13) — shared subscription dispatcher model

	/**
	 * Shared subscription dispatch to a specific client on a target node.
	 * <p>
	 * Sent by the publisher's node to the target node after the local dispatcher
	 * selects exactly one subscriber using the configured strategy. This eliminates
	 * duplicate delivery that occurs with V1 full broadcast for shared subscriptions.
	 * </p>
	 */
	SHARED_DISPATCH_TO_CLIENT(11),

	/**
	 * Shared subscription registration notification broadcast.
	 * <p>
	 * Sent when a client subscribes to a {@code $share/<group>/<topic>} or
	 * {@code $queue/<topic>} filter. Other nodes update their shared subscription
	 * tables to include this client as a candidate for future dispatches.
	 * </p>
	 */
	SHARED_SUBSCRIBE_NOTIFY(12),

	/**
	 * Shared subscription removal notification broadcast.
	 * <p>
	 * Sent when a client unsubscribes from a shared topic. Other nodes remove
	 * the corresponding entry from their shared subscription candidate lists.
	 * </p>
	 */
	SHARED_SUBSCRIBE_REMOVE(13),

	// V3 Storage messages (14-20) — H2 MVStore persistence layer

	/**
	 * Session takeover request sent from the new node to the previous owner node.
	 * <p>
	 * Triggered when a client reconnects to a different node (sticky session failure).
	 * The new node requests all persistent session state from the previous owner.
	 * </p>
	 */
	SESSION_TAKEOVER_REQUEST(14),

	/**
	 * Session takeover response carrying serialized session state.
	 * <p>
	 * Sent by the previous owner node in reply to {@link #SESSION_TAKEOVER_REQUEST}.
	 * Contains subscriptions, pending inflight messages, and session metadata.
	 * </p>
	 */
	SESSION_TAKEOVER_RESPONSE(15),

	/**
	 * Session migration complete notification broadcast to all cluster nodes.
	 * <p>
	 * Sent by the new owner node after successfully absorbing the transferred session.
	 * All nodes update their client-to-node mapping accordingly.
	 * </p>
	 */
	SESSION_MIGRATED_NOTIFY(16),

	/**
	 * Session deletion notification broadcast to all cluster nodes.
	 * <p>
	 * Sent when a session is permanently deleted (e.g., {@code cleanSession=true}
	 * disconnect). All nodes clean up references to the deleted session.
	 * </p>
	 */
	SESSION_DELETE_NOTIFY(17),

	/**
	 * Shared subscription state synchronization message between owner and backup nodes.
	 * <p>
	 * Used to replicate the authoritative shared subscription membership list from
	 * the owner node to its designated backup nodes for fault tolerance.
	 * </p>
	 */
	SHARED_SUB_STATE_SYNC(18),

	/**
	 * Shared subscription ownership takeover request.
	 * <p>
	 * Sent by a backup node when it detects that the owner node has left the cluster.
	 * The backup promotes itself to owner and broadcasts this message to establish
	 * the new ownership, eliminating the message vacuum during owner failover.
	 * </p>
	 */
	SHARED_SUB_TAKEOVER(19),

	/**
	 * Retain message query request sent to a remote shard owner.
	 * <p>
	 * Used in the optional retain sharding mode (V3, P2.5) to query retain messages
	 * stored on a different node. In the default non-sharded mode this message is
	 * not used and retain messages are replicated to all nodes.
	 * </p>
	 */
	RETAIN_QUERY(20),

	/** Application-level liveness probe used because mica-net keeps seed members after disconnect. */
	HEARTBEAT(21);

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
