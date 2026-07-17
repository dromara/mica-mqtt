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

import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessageSerializer;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * H2 MVStore-backed implementation of {@link SessionStore}.
 * <p>
 * Each session is stored under key {@code "session:<clientId>"} as a
 * self-describing binary envelope that can be transported between nodes
 * without re-serialization (the {@link #loadRaw} bytes are reused directly
 * as the payload of {@code SessionTakeoverResponseMessage}).
 * </p>
 * <h2>Value format</h2>
 * <pre>
 *   [1 byte]   cleanSession flag
 *   [8 bytes]  sessionExpirySeconds (long)
 *   [2 bytes]  ownerNodeId length, or -1 if null
 *   [N bytes]  ownerNodeId (UTF-8)
 *   [N bytes]  serialized subscriptions (delegated to ClusterMessageSerializer)
 * </pre>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class H2SessionStore implements SessionStore {
	private static final Logger logger = LoggerFactory.getLogger(H2SessionStore.class);

	private static final String KEY_PREFIX = "session:";
	private final LocalKvStore store;

	public H2SessionStore(LocalKvStore store) {
		this.store = store;
	}

	@Override
	public void save(String clientId, Session session) {
		byte[] bytes = serialize(session);
		store.put(buildKey(clientId), bytes);
	}

	@Override
	public Session load(String clientId) {
		byte[] bytes = store.get(buildKey(clientId));
		return bytes == null ? null : deserialize(clientId, bytes);
	}

	@Override
	public List<Session> loadAll() {
		List<Session> sessions = new ArrayList<>();
		for (LocalKvStore.KeyValue entry : store.scan(KEY_PREFIX)) {
			String clientId = entry.getKey().substring(KEY_PREFIX.length());
			Session session = deserialize(clientId, entry.getValue());
			if (session != null) {
				sessions.add(session);
			}
		}
		return sessions;
	}

	@Override
	public byte[] loadRaw(String clientId) {
		return store.get(buildKey(clientId));
	}

	@Override
	public Session restoreRaw(String clientId, byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		Session session = deserialize(clientId, bytes);
		if (session != null) {
			save(clientId, session);
		}
		return session;
	}

	@Override
	public void delete(String clientId) {
		store.delete(buildKey(clientId));
	}

	static String buildKey(String clientId) {
		return KEY_PREFIX + clientId;
	}

	static byte[] serialize(Session session) {
		try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
			 java.io.DataOutputStream dos = new java.io.DataOutputStream(baos)) {
			dos.writeBoolean(session.isCleanSession());
			dos.writeLong(session.getSessionExpirySeconds());
			String owner = session.getOwnerNodeId();
			if (owner == null) {
				dos.writeShort(-1);
			} else {
				byte[] ownerBytes = owner.getBytes(java.nio.charset.StandardCharsets.UTF_8);
				dos.writeShort(ownerBytes.length);
				dos.write(ownerBytes);
			}
			byte[] subBytes = ClusterMessageSerializer.serializeSubscriptions(session.getSubscriptions());
			dos.writeInt(subBytes.length);
			if (subBytes.length > 0) {
				dos.write(subBytes);
			}
			dos.flush();
			return baos.toByteArray();
		} catch (java.io.IOException e) {
			throw new RuntimeException("Failed to serialize session: " + session.getClientId(), e);
		}
	}

	static Session deserialize(String clientId, byte[] value) {
		try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(value);
			 java.io.DataInputStream dis = new java.io.DataInputStream(bais)) {
			Session session = new Session();
			session.setClientId(clientId);
			session.setCleanSession(dis.readBoolean());
			session.setSessionExpirySeconds(dis.readLong());
			short ownerLen = dis.readShort();
			if (ownerLen >= 0) {
				byte[] ownerBytes = new byte[ownerLen];
				dis.readFully(ownerBytes);
				session.setOwnerNodeId(new String(ownerBytes, java.nio.charset.StandardCharsets.UTF_8));
			}
			int subLen = dis.readInt();
			if (subLen > 0) {
				byte[] subBytes = new byte[subLen];
				dis.readFully(subBytes);
				List<Subscribe> subs = ClusterMessageSerializer.deserializeSubscriptions(subBytes);
				session.setSubscriptions(subs);
			}
			return session;
		} catch (java.io.IOException e) {
			logger.warn("[SessionStore] Failed to deserialize session: {}", clientId, e);
			return null;
		}
	}
}
