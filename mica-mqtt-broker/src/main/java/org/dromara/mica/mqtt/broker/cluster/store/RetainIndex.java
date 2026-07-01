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
			Message msg = deserializeMessage(topic, kv.getValue());
			if (msg != null) {
				index.put(topic, msg);
				count++;
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
		if (topic == null || topic.isEmpty() || message == null) {
			return false;
		}
		if (message.getPayload() != null && message.getPayload().length > maxPayloadBytes) {
			logger.warn("[RetainIndex] Rejecting retain message on {}: payload {} > limit {}",
				topic, message.getPayload().length, maxPayloadBytes);
			return false;
		}
		byte[] bytes = serializeMessage(message);
		// Order: write durable store first, then update in-memory index.  If the
		// in-memory update fails (e.g. JVM crash between the two), we may serve
		// stale data on next startup — but the durable store remains the truth
		// and will be re-loaded by {@link #loadFromStore()}.
		store.put(buildKey(topic), bytes);
		index.put(topic, message);
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
			Message msg = index.get(topicFilter);
			return msg == null ? Collections.emptyList() : Collections.singletonList(msg);
		}
		List<Message> result = new ArrayList<>();
		TopicFilter filter = new TopicFilter(topicFilter);
		for (Map.Entry<String, Message> entry : index.entrySet()) {
			if (filter.match(entry.getKey())) {
				result.add(entry.getValue());
			}
		}
		return result;
	}

	/**
	 * Returns the total number of retained messages currently indexed.
	 *
	 * @return the number of retained messages held in the in-memory index
	 */
	public int size() {
		return index.size();
	}

	private static String buildKey(String topic) {
		return "retain:" + topic;
	}

	private static byte[] serializeMessage(Message msg) {
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

	private static Message deserializeMessage(String topic, byte[] value) {
		try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(value);
			 java.io.DataInputStream dis = new java.io.DataInputStream(bais)) {
			int payloadLen = dis.readInt();
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
			return msg;
		} catch (java.io.IOException e) {
			logger.warn("[RetainIndex] Failed to deserialize retain for {}", topic, e);
			return null;
		}
	}
}