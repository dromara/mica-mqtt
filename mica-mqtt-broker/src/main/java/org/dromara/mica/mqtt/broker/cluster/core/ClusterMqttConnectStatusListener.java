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

	@Override
	public void online(ChannelContext context, String clientId, String username) {
		// 连接已失效时不接管归属、不广播上线。
		// clientNodeMap 里的远端归属由 CLIENT_CONNECT / CLIENT_DISCONNECT 广播维护，是当前事实：
		// 若此时清掉它，本节点会误以为该 client 归自己，getRemoteNodesWithSubscriber 便不再把它
		// 当作远端订阅者，导致本节点发布的消息不再转发给真正的 owner（消息丢失）。
		// 归属仍归远端时漏广播下线也是对的 —— 远端会在自己的客户端断开时广播下线。
		if (context != null && (!context.isAccepted() || context.isClosed() || context.isRemoved())) {
			logger.info("[Cluster] online() skipped, connection is not alive, clientId:{}", clientId);
			return;
		}
		// 接管本地归属：清掉 clientId 残留的远端归属，并记下原 owner 用于 session takeover。
		// 先登记再回调 delegate，保证 delegate 抛异常时归属依然自洽。
		String previousOwner = sessionManager == null ? null : sessionManager.registerLocalClient(clientId);
		if (delegate != null) {
			delegate.online(context, clientId, username);
		}

		// V3 takeover: if the clientId is already mapped to another node, request
		// takeover before broadcasting the new connect.  The previous owner
		// responds with a SessionTakeoverResponse carrying the persisted session
		// bytes; the new owner then broadcasts SessionMigratedNotify to all
		// peers to update routing.
		if (previousOwner != null && !previousOwner.isEmpty()) {
			logger.info("[Cluster] online() detected previous owner {} for client {}, requesting takeover",
				previousOwner, clientId);
			clusterManager.initiateSessionTakeover(clientId, previousOwner, DEFAULT_TAKEOVER_TIMEOUT_MS);
		}

		ClientConnectMessage msg = new ClientConnectMessage();
		msg.setClientId(clientId);
		clusterManager.broadcast(msg);
	}

	@Override
	public void offline(ChannelContext context, String clientId, String username, String reason) {
		if (delegate != null) {
			delegate.offline(context, clientId, username, reason);
		}

		// A displaced socket no longer owns this clientId. Its offline notification
		// must not erase the replacement's subscriptions on other nodes.
		// 归属判据：clientNodeMap 里记录的是「当前 owner」，本机不是 owner 时不广播下线，
		// 否则新 owner 的订阅会被旧连接的迟到下线清掉。
		String owner = sessionManager == null ? null : sessionManager.getClientNode(clientId);
		if (owner != null && !owner.equals(clusterManager.getLocalNodeId())) {
			return;
		}
		// 被顶替下线的连接（互踢 / 跨节点接管）不广播下线：新连接上线时会广播上线。
		if (context != null && context.getCloseCode() == ChannelContext.CloseCode.KICK_EACH_OTHER) {
			return;
		}
		ClientDisconnectMessage msg = new ClientDisconnectMessage();
		msg.setClientId(clientId);
		clusterManager.broadcast(msg);
	}

}
