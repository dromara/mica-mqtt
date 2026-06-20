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

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.SESSION_TAKEOVER_RESPONSE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
		headers.put("attemptId", String.valueOf(attemptId));
		headers.put("status", status == null ? STATUS_ERROR : status);
	}

	@Override
	public byte[] toPayload() {
		return sessionBytes == null ? new byte[0] : sessionBytes;
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		String attemptStr = message.getHeader("attemptId");
		this.attemptId = attemptStr == null ? 0L : Long.parseLong(attemptStr);
		this.status = message.getHeader("status");
		byte[] payload = message.getPayload();
		this.sessionBytes = payload == null ? new byte[0] : payload;
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

	public boolean isOk() {
		return STATUS_OK.equals(status);
	}
}