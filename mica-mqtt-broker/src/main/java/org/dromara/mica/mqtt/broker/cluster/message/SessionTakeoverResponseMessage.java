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
import org.dromara.mica.mqtt.broker.cluster.store.InflightStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * V3 cluster message: response to a {@link SessionTakeoverRequestMessage}.
 * <p>
 * Sent by the previous owner node back to the new owner, carrying the
 * serialized session state in the payload.  The payload format is opaque
 * to the cluster protocol — it is the same byte array that the previous
 * owner loaded from its H2 store (see
 * {@link org.dromara.mica.mqtt.broker.cluster.store.SessionStore}).
 * </p>
 * <p>
 * The {@code status} header signals the outcome:
 * <ul>
 *   <li>{@code "ok"} — payload contains the session bytes; the new owner
 *       should replay inflight messages and resume the subscriptions</li>
 *   <li>{@code "not_found"} — no persistent session for this clientId;
 *       the new owner should start a fresh session</li>
 *   <li>{@code "timeout"} — the previous owner is still processing the
 *       request; the new owner should retry with backoff</li>
 *   <li>{@code "error"} — generic failure; the new owner falls back to V1</li>
 * </ul>
 *
 * @author L.cm
 * @see ClusterMessageType#SESSION_TAKEOVER_RESPONSE
 * @see SessionTakeoverRequestMessage
 * @since 2.6.0
 */
public class SessionTakeoverResponseMessage implements ClusterMessage {
	private static final String HEADER_PAYLOAD_FORMAT = "payloadFormat";
	private static final String PAYLOAD_FORMAT_INFLIGHT_V1 = "session-inflight-v1";
	private static final String PAYLOAD_FORMAT_INFLIGHT_V2 = "session-inflight-v2";
	private static final int MAX_INFLIGHT_ENTRIES = 100_000;

	/** Successful takeover — payload is the session bytes. */
	public static final String STATUS_OK = "ok";
	/** No persistent session found. */
	public static final String STATUS_NOT_FOUND = "not_found";
	/** Owner is busy, retry later. */
	public static final String STATUS_TIMEOUT = "timeout";
	/** Generic failure, fall back to V1. */
	public static final String STATUS_ERROR = "error";

	private String clientId;
	private long attemptId;
	private String status;
	private byte[] sessionBytes;
	private List<InflightStore.InflightEntry> inflightEntries = Collections.emptyList();

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.SESSION_TAKEOVER_RESPONSE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
		headers.put("attemptId", String.valueOf(attemptId));
		headers.put("status", status == null ? STATUS_ERROR : status);
		if (!inflightEntries.isEmpty()) {
			headers.put(HEADER_PAYLOAD_FORMAT, PAYLOAD_FORMAT_INFLIGHT_V2);
		}
	}

	@Override
	public byte[] toPayload() {
		if (inflightEntries.isEmpty()) {
			return sessionBytes == null ? new byte[0] : sessionBytes;
		}
		return encodeTransferPayload();
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		String attemptStr = message.getHeader("attemptId");
		this.attemptId = attemptStr == null ? 0L : Long.parseLong(attemptStr);
		this.status = message.getHeader("status");
		byte[] payload = message.getPayload();
		String payloadFormat = message.getHeader(HEADER_PAYLOAD_FORMAT);
		if (PAYLOAD_FORMAT_INFLIGHT_V1.equals(payloadFormat) || PAYLOAD_FORMAT_INFLIGHT_V2.equals(payloadFormat)) {
			decodeTransferPayload(payload == null ? new byte[0] : payload,
				PAYLOAD_FORMAT_INFLIGHT_V2.equals(payloadFormat));
		} else {
			this.sessionBytes = payload == null ? new byte[0] : payload;
			this.inflightEntries = Collections.emptyList();
		}
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public long getAttemptId() {
		return attemptId;
	}

	public void setAttemptId(long attemptId) {
		this.attemptId = attemptId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public byte[] getSessionBytes() {
		return sessionBytes;
	}

	public void setSessionBytes(byte[] sessionBytes) {
		this.sessionBytes = sessionBytes;
	}

	public List<InflightStore.InflightEntry> getInflightEntries() {
		return inflightEntries;
	}

	public void setInflightEntries(List<InflightStore.InflightEntry> inflightEntries) {
		this.inflightEntries = inflightEntries == null ? Collections.emptyList() : inflightEntries;
	}

	public boolean isOk() {
		return STATUS_OK.equals(status);
	}

	private byte[] encodeTransferPayload() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			byte[] session = sessionBytes == null ? new byte[0] : sessionBytes;
			out.writeInt(session.length);
			out.write(session);
			out.writeInt(inflightEntries.size());
			for (InflightStore.InflightEntry entry : inflightEntries) {
				byte[] topic = entry.getTopic().getBytes(StandardCharsets.UTF_8);
				byte[] payload = entry.getPayload();
				out.writeUTF(entry.getClientId());
				out.writeInt(entry.getPacketId());
				out.writeLong(entry.getExpireAt());
				out.writeInt(entry.getQos());
				out.writeInt(entry.getPhase());
				out.writeInt(topic.length);
				out.write(topic);
				out.writeInt(payload.length);
				out.write(payload);
			}
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to encode session takeover payload", e);
		}
	}

	private void decodeTransferPayload(byte[] payload, boolean includesPhase) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
			int sessionLength = readLength(in, payload.length);
			byte[] session = new byte[sessionLength];
			in.readFully(session);
			int count = in.readInt();
			if (count < 0 || count > MAX_INFLIGHT_ENTRIES) {
				throw new IOException("Invalid inflight entry count: " + count);
			}
			List<InflightStore.InflightEntry> entries = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				String entryClientId = in.readUTF();
				int packetId = in.readInt();
				long expireAt = in.readLong();
				int qos = in.readInt();
				int phase = includesPhase ? in.readInt() : InflightStore.PHASE_PUBLISH;
				int topicLength = readLength(in, in.available());
				byte[] topic = new byte[topicLength];
				in.readFully(topic);
				int payloadLength = readLength(in, in.available());
				byte[] entryPayload = new byte[payloadLength];
				in.readFully(entryPayload);
				entries.add(new InflightStore.InflightEntry(entryClientId, packetId, expireAt,
					new String(topic, StandardCharsets.UTF_8), entryPayload, qos, phase));
			}
			this.sessionBytes = session;
			this.inflightEntries = entries;
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid session takeover payload", e);
		}
	}

	private static int readLength(DataInputStream in, int maximum) throws IOException {
		int length = in.readInt();
		if (length < 0 || length > maximum) {
			throw new IOException("Invalid payload length: " + length);
		}
		return length;
	}
}
