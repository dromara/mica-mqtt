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

import org.dromara.mica.mqtt.core.server.model.Subscribe;

import java.util.List;

/**
 * Persistent store for MQTT session state used during cross-node takeover (P2.1).
 * <p>
 * An "MQTT session" in the V3 cluster model consists of:
 * <ul>
 *   <li>The set of active subscriptions ({@link Subscribe})</li>
 *   <li>The pending inflight QoS 1/2 messages (already handled by {@link InflightStore})</li>
 *   <li>Session metadata: {@code cleanSession}, {@code sessionExpirySeconds}, owner node</li>
 * </ul>
 * <p>
 * <strong>Invariant INV-1</strong> (from the storage design doc): a session must
 * be persisted to L2 <em>before</em> the broker sends CONNACK.  This ensures
 * that even if the new owner crashes immediately after accepting a connection,
 * the previous owner (or another node) can re-acquire the session state via
 * the takeover protocol.
 * </p>
 * <p>
 * <strong>Wire format</strong>: when a node hands a session off to another
 * node via {@link org.dromara.mica.mqtt.broker.cluster.message.SessionTakeoverResponseMessage},
 * the payload is exactly the {@code byte[]} returned by
 * {@link #loadRaw(String)}.  This means the takeover protocol does not need
 * a separate codec — the H2 store format <em>is</em> the wire format.
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public interface SessionStore {

	/**
	 * Persists (or replaces) the session state for the given client.
	 *
	 * @param clientId  the MQTT client identifier
	 * @param session   the session state to persist
	 */
	void save(String clientId, Session session);

	/**
	 * Returns the persisted session state, or {@code null} if no session exists.
	 *
	 * @param clientId the MQTT client identifier
	 * @return the session, or {@code null}
	 */
	Session load(String clientId);

	/**
	 * Loads every persisted session during broker startup.
	 *
	 * @return persisted sessions, never {@code null}
	 */
	List<Session> loadAll();

	/**
	 * Returns the raw bytes for the session, suitable for sending as the
	 * payload of a {@code SessionTakeoverResponseMessage}.
	 *
	 * @param clientId the MQTT client identifier
	 * @return the persisted bytes, or {@code null}
	 */
	byte[] loadRaw(String clientId);

	/**
	 * Restores a session from raw bytes (e.g. received from a previous owner).
	 * <p>
	 * Used by the new owner after a successful takeover to install the session
	 * state locally before accepting the client's first PUBLISH/SUBSCRIBE.
	 * </p>
	 *
	 * @param clientId the MQTT client identifier
	 * @param bytes    the raw session bytes (same format as {@link #loadRaw})
	 * @return the deserialized session
	 */
	Session restoreRaw(String clientId, byte[] bytes);

	/**
	 * Permanently removes the session for the given client.
	 *
	 * @param clientId the MQTT client identifier
	 */
	void delete(String clientId);

	// ---- Nested value type -------------------------------------------------------

	/**
	 * Snapshot of the persistent portion of an MQTT session.
	 */
	final class Session {
		private String clientId;
		private List<Subscribe> subscriptions;
		private boolean cleanSession;
		private long sessionExpirySeconds;
		private String ownerNodeId;

		public Session() {
		}

		public Session(String clientId, List<Subscribe> subscriptions,
					   boolean cleanSession, long sessionExpirySeconds,
					   String ownerNodeId) {
			this.clientId = clientId;
			this.subscriptions = subscriptions;
			this.cleanSession = cleanSession;
			this.sessionExpirySeconds = sessionExpirySeconds;
			this.ownerNodeId = ownerNodeId;
		}

		public String getClientId() {
			return clientId;
		}

		public void setClientId(String clientId) {
			this.clientId = clientId;
		}

		public List<Subscribe> getSubscriptions() {
			return subscriptions;
		}

		public void setSubscriptions(List<Subscribe> subscriptions) {
			this.subscriptions = subscriptions;
		}

		public boolean isCleanSession() {
			return cleanSession;
		}

		public void setCleanSession(boolean cleanSession) {
			this.cleanSession = cleanSession;
		}

		public long getSessionExpirySeconds() {
			return sessionExpirySeconds;
		}

		public void setSessionExpirySeconds(long sessionExpirySeconds) {
			this.sessionExpirySeconds = sessionExpirySeconds;
		}

		public String getOwnerNodeId() {
			return ownerNodeId;
		}

		public void setOwnerNodeId(String ownerNodeId) {
			this.ownerNodeId = ownerNodeId;
		}
	}
}
