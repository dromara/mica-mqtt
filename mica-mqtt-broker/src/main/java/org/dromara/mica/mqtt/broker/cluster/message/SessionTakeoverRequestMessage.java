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
 * V3 cluster message: request to take over a session owned by another node.
 * <p>
 * Sent from the node that received a new MQTT CONNECT for a client whose session
 * is currently owned by another node (sticky-session failure).  The previous
 * owner should respond with a {@link SessionTakeoverResponseMessage} carrying
 * the serialized session state (subscriptions, inflight messages, expiry).
 * </p>
 * <p>
 * <strong>Idempotency</strong>: the request carries an {@code attemptId} that is
 * echoed back in the response so that the new owner can match replies and drop
 * stale duplicates (e.g. when both nodes race to be the new owner).
 * </p>
 * <p>
 * <strong>Timeout</strong>: the request carries a {@code timeoutMs} value so
 * that the previous owner can bound how long it holds the session lock while
 * the takeover completes.  If the timeout elapses without a successful response,
 * the new owner falls back to V1 behavior (treat the session as a fresh start).
 * </p>
 *
 * @author L.cm
 * @see ClusterMessageType#SESSION_TAKEOVER_REQUEST
 * @see SessionTakeoverResponseMessage
 * @since 2.6.0
 */
public class SessionTakeoverRequestMessage implements ClusterMessage {

	private String clientId;
	private long attemptId;
	private long timeoutMs;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.SESSION_TAKEOVER_REQUEST;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(ClusterMessageSerializer.HEADER_CLIENT_ID, clientId);
		headers.put("attemptId", String.valueOf(attemptId));
		headers.put(ClusterMessageSerializer.HEADER_TIMEOUT, String.valueOf(timeoutMs));
	}

	@Override
	public byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(ClusterMessageSerializer.HEADER_CLIENT_ID);
		String attemptStr = message.getHeader("attemptId");
		this.attemptId = attemptStr == null ? 0L : Long.parseLong(attemptStr);
		String timeoutStr = message.getHeader(ClusterMessageSerializer.HEADER_TIMEOUT);
		this.timeoutMs = timeoutStr == null ? 5_000L : Long.parseLong(timeoutStr);
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

	public long getTimeoutMs() {
		return timeoutMs;
	}

	public void setTimeoutMs(long timeoutMs) {
		this.timeoutMs = timeoutMs;
	}
}