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

import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index over retained MQTT messages, backed by a {@link LocalKvStore}
 * for durability (P2.4).
 * <p>
 * MQTT retain semantics require wildcard subscription matching, e.g. a client
 * subscribing to {@code sensors/+/temperature} must receive the retained message
 * for {@code sensors/room1/temperature}.  Doing this lookup against H2 MVStore
 * directly would require either a full table scan (slow) or a trie structure in
 * H2 (complex).  The pragmatic solution is to maintain a
 * {@link ConcurrentSkipListMap} (lock-free skiplist) of retained messages in
 * memory, sorted by topic, and to publish inserts/deletes atomically to both
 * the in-memory index and the durable store.
 * </p>
 * <p>
 * Memory budget: each entry occupies roughly the topic string + payload + a
 * 100-byte envelope.  At 100 000 retained messages with 1 KB payloads this is
 * approximately 100 MB — acceptable for the target IoT use cases.  Operators
 * with more aggressive workloads should consider the optional sharding mode
 * (P2.5).
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class RetainIndex {
	private static final Logger logger = LoggerFactory.getLogger(RetainIndex.class);
	private static final int FORMAT_MAGIC = 0x52455432;

	/**
	 * The maximum allowed payload size for a retained message.  Larger payloads
	 * are rejected to keep the in-memory index bounded.
	 */
	public static final int DEFAULT_MAX_PAYLOAD_BYTES = 1024 * 1024;

	private final LocalKvStore store;
	private final int maxPayloadBytes;

	/**
	 * Sorted in-memory index: topic → message.  Empty topic never appears as a
	 * retained key.  Use {@link ConcurrentSkipListMap#subMap} for prefix ranges
	 * when answering wildcard queries.
	 */
	private final ConcurrentSkipListMap<String, Message> index = new ConcurrentSkipListMap<>();
	private final ConcurrentHashMap<String, Long> expirations = new ConcurrentHashMap<>();

	public RetainIndex(LocalKvStore store) {
		this(store, DEFAULT_MAX_PAYLOAD_BYTES);
	}

	public RetainIndex(LocalKvStore store, int maxPayloadBytes) {
		this.store = store;
		this.maxPayloadBytes = maxPayloadBytes;
	}

	/**
	 * Rebuilds the in-memory index by scanning the durable store.  Call this
	 * once at broker startup, before the MQTT listener accepts connections.
	 */
	public void loadFromStore() {
		List<LocalKvStore.KeyValue> entries = store.scan("retain:");
		int count = 0;
		for (LocalKvStore.KeyValue kv : entries) {
			String topic = kv.getKey().substring("retain:".length());
			DecodedRetain decoded = deserializeMessage(topic, kv.getValue());
			if (decoded != null && !decoded.isExpired(System.currentTimeMillis())) {
				index.put(topic, decoded.message);
				if (decoded.expireAt > 0) {
					expirations.put(topic, decoded.expireAt);
				}
				count++;
			} else if (decoded != null) {
				store.delete(kv.getKey());
			}
		}
		logger.info("[RetainIndex] Loaded {} retained messages from durable store", count);
	}

	/**
	 * Stores a retained message both in memory and in the durable store.
	 *
	 * @param topic   the topic under which the message is retained
	 * @param message the message body
	 * @return {@code true} if accepted; {@code false} if the payload exceeds
	 *         {@link #maxPayloadBytes}
	 */
	public boolean put(String topic, Message message) {
		return put(topic, message, 0);
	}

	public boolean put(String topic, Message message, int timeoutSeconds) {
		if (topic == null || topic.isEmpty() || message == null) {
			return false;
		}
		if (message.getPayload() != null && message.getPayload().length > maxPayloadBytes) {
			logger.warn("[RetainIndex] Rejecting retain message on {}: payload {} > limit {}",
				topic, message.getPayload().length, maxPayloadBytes);
			return false;
		}
		long expireAt = timeoutSeconds > 0
			? System.currentTimeMillis() + timeoutSeconds * 1_000L
			: 0L;
		byte[] bytes = serializeMessage(message, expireAt);
		// Order: write durable store first, then update in-memory index.  If the
		// in-memory update fails (e.g. JVM crash between the two), we may serve
		// stale data on next startup — but the durable store remains the truth
		// and will be re-loaded by {@link #loadFromStore()}.
		store.put(buildKey(topic), bytes);
		index.put(topic, message);
		if (expireAt > 0) {
			expirations.put(topic, expireAt);
		} else {
			expirations.remove(topic);
		}
		return true;
	}

	/**
	 * Removes a retained message both in memory and in the durable store.
	 *
	 * @param topic the topic whose retained message should be cleared
	 */
	public void remove(String topic) {
		if (topic == null || topic.isEmpty()) {
			return;
		}
		store.delete(buildKey(topic));
		index.remove(topic);
		expirations.remove(topic);
	}

	/**
	 * Returns all retained messages matching the given topic filter (which may
	 * contain {@code +} and {@code #} wildcards).
	 *
	 * @param topicFilter the topic filter to match
	 * @return matching retained messages; never {@code null}, may be empty
	 */
	public List<Message> match(String topicFilter) {
		if (topicFilter == null || topicFilter.isEmpty()) {
			return Collections.emptyList();
		}
		if (!topicFilter.contains("+") && !topicFilter.contains("#")) {
			Message msg = getAlive(topicFilter, System.currentTimeMillis());
			return msg == null ? Collections.emptyList() : Collections.singletonList(msg);
		}
		List<Message> result = new ArrayList<>();
		TopicFilter filter = new TopicFilter(topicFilter);
		long now = System.currentTimeMillis();
		Iterable<Map.Entry<String, Message>> candidates = wildcardCandidates(topicFilter);
		for (Map.Entry<String, Message> entry : candidates) {
			if (!filter.match(entry.getKey())) {
				continue;
			}
			Long expireAt = expirations.get(entry.getKey());
			if (expireAt != null && expireAt > 0L && expireAt <= now) {
				remove(entry.getKey());
			} else {
				result.add(entry.getValue());
			}
		}
		return result;
	}

	private Iterable<Map.Entry<String, Message>> wildcardCandidates(String topicFilter) {
		int plusIndex = topicFilter.indexOf('+');
		int hashIndex = topicFilter.indexOf('#');
		int wildcardIndex;
		if (plusIndex < 0) {
			wildcardIndex = hashIndex;
		} else if (hashIndex < 0) {
			wildcardIndex = plusIndex;
		} else {
			wildcardIndex = Math.min(plusIndex, hashIndex);
		}
		String prefix = topicFilter.substring(0, wildcardIndex);
		if (prefix.isEmpty()) {
			return index.entrySet();
		}
		return index.subMap(prefix, true, prefix + Character.MAX_VALUE, true).entrySet();
	}

	/**
	 * Returns the total number of retained messages currently indexed.
	 *
	 * @return the number of retained messages held in the in-memory index
	 */
	public int size() {
		return index.size();
	}

	/** Returns a stable snapshot used for shard rebalancing. */
	public List<Message> snapshot() {
		List<Message> messages = new ArrayList<>();
		for (RetainEntry entry : snapshotEntries()) {
			messages.add(entry.getMessage());
		}
		return messages;
	}

	public List<RetainEntry> snapshotEntries() {
		long now = System.currentTimeMillis();
		List<RetainEntry> entries = new ArrayList<>();
		for (Map.Entry<String, Message> entry : index.entrySet()) {
			Message message = getAlive(entry.getKey(), now);
			if (message != null) {
				entries.add(new RetainEntry(entry.getKey(), message, expirations.getOrDefault(entry.getKey(), 0L)));
			}
		}
		return entries;
	}

	private Message getAlive(String topic, long now) {
		Long expireAt = expirations.get(topic);
		if (expireAt != null && expireAt > 0 && expireAt <= now) {
			remove(topic);
			return null;
		}
		return index.get(topic);
	}

	private static String buildKey(String topic) {
		return "retain:" + topic;
	}

	private static byte[] serializeMessage(Message msg, long expireAt) {
		// For simplicity we use the DefaultMessageSerializer from the core module
		// via a defensive copy in the kafka-style envelope:
		//   [4 bytes] payload length
		//   [N bytes] payload
		//   [1 byte]  qos
		//   [4 bytes] topic length
		//   [N bytes] topic
		// A full upgrade would replace this with a dedicated message codec.
		java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
		try {
			dos.writeInt(FORMAT_MAGIC);
			dos.writeLong(expireAt);
			byte[] payload = msg.getPayload() == null ? new byte[0] : msg.getPayload();
			dos.writeInt(payload.length);
			dos.write(payload);
			dos.writeByte(msg.getQos());
			byte[] topicBytes = (msg.getTopic() == null ? "" : msg.getTopic()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
			dos.writeInt(topicBytes.length);
			dos.write(topicBytes);
			dos.flush();
			return baos.toByteArray();
		} catch (java.io.IOException e) {
			throw new RuntimeException("Failed to serialize retain message", e);
		}
	}

	private static DecodedRetain deserializeMessage(String topic, byte[] value) {
		try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(value);
			 java.io.DataInputStream dis = new java.io.DataInputStream(bais)) {
			int first = dis.readInt();
			long expireAt = first == FORMAT_MAGIC ? dis.readLong() : 0L;
			int payloadLen = first == FORMAT_MAGIC ? dis.readInt() : first;
			byte[] payload = new byte[payloadLen];
			dis.readFully(payload);
			int qos = dis.readByte() & 0xFF;
			int topicLen = dis.readInt();
			byte[] topicBytes = new byte[topicLen];
			dis.readFully(topicBytes);
			String innerTopic = new String(topicBytes, java.nio.charset.StandardCharsets.UTF_8);
			Message msg = new Message();
			msg.setPayload(payload);
			msg.setQos(qos);
			msg.setTopic(innerTopic.isEmpty() ? topic : innerTopic);
			msg.setRetain(true);
			return new DecodedRetain(msg, expireAt);
		} catch (java.io.IOException e) {
			logger.warn("[RetainIndex] Failed to deserialize retain for {}", topic, e);
			return null;
		}
	}

	public static final class RetainEntry {
		private final String topic;
		private final Message message;
		private final long expireAt;

		private RetainEntry(String topic, Message message, long expireAt) {
			this.topic = topic;
			this.message = message;
			this.expireAt = expireAt;
		}

		public String getTopic() {
			return topic;
		}

		public Message getMessage() {
			return message;
		}

		public int remainingTimeoutSeconds(long nowMs) {
			if (expireAt <= 0) {
				return 0;
			}
			long remaining = Math.max(1L, expireAt - nowMs);
			return (int) Math.min(Integer.MAX_VALUE, (remaining + 999L) / 1_000L);
		}
	}

	private static final class DecodedRetain {
		private final Message message;
		private final long expireAt;

		private DecodedRetain(Message message, long expireAt) {
			this.message = message;
			this.expireAt = expireAt;
		}

		private boolean isExpired(long nowMs) {
			return expireAt > 0 && expireAt <= nowMs;
		}
	}
}
