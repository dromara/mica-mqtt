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

import org.h2.mvstore.Cursor;
import org.h2.mvstore.MVMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * H2 MVStore-backed implementation of {@link InflightStore}.
 * <p>
 * Stores QoS 1/2 inflight messages in a dedicated {@code MVMap} named
 * {@value #MAP_NAME} within the shared {@link H2MvStoreImpl} engine.  Because
 * the inflight write path is in the hot send loop, puts are dispatched to an
 * ordered async writer so that H2 file I/O does not block t-io worker threads
 * while preserving put → phase-update → remove ordering for each packet.
 * </p>
 *
 * <h2>Key format</h2>
 * <pre>
 *   "inflight:{clientId}:{packetId:05d}"
 * </pre>
 * The zero-padded packet-id ensures lexicographic key ordering matches numeric
 * ordering, making {@code listByClient} return entries in packet-id order.
 *
 * <h2>Value format</h2>
 * <p>
 * A compact binary encoding (big-endian):
 * </p>
 * <pre>
 *   [8 bytes] expireAt (long)
 *   [4 bytes] topicLen (int)
 *   [topicLen bytes] topic (UTF-8)
 *   [4 bytes] payloadLen (int)
 *   [payloadLen bytes] payload (raw bytes)
 *   [1 byte]  qos
 *   [1 byte]  phase (0=PUBLISH, 1=PUBREL; absent in legacy records)
 * </pre>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class H2InflightStore implements InflightStore {
	private static final Logger logger = LoggerFactory.getLogger(H2InflightStore.class);

	static final String MAP_NAME = "mica_mqtt_inflight";
	private static final String KEY_PREFIX = "inflight:";

	private final H2MvStoreImpl engine;
	private final MVMap<String, byte[]> map;

	/**
	 * A single writer is intentional: multiple writers can reorder put, PUBREL phase
	 * update and ACK removal for the same packet, resurrecting stale inflight state.
	 */
	private static final int WRITER_POOL_SIZE = 1;
	private final ExecutorService asyncWriter;

	/**
	 * Constructs an inflight store using the given shared H2 engine.
	 * <p>
	 * {@link H2MvStoreImpl#open(java.nio.file.Path)} must have been called before
	 * invoking this constructor.
	 * </p>
	 *
	 * @param engine the already-opened H2 engine; must not be {@code null}
	 */
	public H2InflightStore(H2MvStoreImpl engine) {
		this.engine = engine;
		this.map = engine.openMap(MAP_NAME);
		this.asyncWriter = Executors.newFixedThreadPool(WRITER_POOL_SIZE, new InflightWriterThreadFactory());
	}

	@Override
	public void put(String clientId, int packetId, long expireAt, String topic, byte[] payload, int qos) {
		String key = buildKey(clientId, packetId);
		byte[] value = serialize(expireAt, topic, payload, qos, PHASE_PUBLISH);
		// Async write to avoid blocking the send path.
		// The writer is asynchronous, but each accepted mutation is committed so a
		// kill -9 cannot erase a QoS packet after it became visible to the store.
		asyncWriter.submit(() -> {
			try {
				map.put(key, value);
				engine.recordWriteOperation();
				engine.commit();
			} catch (Exception e) {
				logger.error("[InflightStore] Failed to persist inflight record clientId={} packetId={}", clientId, packetId, e);
			}
		});
	}

	@Override
	public void updatePhase(String clientId, int packetId, int phase) {
		String key = buildKey(clientId, packetId);
		asyncWriter.submit(() -> {
			try {
				byte[] current = map.get(key);
				engine.recordReadOperation();
				if (current != null) {
					map.put(key, withPhase(current, phase));
					engine.recordWriteOperation();
				}
				engine.commit();
			} catch (Exception e) {
				logger.error("[InflightStore] Failed to update phase clientId={} packetId={} phase={}",
					clientId, packetId, phase, e);
			}
		});
	}

	@Override
	public void remove(String clientId, int packetId) {
		String key = buildKey(clientId, packetId);
		// Async to keep the ACK path non-blocking; commit prevents stale replay.
		asyncWriter.submit(() -> {
			try {
				map.remove(key);
				engine.recordWriteOperation();
				engine.commit();
			} catch (Exception e) {
				logger.error("[InflightStore] Failed to remove inflight record clientId={} packetId={}", clientId, packetId, e);
			}
		});
	}

	@Override
	public List<InflightEntry> listByClient(String clientId) {
		engine.recordReadOperation();
		String prefix = KEY_PREFIX + clientId + ":";
		List<InflightEntry> result = new ArrayList<>();
		Cursor<String, byte[]> cursor = map.cursor(prefix);
		while (cursor.hasNext()) {
			String key = cursor.next();
			if (!key.startsWith(prefix)) {
				break;
			}
			InflightEntry entry = deserialize(clientId, key, cursor.getValue());
			if (entry != null) {
				result.add(entry);
			}
		}
		return result;
	}

	@Override
	public int removeExpired(long nowMs) {
		engine.recordReadOperation();
		List<String> toRemove = new ArrayList<>();
		// Use prefix cursor to avoid scanning unrelated map entries.
		Cursor<String, byte[]> cursor = map.cursor(KEY_PREFIX);
		while (cursor.hasNext()) {
			String key = cursor.next();
			if (!key.startsWith(KEY_PREFIX)) {
				break;
			}
			byte[] value = cursor.getValue();
			if (value == null || value.length < 8) {
				continue;
			}
			// First 8 bytes are expireAt
			long expireAt = readLong(value, 0);
			if (expireAt > 0 && expireAt < nowMs) {
				toRemove.add(key);
			}
		}
		if (!toRemove.isEmpty()) {
			for (String key : toRemove) {
				map.remove(key);
				engine.recordWriteOperation();
			}
			engine.commit();
			logger.debug("[InflightStore] Removed {} expired inflight records", toRemove.size());
		}
		return toRemove.size();
	}

	@Override
	public long count() {
		engine.recordReadOperation();
		return map.sizeAsLong();
	}

	/**
	 * Shuts down the async writer pool.
	 * <p>
	 * Call this when the broker is stopping to allow in-flight async writes to complete.
	 * </p>
	 */
	public void shutdown() {
		asyncWriter.shutdown();
		try {
			if (!asyncWriter.awaitTermination(5, TimeUnit.SECONDS)) {
				logger.warn("[InflightStore] Async writer did not terminate in 5 s; forcing shutdown");
				asyncWriter.shutdownNow();
			}
		} catch (InterruptedException e) {
			asyncWriter.shutdownNow();
			Thread.currentThread().interrupt();
		}
		// Final defensive flush after the writer queue has drained.
		engine.commit();
	}

	/**
	 * Waits for all currently queued async writes to complete, then commits
	 * so that subsequent reads (e.g. cursor scans) see the latest data.
	 * <p>
	 * This method is intended for testing only.  It submits a barrier task to each
	 * writer thread and blocks until all threads have processed it, ensuring that
	 * all previously submitted puts and removes are visible.
	 * </p>
	 */
	void awaitWrites() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(WRITER_POOL_SIZE);
		for (int i = 0; i < WRITER_POOL_SIZE; i++) {
			asyncWriter.submit(latch::countDown);
		}
		latch.await(5, TimeUnit.SECONDS);
		// Flush uncommitted writes so cursor scans can see them
		// (required when auto-commit is disabled).
		engine.commit();
	}

	// ---- Key helpers -------------------------------------------------------------

	/**
	 * Builds a store key for the given client + packet.
	 * Packet ID is zero-padded to 5 digits so that lexicographic order == numeric order.
	 */
	static String buildKey(String clientId, int packetId) {
		return String.format("%s%s:%05d", KEY_PREFIX, clientId, packetId);
	}

	// ---- Value serialization / deserialization -----------------------------------

	/**
	 * Serializes an inflight record to a compact binary format.
	 *
	 * <pre>
	 *   [8 bytes] expireAt
	 *   [4 bytes] topicLen
	 *   [topicLen bytes] topic (UTF-8)
	 *   [4 bytes] payloadLen
	 *   [payloadLen bytes] payload
	 *   [1 byte]  qos
	 * </pre>
	 */
	static byte[] serialize(long expireAt, String topic, byte[] payload, int qos) {
		return serialize(expireAt, topic, payload, qos, PHASE_PUBLISH);
	}

	static byte[] serialize(long expireAt, String topic, byte[] payload, int qos, int phase) {
		byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
		int size = 8 + 4 + topicBytes.length + 4 + (payload == null ? 0 : payload.length) + 2;
		ByteArrayOutputStream baos = new ByteArrayOutputStream(size);
		try (DataOutputStream dos = new DataOutputStream(baos)) {
			dos.writeLong(expireAt);
			dos.writeInt(topicBytes.length);
			dos.write(topicBytes);
			int payloadLen = payload == null ? 0 : payload.length;
			dos.writeInt(payloadLen);
			if (payloadLen > 0) {
				dos.write(payload);
			}
			dos.writeByte(qos);
			dos.writeByte(phase);
		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize inflight record", e);
		}
		return baos.toByteArray();
	}

	private InflightEntry deserialize(String clientId, String key, byte[] value) {
		if (value == null || value.length < 8) {
			return null;
		}
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(value))) {
			long expireAt = dis.readLong();
			int topicLen = dis.readInt();
			byte[] topicBytes = new byte[topicLen];
			dis.readFully(topicBytes);
			String topic = new String(topicBytes, StandardCharsets.UTF_8);
			int payloadLen = dis.readInt();
			byte[] payload = new byte[payloadLen];
			if (payloadLen > 0) {
				dis.readFully(payload);
			}
			int qos = dis.readByte() & 0xFF;
			int phase = dis.available() > 0 ? dis.readByte() & 0xFF : PHASE_PUBLISH;

			// Extract packetId from key: "inflight:{clientId}:{packetId}"
			int lastColon = key.lastIndexOf(':');
			int packetId = 0;
			if (lastColon >= 0 && lastColon < key.length() - 1) {
				try {
					packetId = Integer.parseInt(key.substring(lastColon + 1));
				} catch (NumberFormatException e) {
					logger.warn("[InflightStore] Invalid packetId in key: {}", key);
				}
			}
			return new InflightEntry(clientId, packetId, expireAt, topic, payload, qos, phase);
		} catch (IOException e) {
			logger.warn("[InflightStore] Failed to deserialize inflight record key={}", key, e);
			return null;
		}
	}

	private static byte[] withPhase(byte[] value, int phase) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(value))) {
			dis.readLong();
			int topicLength = dis.readInt();
			if (topicLength < 0 || topicLength > dis.available()) {
				throw new IOException("Invalid inflight topic length: " + topicLength);
			}
			dis.skipBytes(topicLength);
			int payloadLength = dis.readInt();
			if (payloadLength < 0 || payloadLength > dis.available()) {
				throw new IOException("Invalid inflight payload length: " + payloadLength);
			}
			dis.skipBytes(payloadLength);
			dis.readByte();
			int phaseOffset = value.length - dis.available();
			if (dis.available() > 0) {
				byte[] updated = java.util.Arrays.copyOf(value, value.length);
				updated[phaseOffset] = (byte) phase;
				return updated;
			}
			byte[] updated = java.util.Arrays.copyOf(value, value.length + 1);
			updated[value.length] = (byte) phase;
			return updated;
		}
	}

	/** Reads a big-endian long from position {@code offset} in {@code bytes}. */
	private static long readLong(byte[] bytes, int offset) {
		long value = 0;
		for (int i = 0; i < 8; i++) {
			value = (value << 8) | (bytes[offset + i] & 0xFF);
		}
		return value;
	}

	// ---- Thread factory ----------------------------------------------------------

	private static final class InflightWriterThreadFactory implements ThreadFactory {
		private final AtomicInteger count = new AtomicInteger(0);

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "mica-mqtt-inflight-writer-" + count.incrementAndGet());
			t.setDaemon(true);
			return t;
		}
	}
}
