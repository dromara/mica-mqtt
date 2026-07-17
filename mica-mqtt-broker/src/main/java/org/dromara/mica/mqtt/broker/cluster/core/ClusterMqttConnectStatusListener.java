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

package org.dromara.mica.mqtt.broker.cluster.core;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator for {@link IMqttConnectStatusListener} that broadcasts client lifecycle events to the cluster.
 * <p>
 * This listener wraps an existing {@link IMqttConnectStatusListener} and extends it with
 * cluster awareness. When a client connects or disconnects, it broadcasts a notification
 * to all other cluster nodes so they can update their client-to-node mappings.
 * </p>
 *
 * <h2>V3 session takeover (P2.1)</h2>
 * <p>
 * When a client connects and another node is recorded as its previous owner in
 * {@link ClusterMqttSessionManager#getClientNodeMap()}, this listener triggers
 * a session takeover via {@link MqttClusterManager#initiateSessionTakeover}.
 * </p>
 *
 * @author L.cm
 * @see IMqttConnectStatusListener
 * @since 1.0.0
 */
public class ClusterMqttConnectStatusListener implements IMqttConnectStatusListener {
	private static final Logger logger = LoggerFactory.getLogger(ClusterMqttConnectStatusListener.class);

	/** Default takeover timeout (matches the one used in MqttClusterManager). */
	private static final long DEFAULT_TAKEOVER_TIMEOUT_MS = 5_000L;

	private final IMqttConnectStatusListener delegate;
	private final MqttClusterManager clusterManager;
	private ClusterMqttSessionManager sessionManager;
	private TimerTaskService taskService;

	public ClusterMqttConnectStatusListener(IMqttConnectStatusListener delegate, MqttClusterManager clusterManager) {
		this.delegate = delegate;
		this.clusterManager = clusterManager;
	}

	/**
	 * Wires the session manager so the listener can detect previous-owner
	 * connections and trigger session takeover (P2.1).
	 *
	 * @param sessionManager the cluster session manager used to look up the
	 *                       previous owner of a clientId
	 */
	public void setSessionManager(ClusterMqttSessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	public void setTaskService(TimerTaskService taskService) {
		this.taskService = taskService;
	}

	@Override
	public void online(ChannelContext context, String clientId, String username) {
		if (delegate != null) {
			delegate.online(context, clientId, username);
		}

		// V3 takeover: if the clientId is already mapped to another node, request
		// takeover before broadcasting the new connect.  The previous owner
		// responds with a SessionTakeoverResponse carrying the persisted session
		// bytes; the new owner then broadcasts SessionMigratedNotify to all
		// peers to update routing.
		String previousOwner = lookupPreviousOwner(clientId);
		if (previousOwner != null && !previousOwner.isEmpty()) {
			logger.info("[Cluster] online() detected previous owner {} for client {}, requesting takeover",
				previousOwner, clientId);
			clusterManager.initiateSessionTakeover(clientId, previousOwner, DEFAULT_TAKEOVER_TIMEOUT_MS);
		}
		if (sessionManager != null) {
			sessionManager.markLocalClient(clientId);
			if (previousOwner == null || previousOwner.isEmpty()) {
				int replayed = sessionManager.replayInflight(context, clientId, taskService);
				clusterManager.getMetrics().inflightReplayedAdd(replayed);
			}
		}

		ClientConnectMessage msg = new ClientConnectMessage();
		msg.setClientId(clientId);
		clusterManager.broadcast(msg);
		clusterManager.getMetrics().clientConnectBroadcastInc();
	}

	@Override
	public void offline(ChannelContext context, String clientId, String username, String reason) {
		if (delegate != null) {
			delegate.offline(context, clientId, username, reason);
		}

		ClientDisconnectMessage msg = new ClientDisconnectMessage();
		msg.setClientId(clientId);
		msg.setPersistentSession(sessionManager != null && sessionManager.isPersistentSession(clientId));
		clusterManager.broadcast(msg);
		clusterManager.getMetrics().clientDisconnectBroadcastInc();
	}

	private String lookupPreviousOwner(String clientId) {
		if (sessionManager == null) {
			return null;
		}
		return sessionManager.getClientNodeMap().get(clientId);
	}
}
