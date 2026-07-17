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

package org.dromara.mica.mqtt.broker.cluster.store;

import java.util.List;

/**
 * Store for QoS 1 and QoS 2 in-flight (unacknowledged) MQTT messages.
 * <p>
 * When a broker delivers a QoS 1/2 message to a client it adds an <em>inflight</em>
 * record to this store.  The record is removed when the client acknowledges delivery
 * (PUBACK for QoS 1, PUBCOMP for QoS 2).  On node restart or client reconnect, the
 * broker replays all inflight records to guarantee at-least-once delivery.
 * </p>
 *
 * <h2>TTL</h2>
 * <p>
 * Inflight messages carry an {@code expireAt} timestamp (milliseconds since epoch).
 * A background {@link InflightTtlCleaner} periodically sweeps expired records so that
 * sessions that never send ACKs (e.g. silent client crashes) do not accumulate unbounded
 * state.  The default TTL is 30 seconds (see {@link InflightTtlCleaner#DEFAULT_TTL_MS}).
 * </p>
 *
 * <h2>Key structure</h2>
 * <p>
 * Each record is stored under a composite key
 * {@code "inflight:<clientId>:<packetId>"} so that a prefix scan on
 * {@code "inflight:<clientId>:"} efficiently retrieves all pending messages for a
 * given client without a full table scan.
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 * @see H2InflightStore
 * @see InflightTtlCleaner
 */
public interface InflightStore {
	int PHASE_PUBLISH = 0;
	int PHASE_PUBREL = 1;

	/**
	 * Records an in-flight message for the given client.
	 *
	 * @param clientId  the MQTT client identifier; never {@code null}
	 * @param packetId  the MQTT packet identifier (1–65 535)
	 * @param expireAt  absolute expiry time in milliseconds since epoch
	 * @param topic     the published topic; never {@code null}
	 * @param payload   the message payload; may be empty but never {@code null}
	 * @param qos       the QoS level (1 or 2)
	 */
	void put(String clientId, int packetId, long expireAt, String topic, byte[] payload, int qos);

	/** Persists a transferred entry including its QoS delivery phase. */
	default void put(InflightEntry entry) {
		put(entry.getClientId(), entry.getPacketId(), entry.getExpireAt(),
			entry.getTopic(), entry.getPayload(), entry.getQos());
		if (entry.getPhase() != PHASE_PUBLISH) {
			updatePhase(entry.getClientId(), entry.getPacketId(), entry.getPhase());
		}
	}

	/** Updates an existing QoS 2 record after PUBREC moves it to PUBREL. */
	default void updatePhase(String clientId, int packetId, int phase) {
	}

	/**
	 * Removes the in-flight record for a specific client and packet.
	 * <p>
	 * Called when the client sends PUBACK (QoS 1) or PUBCOMP (QoS 2).
	 * </p>
	 *
	 * @param clientId  the client identifier
	 * @param packetId  the packet identifier to acknowledge
	 */
	void remove(String clientId, int packetId);

	/**
	 * Returns all pending in-flight records for the given client.
	 * <p>
	 * Used on client reconnect to replay unacknowledged messages in packet-id order.
	 * </p>
	 *
	 * @param clientId the client identifier
	 * @return ordered list of inflight entries; never {@code null}, may be empty
	 */
	List<InflightEntry> listByClient(String clientId);

	/**
	 * Removes all in-flight records whose {@code expireAt} timestamp is less than
	 * {@code nowMs}.
	 * <p>
	 * Called periodically by {@link InflightTtlCleaner}.
	 * </p>
	 *
	 * @param nowMs current time in milliseconds
	 * @return number of records removed
	 */
	int removeExpired(long nowMs);

	/**
	 * Returns the total number of in-flight records across all clients.
	 *
	 * @return total record count
	 */
	long count();

	// ---- Nested value type -------------------------------------------------------

	/**
	 * An immutable snapshot of a single inflight record.
	 */
	final class InflightEntry {
		private final String clientId;
		private final int packetId;
		private final long expireAt;
		private final String topic;
		private final byte[] payload;
		private final int qos;
		private final int phase;

		public InflightEntry(String clientId, int packetId, long expireAt,
							 String topic, byte[] payload, int qos) {
			this(clientId, packetId, expireAt, topic, payload, qos, PHASE_PUBLISH);
		}

		public InflightEntry(String clientId, int packetId, long expireAt,
							 String topic, byte[] payload, int qos, int phase) {
			this.clientId = clientId;
			this.packetId = packetId;
			this.expireAt = expireAt;
			this.topic = topic;
			this.payload = payload;
			this.qos = qos;
			this.phase = phase;
		}

		public String getClientId() {
			return clientId;
		}

		public int getPacketId() {
			return packetId;
		}

		public long getExpireAt() {
			return expireAt;
		}

		public String getTopic() {
			return topic;
		}

		public byte[] getPayload() {
			return payload;
		}

		public int getQos() {
			return qos;
		}

		public int getPhase() {
			return phase;
		}
	}
}
