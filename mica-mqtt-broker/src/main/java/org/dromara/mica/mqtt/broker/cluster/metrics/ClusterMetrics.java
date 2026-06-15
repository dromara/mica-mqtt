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
 * <pre>{@code
 * ClusterMetrics metrics = clusterManager.getMetrics();
 * metrics.sharedDispatchSentInc();   // called every time a SHARED_DISPATCH_TO_CLIENT is sent
 * }</pre>
 * </p>
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

	public long getClusterMessagesSent() {
		return clusterMessagesSent.get();
	}

	public long getClusterMessagesReceived() {
		return clusterMessagesReceived.get();
	}

	public long getClusterSendErrors() {
		return clusterSendErrors.get();
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
			", clusterMessagesSent=" + clusterMessagesSent.get() +
			", clusterMessagesReceived=" + clusterMessagesReceived.get() +
			", clusterSendErrors=" + clusterSendErrors.get() +
			'}';
	}
}
