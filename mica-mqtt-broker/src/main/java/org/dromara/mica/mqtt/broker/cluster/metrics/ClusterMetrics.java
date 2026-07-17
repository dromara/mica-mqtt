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

package org.dromara.mica.mqtt.broker.cluster.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight metrics counters for MQTT broker cluster operations.
 * <p>
 * This class provides simple, lock-free counters and gauges that downstream
 * components (dispatcher, session manager, store) can increment in the hot path
 * without significant overhead.  The counters are deliberately minimal — they do
 * not integrate with Micrometer or any external metrics system out of the box, but
 * they expose the raw values so that Spring Boot Actuator endpoints or custom
 * exporters can read them.
 * </p>
 * <p>
 * Usage in components:
 * </p>
 * <pre>
 * ClusterMetrics metrics = clusterManager.getMetrics();
 * metrics.sharedDispatchSentInc();   // called every time a SHARED_DISPATCH_TO_CLIENT is sent
 * </pre>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class ClusterMetrics {

	// ---- Message forwarding counters -----------------------------------------------

	/** Total PUBLISH_FORWARD messages sent to remote nodes (normal subscriptions). */
	private final AtomicLong publishForwardSent = new AtomicLong();

	/** Total SHARED_DISPATCH_TO_CLIENT messages sent for shared subscriptions. */
	private final AtomicLong sharedDispatchSent = new AtomicLong();

	/** Total SHARED_DISPATCH_TO_CLIENT messages received and delivered locally. */
	private final AtomicLong sharedDispatchReceived = new AtomicLong();

	/**
	 * Total shared-dispatch messages that had to be re-picked because the original
	 * target was no longer connected when the message arrived.
	 */
	private final AtomicLong sharedDispatchRepick = new AtomicLong();

	/** Total shared-dispatch messages dropped (no active subscriber found during re-pick). */
	private final AtomicLong sharedDispatchDropped = new AtomicLong();

	// ---- Session counters ----------------------------------------------------------

	/** Total state sync requests sent (new node joining). */
	private final AtomicLong stateSyncRequests = new AtomicLong();

	/** Total state sync responses received. */
	private final AtomicLong stateSyncResponses = new AtomicLong();

	/** Total CLIENT_CONNECT messages broadcast. */
	private final AtomicLong clientConnectBroadcast = new AtomicLong();

	/** Total CLIENT_DISCONNECT messages broadcast. */
	private final AtomicLong clientDisconnectBroadcast = new AtomicLong();

	/** Total durable inflight PUBLISH packets replayed after reconnect/takeover. */
	private final AtomicLong inflightReplayed = new AtomicLong();

	/** Total cross-node session takeover attempts started. */
	private final AtomicLong sessionTakeoverStarted = new AtomicLong();
	/** Total cross-node session takeovers completed successfully. */
	private final AtomicLong sessionTakeoverSucceeded = new AtomicLong();
	/** Total cross-node session takeover responses that could not be installed. */
	private final AtomicLong sessionTakeoverFailed = new AtomicLong();
	/** Total cross-node session takeover attempts that timed out. */
	private final AtomicLong sessionTakeoverTimedOut = new AtomicLong();

	// ---- Retain and membership counters -------------------------------------------

	/** Total retained-message replica notifications received. */
	private final AtomicLong retainReplicaReceived = new AtomicLong();
	/** Total retained-message query requests initiated. */
	private final AtomicLong retainQueryRequests = new AtomicLong();
	/** Total retained-message queries that did not receive every expected response. */
	private final AtomicLong retainQueryTimedOut = new AtomicLong();
	/** Cumulative end-to-end retained-message replica latency in milliseconds. */
	private final AtomicLong retainReplicaLatencyMillis = new AtomicLong();
	/** Number of replica latency samples contributing to the cumulative value. */
	private final AtomicLong retainReplicaLatencySamples = new AtomicLong();
	/** Total remote-node departures detected by notification or membership polling. */
	private final AtomicLong nodeDepartures = new AtomicLong();

	// ---- Cluster-level counters ----------------------------------------------------

	/** Total cluster messages successfully sent (any type). */
	private final AtomicLong clusterMessagesSent = new AtomicLong();

	/** Total cluster messages received (any type). */
	private final AtomicLong clusterMessagesReceived = new AtomicLong();

	/** Total cluster messages that could not be sent due to errors. */
	private final AtomicLong clusterSendErrors = new AtomicLong();

	// ---- Increment helpers ---------------------------------------------------------

	public void publishForwardSentInc() {
		publishForwardSent.incrementAndGet();
	}

	public void sharedDispatchSentInc() {
		sharedDispatchSent.incrementAndGet();
	}

	public void sharedDispatchReceivedInc() {
		sharedDispatchReceived.incrementAndGet();
	}

	public void sharedDispatchRepickInc() {
		sharedDispatchRepick.incrementAndGet();
	}

	public void sharedDispatchDroppedInc() {
		sharedDispatchDropped.incrementAndGet();
	}

	public void stateSyncRequestsInc() {
		stateSyncRequests.incrementAndGet();
	}

	public void stateSyncResponsesInc() {
		stateSyncResponses.incrementAndGet();
	}

	public void clientConnectBroadcastInc() {
		clientConnectBroadcast.incrementAndGet();
	}

	public void clientDisconnectBroadcastInc() {
		clientDisconnectBroadcast.incrementAndGet();
	}

	public void inflightReplayedAdd(long count) {
		if (count > 0) {
			inflightReplayed.addAndGet(count);
		}
	}

	public void sessionTakeoverStartedInc() {
		sessionTakeoverStarted.incrementAndGet();
	}

	public void sessionTakeoverSucceededInc() {
		sessionTakeoverSucceeded.incrementAndGet();
	}

	public void sessionTakeoverFailedInc() {
		sessionTakeoverFailed.incrementAndGet();
	}

	public void sessionTakeoverTimedOutInc() {
		sessionTakeoverTimedOut.incrementAndGet();
	}

	public void retainReplicaReceived(long latencyMillis) {
		retainReplicaReceived.incrementAndGet();
		if (latencyMillis >= 0L) {
			retainReplicaLatencyMillis.addAndGet(latencyMillis);
			retainReplicaLatencySamples.incrementAndGet();
		}
	}

	public void retainQueryRequestsInc() {
		retainQueryRequests.incrementAndGet();
	}

	public void retainQueryTimedOutInc() {
		retainQueryTimedOut.incrementAndGet();
	}

	public void nodeDeparturesInc() {
		nodeDepartures.incrementAndGet();
	}

	public void clusterMessagesSentInc() {
		clusterMessagesSent.incrementAndGet();
	}

	public void clusterMessagesReceivedInc() {
		clusterMessagesReceived.incrementAndGet();
	}

	public void clusterSendErrorsInc() {
		clusterSendErrors.incrementAndGet();
	}

	// ---- Read accessors ------------------------------------------------------------

	public long getPublishForwardSent() {
		return publishForwardSent.get();
	}

	public long getSharedDispatchSent() {
		return sharedDispatchSent.get();
	}

	public long getSharedDispatchReceived() {
		return sharedDispatchReceived.get();
	}

	public long getSharedDispatchRepick() {
		return sharedDispatchRepick.get();
	}

	public long getSharedDispatchDropped() {
		return sharedDispatchDropped.get();
	}

	public long getStateSyncRequests() {
		return stateSyncRequests.get();
	}

	public long getStateSyncResponses() {
		return stateSyncResponses.get();
	}

	public long getClientConnectBroadcast() {
		return clientConnectBroadcast.get();
	}

	public long getClientDisconnectBroadcast() {
		return clientDisconnectBroadcast.get();
	}

	public long getInflightReplayed() {
		return inflightReplayed.get();
	}

	public long getSessionTakeoverStarted() {
		return sessionTakeoverStarted.get();
	}

	public long getSessionTakeoverSucceeded() {
		return sessionTakeoverSucceeded.get();
	}

	public long getSessionTakeoverFailed() {
		return sessionTakeoverFailed.get();
	}

	public long getSessionTakeoverTimedOut() {
		return sessionTakeoverTimedOut.get();
	}

	public long getRetainReplicaReceived() {
		return retainReplicaReceived.get();
	}

	public long getRetainQueryRequests() {
		return retainQueryRequests.get();
	}

	public long getRetainQueryTimedOut() {
		return retainQueryTimedOut.get();
	}

	public long getRetainReplicaLatencyMillis() {
		return retainReplicaLatencyMillis.get();
	}

	public long getRetainReplicaLatencySamples() {
		return retainReplicaLatencySamples.get();
	}

	public long getNodeDepartures() {
		return nodeDepartures.get();
	}

	public long getClusterMessagesSent() {
		return clusterMessagesSent.get();
	}

	public long getClusterMessagesReceived() {
		return clusterMessagesReceived.get();
	}

	public long getClusterSendErrors() {
		return clusterSendErrors.get();
	}

	/**
	 * Returns all counters as a name → value map, suitable for export to
	 * monitoring systems (Prometheus exporter, Spring Boot Actuator
	 * {@code /actuator/metrics}, or a JSON status endpoint).
	 * <p>
	 * The returned map is a defensive copy with stable ordering.  Values are
	 * snapshots — they may be stale by the time the caller inspects them.
	 * </p>
	 *
	 * @return immutable snapshot of all counters
	 */
	public Map<String, Long> snapshot() {
		Map<String, Long> snap = new LinkedHashMap<>();
		snap.put("publishForwardSent", publishForwardSent.get());
		snap.put("sharedDispatchSent", sharedDispatchSent.get());
		snap.put("sharedDispatchReceived", sharedDispatchReceived.get());
		snap.put("sharedDispatchRepick", sharedDispatchRepick.get());
		snap.put("sharedDispatchDropped", sharedDispatchDropped.get());
		snap.put("stateSyncRequests", stateSyncRequests.get());
		snap.put("stateSyncResponses", stateSyncResponses.get());
		snap.put("clientConnectBroadcast", clientConnectBroadcast.get());
		snap.put("clientDisconnectBroadcast", clientDisconnectBroadcast.get());
		snap.put("inflightReplayed", inflightReplayed.get());
		snap.put("sessionTakeoverStarted", sessionTakeoverStarted.get());
		snap.put("sessionTakeoverSucceeded", sessionTakeoverSucceeded.get());
		snap.put("sessionTakeoverFailed", sessionTakeoverFailed.get());
		snap.put("sessionTakeoverTimedOut", sessionTakeoverTimedOut.get());
		snap.put("retainReplicaReceived", retainReplicaReceived.get());
		snap.put("retainQueryRequests", retainQueryRequests.get());
		snap.put("retainQueryTimedOut", retainQueryTimedOut.get());
		snap.put("retainReplicaLatencyMillis", retainReplicaLatencyMillis.get());
		snap.put("retainReplicaLatencySamples", retainReplicaLatencySamples.get());
		snap.put("nodeDepartures", nodeDepartures.get());
		snap.put("clusterMessagesSent", clusterMessagesSent.get());
		snap.put("clusterMessagesReceived", clusterMessagesReceived.get());
		snap.put("clusterSendErrors", clusterSendErrors.get());
		return snap;
	}

	/**
	 * Returns a Prometheus-format text dump of all counters.  Useful for
	 * implementing a {@code /metrics} HTTP endpoint without depending on the
	 * Micrometer library.
	 * <p>
	 * Example output:
	 * </p>
	 * <pre>
	 * # HELP mqtt_cluster_publish_forward_sent_total Total PUBLISH_FORWARD messages sent
	 * # TYPE mqtt_cluster_publish_forward_sent_total counter
	 * mqtt_cluster_publish_forward_sent_total 1234
	 * </pre>
	 *
	 * @return Prometheus text format
	 */
	public String toPrometheus() {
		StringBuilder sb = new StringBuilder(2048);
		Map<String, Long> snap = snapshot();
		for (Map.Entry<String, Long> entry : snap.entrySet()) {
			// Prometheus convention: counter metrics should end with _total
			String metricName = "mqtt_cluster_" + camelToSnake(entry.getKey()) + "_total";
			sb.append("# HELP ").append(metricName).append(" Cluster metric ").append(entry.getKey()).append('\n');
			sb.append("# TYPE ").append(metricName).append(" counter\n");
			sb.append(metricName).append(' ').append(entry.getValue()).append('\n');
		}
		return sb.toString();
	}

	private static String camelToSnake(String camel) {
		StringBuilder sb = new StringBuilder(camel.length() + 4);
		for (int i = 0; i < camel.length(); i++) {
			char c = camel.charAt(i);
			if (i > 0 && Character.isUpperCase(c)) {
				sb.append('_');
			}
			sb.append(Character.toLowerCase(c));
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return "ClusterMetrics{" +
			"publishForwardSent=" + publishForwardSent.get() +
			", sharedDispatchSent=" + sharedDispatchSent.get() +
			", sharedDispatchReceived=" + sharedDispatchReceived.get() +
			", sharedDispatchRepick=" + sharedDispatchRepick.get() +
			", sharedDispatchDropped=" + sharedDispatchDropped.get() +
			", stateSyncRequests=" + stateSyncRequests.get() +
			", stateSyncResponses=" + stateSyncResponses.get() +
			", inflightReplayed=" + inflightReplayed.get() +
			", sessionTakeoverStarted=" + sessionTakeoverStarted.get() +
			", sessionTakeoverSucceeded=" + sessionTakeoverSucceeded.get() +
			", sessionTakeoverFailed=" + sessionTakeoverFailed.get() +
			", sessionTakeoverTimedOut=" + sessionTakeoverTimedOut.get() +
			", retainReplicaReceived=" + retainReplicaReceived.get() +
			", retainQueryRequests=" + retainQueryRequests.get() +
			", retainQueryTimedOut=" + retainQueryTimedOut.get() +
			", retainReplicaLatencyMillis=" + retainReplicaLatencyMillis.get() +
			", retainReplicaLatencySamples=" + retainReplicaLatencySamples.get() +
			", nodeDepartures=" + nodeDepartures.get() +
			", clusterMessagesSent=" + clusterMessagesSent.get() +
			", clusterMessagesReceived=" + clusterMessagesReceived.get() +
			", clusterSendErrors=" + clusterSendErrors.get() +
			'}';
	}
}
