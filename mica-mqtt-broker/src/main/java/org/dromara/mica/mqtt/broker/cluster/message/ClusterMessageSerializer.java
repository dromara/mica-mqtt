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
import org.dromara.mica.mqtt.core.server.model.Subscribe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializer for cluster messages handling conversion between domain models and t-io transport format.
 * This class provides bidirectional serialization:
 * <ul>
 *   <li>{@link #toClusterData(ClusterMessage, String)} - serializes a {@link ClusterMessage} into a t-io {@link ClusterDataMessage}</li>
 *   <li>{@link #fromClusterData(ClusterDataMessage)} - deserializes a {@link ClusterDataMessage} back into the appropriate {@link ClusterMessage} subclass</li>
 * </ul>
 * The serialization format uses efficient binary encoding with compact header management.
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType
 * @since 1.0.0
 */
public class ClusterMessageSerializer {
	private static final int ENVELOPE_MAGIC = 0x4D514343;
	private static final int MAX_ENVELOPE_HEADERS = 64;
	private static final int SUBSCRIPTION_FORMAT_V2 = -2;
	/**
	 * Header key for message type code.
	 */
	public static final String HEADER_TYPE = "type";
	/**
	 * Header key for client identifier.
	 */
	public static final String HEADER_CLIENT_ID = "clientId";
	/**
	 * Header key for node identifier.
	 */
	public static final String HEADER_NODE_ID = "nodeId";
	/**
	 * Header key for source node identifier.
	 */
	public static final String HEADER_SOURCE_NODE = "sourceNode";
	/**
	 * Header key for topic name.
	 */
	public static final String HEADER_TOPIC = "topic";
	/**
	 * Header key for timeout value in milliseconds.
	 */
	public static final String HEADER_TIMEOUT = "timeout";

	/**
	 * Serializes a cluster message into a t-io cluster data message for network transmission.
	 *
	 * @param msg the cluster message to serialize
	 * @param sourceNode the identifier of the source node initiating the send
	 * @return the serialized {@link ClusterDataMessage} ready for transmission
	 */
	public static ClusterDataMessage toClusterData(ClusterMessage msg, String sourceNode) {
		Map<String, String> headers = new HashMap<>(8);
		headers.put(HEADER_TYPE, String.valueOf(msg.getType().getCode()));
		headers.put(HEADER_SOURCE_NODE, sourceNode);
		msg.toClusterData(headers);
		byte[] payload = msg.toPayload();
		// Keep the MQTT envelope in the binary payload. This avoids depending on
		// mica-net's optional JSON header codec, which is not present in every
		// runtime distribution. fromClusterData still accepts the legacy format.
		return new ClusterDataMessage(System.currentTimeMillis(), null, encodeEnvelope(headers, payload));
	}

	/**
	 * Deserializes a t-io cluster data message into the appropriate cluster message subtype.
	 *
	 * @param data the serialized {@link ClusterDataMessage} received from the network
	 * @param <T> the expected cluster message subtype
	 * @return the deserialized cluster message instance
	 * @throws IllegalArgumentException if the message type is unknown
	 */
	@SuppressWarnings("unchecked")
	public static <T extends ClusterMessage> T fromClusterData(ClusterDataMessage data) {
		data = unwrapEnvelope(data);
		int typeCode = Integer.parseInt(data.getHeader(HEADER_TYPE));
		ClusterMessageType type;
		try {
			type = ClusterMessageType.fromCode(typeCode);
		} catch (IllegalArgumentException e) {
			// Unknown message type — forward-compatibility: a V1 node receiving a V2/V3
			// message should log a warning and return null so the caller can skip it.
			return null;
		}
		ClusterMessage msg = createMessage(type);
		if (msg == null) {
			return null;
		}
		msg.fromClusterData(data);
		return (T) msg;
	}

	/**
	 * Extracts the source node identifier from a cluster data message.
	 *
	 * @param data the cluster data message
	 * @return the source node identifier
	 */
	public static String getSourceNode(ClusterDataMessage data) {
		return unwrapEnvelope(data).getHeader(HEADER_SOURCE_NODE);
	}

	private static byte[] encodeEnvelope(Map<String, String> headers, byte[] payload) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(128 + payload.length);
			DataOutputStream output = new DataOutputStream(bytes);
			output.writeInt(ENVELOPE_MAGIC);
			output.writeInt(headers.size());
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				writeEnvelopeString(output, entry.getKey());
				writeEnvelopeString(output, entry.getValue());
			}
			output.writeInt(payload.length);
			output.write(payload);
			output.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to encode cluster envelope", e);
		}
	}

	private static ClusterDataMessage unwrapEnvelope(ClusterDataMessage data) {
		if (data.getHeader(HEADER_TYPE) != null) {
			return data;
		}
		byte[] payload = data.getPayload();
		if (payload == null || payload.length < 12) {
			return data;
		}
		try {
			DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
			if (input.readInt() != ENVELOPE_MAGIC) {
				return data;
			}
			int headerCount = input.readInt();
			if (headerCount < 0 || headerCount > MAX_ENVELOPE_HEADERS) {
				throw new IllegalArgumentException("Invalid cluster envelope header count: " + headerCount);
			}
			Map<String, String> headers = new HashMap<>(headerCount);
			for (int i = 0; i < headerCount; i++) {
				headers.put(readEnvelopeString(input), readEnvelopeString(input));
			}
			int payloadLength = input.readInt();
			if (payloadLength < 0 || payloadLength > input.available()) {
				throw new IllegalArgumentException("Invalid cluster envelope payload length: " + payloadLength);
			}
			byte[] messagePayload = new byte[payloadLength];
			input.readFully(messagePayload);
			return new ClusterDataMessage(data.getTimestamp(), headers, messagePayload);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to decode cluster envelope", e);
		}
	}

	private static void writeEnvelopeString(DataOutputStream output, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	private static String readEnvelopeString(DataInputStream input) throws IOException {
		int length = input.readInt();
		if (length < 0 || length > input.available()) {
			throw new IllegalArgumentException("Invalid cluster envelope string length: " + length);
		}
		byte[] bytes = new byte[length];
		input.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static ClusterMessage createMessage(ClusterMessageType type) {
		switch (type) {
			case CLIENT_CONNECT:
				return new ClientConnectMessage();
			case CLIENT_DISCONNECT:
				return new ClientDisconnectMessage();
			case SUBSCRIBE_NOTIFY:
				return new SubscribeNotifyMessage();
			case UNSUBSCRIBE_NOTIFY:
				return new UnsubscribeNotifyMessage();
			case PUBLISH_FORWARD:
				return new PublishForwardMessage();
			case STATE_SYNC_RESPONSE:
				return new StateSyncResponseMessage();
			case NODE_LEAVE:
				return new NodeLeaveMessage();
			case STATE_SYNC_REQUEST:
				return new StateSyncRequestMessage();
			case WILL_MESSAGE:
				return new WillMessageNotifyMessage();
			case RETAIN_MESSAGE:
				return new RetainMessageNotifyMessage();
			case SHARED_DISPATCH_TO_CLIENT:
				return new SharedDispatchToClientMessage();
			case SHARED_SUBSCRIBE_NOTIFY:
		case SHARED_SUBSCRIBE_REMOVE:
		case SHARED_SUB_STATE_SYNC:
		case SHARED_SUB_TAKEOVER:
			// V2 shared-subscribe notifications and V3 storage messages that
			// are still under development; the caller will skip the null return.
			return null;
			case RETAIN_QUERY:
				return new RetainQueryMessage();
			case HEARTBEAT:
				return new HeartbeatMessage();
		case SESSION_TAKEOVER_REQUEST:
			return new SessionTakeoverRequestMessage();
		case SESSION_TAKEOVER_RESPONSE:
			return new SessionTakeoverResponseMessage();
		case SESSION_MIGRATED_NOTIFY:
			return new SessionMigratedNotifyMessage();
		case SESSION_DELETE_NOTIFY:
			return new SessionDeleteNotifyMessage();
			default:
				throw new IllegalArgumentException("Unknown message type: " + type);
		}
	}

	/**
	 * Serializes a list of subscription records into binary format.
	 *
	 * @param subscriptions the list of subscriptions to serialize
	 * @return the binary payload, or an empty array if the list is null or empty
	 */
	public static byte[] serializeSubscriptions(List<Subscribe> subscriptions) {
		if (subscriptions == null || subscriptions.isEmpty()) {
			return new byte[0];
		}
		ByteBuffer buf = ByteBuffer.allocate(calculateSubscriptionsLength(subscriptions));
		// A negative marker distinguishes the complete MQTT 5 subscription format
		// from the legacy qos/noLocal-only format, whose first int was a count.
		buf.putInt(SUBSCRIPTION_FORMAT_V2);
		buf.putInt(subscriptions.size());
		for (Subscribe sub : subscriptions) {
			writeString(buf, sub.getTopicFilter());
			writeString(buf, sub.getClientId());
			buf.put((byte) sub.getMqttQoS());
			buf.put(sub.isNoLocal() ? (byte) 1 : 0);
			buf.put(sub.isRetainAsPublished() ? (byte) 1 : 0);
			buf.put((byte) sub.getRetainHandling());
			buf.putInt(sub.getSubscriptionId());
		}
		byte[] result = new byte[buf.position()];
		buf.flip();
		buf.get(result);
		return result;
	}

	/**
	 * Deserializes a binary payload into a list of subscription records.
	 *
	 * @param data the binary payload
	 * @return the list of subscriptions, or null if the payload is invalid
	 */
	public static List<Subscribe> deserializeSubscriptions(byte[] data) {
		if (data == null || data.length == 0) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(data);
		int marker = buf.getInt();
		boolean completeOptions = marker == SUBSCRIPTION_FORMAT_V2;
		int size = completeOptions ? buf.getInt() : marker;
		if (size == 0) {
			return null;
		}
		List<Subscribe> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			Subscribe sub = new Subscribe();
			sub.setTopicFilter(readString(buf));
			sub.setClientId(readString(buf));
			sub.setMqttQoS(buf.get() & 0xFF);
			sub.setNoLocal(buf.get() == 1);
			if (completeOptions) {
				sub.setRetainAsPublished(buf.get() == 1);
				sub.setRetainHandling(buf.get() & 0xFF);
				sub.setSubscriptionId(buf.getInt());
			}
			list.add(sub);
		}
		return list;
	}

	/**
	 * Serializes a list of topic strings into binary format.
	 *
	 * @param topics the list of topics to serialize
	 * @return the binary payload, or an empty array if the list is null or empty
	 */
	public static byte[] serializeTopics(List<String> topics) {
		if (topics == null || topics.isEmpty()) {
			return new byte[0];
		}
		int len = 4;
		List<byte[]> topicBytes = new ArrayList<>();
		for (String topic : topics) {
			byte[] bytes = topic.getBytes(StandardCharsets.UTF_8);
			topicBytes.add(bytes);
			len += 2 + bytes.length;
		}
		ByteBuffer buf = ByteBuffer.allocate(len);
		buf.putInt(topics.size());
		for (byte[] bytes : topicBytes) {
			buf.putShort((short) bytes.length);
			buf.put(bytes);
		}
		byte[] result = new byte[buf.position()];
		buf.flip();
		buf.get(result);
		return result;
	}

	/**
	 * Deserializes a binary payload into a list of topic strings.
	 *
	 * @param data the binary payload
	 * @return the list of topics, or null if the payload is invalid
	 */
	public static List<String> deserializeTopics(byte[] data) {
		if (data == null || data.length == 0) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(data);
		int size = buf.getInt();
		if (size == 0) {
			return null;
		}
		List<String> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(readString(buf));
		}
		return list;
	}

	/**
	 * Serializes state synchronization data containing client-to-node mappings and subscription tables.
	 *
	 * @param clientNodeMap mapping of client identifiers to node identifiers
	 * @param subscriptionMap mapping of client identifiers to their subscription lists
	 * @return the binary serialized payload
	 */
	public static byte[] serializeStateSyncData(Map<String, String> clientNodeMap, Map<String, List<Subscribe>> subscriptionMap) {
		int len = calculateStateSyncDataLength(clientNodeMap, subscriptionMap);
		ByteBuffer buf = ByteBuffer.allocate(len);

		if (clientNodeMap == null || clientNodeMap.isEmpty()) {
			buf.putInt(0);
		} else {
			buf.putInt(clientNodeMap.size());
			for (Map.Entry<String, String> entry : clientNodeMap.entrySet()) {
				writeString(buf, entry.getKey());
				writeString(buf, entry.getValue());
			}
		}

		if (subscriptionMap == null || subscriptionMap.isEmpty()) {
			buf.putInt(0);
		} else {
			buf.putInt(subscriptionMap.size());
			for (Map.Entry<String, List<Subscribe>> entry : subscriptionMap.entrySet()) {
				writeString(buf, entry.getKey());
				byte[] subsBytes = serializeSubscriptions(entry.getValue());
				buf.putInt(subsBytes.length);
				if (subsBytes.length > 0) {
					buf.put(subsBytes);
				}
			}
		}

		byte[] result = new byte[buf.position()];
		buf.flip();
		buf.get(result);
		return result;
	}

	/**
	 * Deserializes state synchronization data from binary format.
	 *
	 * @param data the binary payload
	 * @return the deserialized {@link StateSyncData} containing client-node mappings and subscriptions
	 */
	public static StateSyncData deserializeStateSyncData(byte[] data) {
		if (data == null || data.length == 0) {
			return new StateSyncData();
		}
		ByteBuffer buf = ByteBuffer.wrap(data);

		int clientNodeMapSize = buf.getInt();
		Map<String, String> clientNodeMap = null;
		if (clientNodeMapSize > 0) {
			clientNodeMap = new HashMap<>(clientNodeMapSize);
			for (int i = 0; i < clientNodeMapSize; i++) {
				String key = readString(buf);
				String value = readString(buf);
				clientNodeMap.put(key, value);
			}
		}

		int subMapSize = buf.getInt();
		Map<String, List<Subscribe>> subscriptionMap = null;
		if (subMapSize > 0) {
			subscriptionMap = new HashMap<>(subMapSize);
			for (int i = 0; i < subMapSize; i++) {
				String key = readString(buf);
				int subLen = buf.getInt();
				List<Subscribe> subs = null;
				if (subLen > 0) {
					byte[] subBytes = new byte[subLen];
					buf.get(subBytes);
					subs = deserializeSubscriptions(subBytes);
				}
				subscriptionMap.put(key, subs);
			}
		}

		return new StateSyncData(clientNodeMap, subscriptionMap);
	}

	private static int calculateSubscriptionsLength(List<Subscribe> subscriptions) {
		int len = 8;
		for (Subscribe sub : subscriptions) {
			len += 2 + stringBytes(sub.getTopicFilter());
			len += 2 + stringBytes(sub.getClientId());
			len += 8;
		}
		return len;
	}

	private static int calculateStateSyncDataLength(Map<String, String> clientNodeMap, Map<String, List<Subscribe>> subscriptionMap) {
		int len = 4;
		if (clientNodeMap != null && !clientNodeMap.isEmpty()) {
			for (Map.Entry<String, String> entry : clientNodeMap.entrySet()) {
				len += 2 + stringBytes(entry.getKey());
				len += 2 + stringBytes(entry.getValue());
			}
		}
		len += 4;
		if (subscriptionMap != null && !subscriptionMap.isEmpty()) {
			for (Map.Entry<String, List<Subscribe>> entry : subscriptionMap.entrySet()) {
				len += 2 + stringBytes(entry.getKey());
				byte[] subsBytes = serializeSubscriptions(entry.getValue());
				len += 4 + subsBytes.length;
			}
		}
		return len;
	}

	private static int stringBytes(String s) {
		return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
	}

	private static void writeString(ByteBuffer buf, String s) {
		if (s == null) {
			buf.putShort((short) -1);
			return;
		}
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		buf.putShort((short) bytes.length);
		buf.put(bytes);
	}

	private static String readString(ByteBuffer buf) {
		short len = buf.getShort();
		if (len == -1) {
			return null;
		}
		byte[] bytes = new byte[len];
		buf.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	/**
	 * Data carrier for state synchronization containing client-node mappings and subscription tables.
	 * <p>
	 * This class is returned by {@link #deserializeStateSyncData(byte[])} to provide structured
	 * access to the deserialized state data during cluster node join synchronization.
	 * </p>
	 *
	 * @author L.cm
	 * @since 1.0.0
	 */
	public static class StateSyncData {
		private Map<String, String> clientNodeMap;
		private Map<String, List<Subscribe>> subscriptionMap;

		public StateSyncData() {
		}

		public StateSyncData(Map<String, String> clientNodeMap, Map<String, List<Subscribe>> subscriptionMap) {
			this.clientNodeMap = clientNodeMap;
			this.subscriptionMap = subscriptionMap;
		}

		/**
		 * Returns the mapping of client identifiers to their hosting node identifiers.
		 *
		 * @return the client-to-node mapping, may be null if not populated
		 */
		public Map<String, String> getClientNodeMap() {
			return clientNodeMap;
		}

		public void setClientNodeMap(Map<String, String> clientNodeMap) {
			this.clientNodeMap = clientNodeMap;
		}

		/**
		 * Returns the mapping of client identifiers to their subscription lists.
		 *
		 * @return the client-to-subscriptions mapping, may be null if not populated
		 */
		public Map<String, List<Subscribe>> getSubscriptionMap() {
			return subscriptionMap;
		}

		public void setSubscriptionMap(Map<String, List<Subscribe>> subscriptionMap) {
			this.subscriptionMap = subscriptionMap;
		}
	}
}
