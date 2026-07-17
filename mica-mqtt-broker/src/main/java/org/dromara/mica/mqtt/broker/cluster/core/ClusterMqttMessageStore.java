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

import org.dromara.mica.mqtt.broker.cluster.message.RetainMessageNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.message.WillMessageNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.store.RetainIndex;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decorator for {@link IMqttMessageStore} that synchronizes will and retained messages across cluster nodes.
 * <p>
 * This store wraps an existing {@link IMqttMessageStore} and extends it with
 * cluster synchronization. When a will message is set or a retained message is
 * published/cleared, it broadcasts the change to all other cluster nodes.
 * </p>
 *
 * <h2>V3 retain persistence (P2.4)</h2>
 * <p>
 * When a {@link RetainIndex} is wired in via {@link #setRetainIndex(RetainIndex)},
 * retain messages are written to the durable store as well as the in-memory
 * delegate.  Read-side ({@link #getRetainMessage}) prefers the in-memory delegate
 * (which is the broadcast-replicated copy) and falls back to the durable index
 * when the delegate is empty (e.g. after a restart before the first sync).
 * </p>
 *
 * @author L.cm
 * @see IMqttMessageStore
 * @since 1.0.0
 */
public class ClusterMqttMessageStore implements IMqttMessageStore {
	private static final Logger logger = LoggerFactory.getLogger(ClusterMqttMessageStore.class);

	private final IMqttMessageStore delegate;
	private final MqttClusterManager clusterManager;
	private volatile RetainIndex retainIndex;

	public ClusterMqttMessageStore(IMqttMessageStore delegate, MqttClusterManager clusterManager) {
		this.delegate = delegate;
		this.clusterManager = clusterManager;
	}

	/**
	 * Wires the V3 retain index.  When set, all retain add/clear operations are
	 * mirrored to the durable index; reads also consult the index as a fallback.
	 *
	 * @param retainIndex the retain index; may be {@code null} to disable
	 */
	public void setRetainIndex(RetainIndex retainIndex) {
		this.retainIndex = retainIndex;
	}

	@Override
	public boolean addWillMessage(String clientId, Message message) {
		delegate.addWillMessage(clientId, message);
		if (clusterManager.isClusterEnabled()) {
			WillMessageNotifyMessage willMsg = new WillMessageNotifyMessage();
			willMsg.setClientId(clientId);
			willMsg.setWillMessage(message);
			clusterManager.broadcast(willMsg);
			logger.debug("[Cluster] Broadcasted will message for clientId: {}", clientId);
		}
		return true;
	}

	@Override
	public boolean clearWillMessage(String clientId) {
		boolean cleared = delegate.clearWillMessage(clientId);
		if (clusterManager.isClusterEnabled()) {
			WillMessageNotifyMessage willMsg = new WillMessageNotifyMessage();
			willMsg.setClientId(clientId);
			willMsg.setWillMessage(null);
			clusterManager.broadcast(willMsg);
		}
		return cleared;
	}

	@Override
	public Message getWillMessage(String clientId) {
		return delegate.getWillMessage(clientId);
	}

	@Override
	public boolean addRetainMessage(String topic, int timeout, Message message) {
		if (isRetainShardingActive()) {
			List<String> replicas = clusterManager.getRetainReplicaNodes(topic);
			if (replicas.contains(clusterManager.getLocalNodeId())) {
				addRetainMessageLocal(topic, timeout, message);
			}
			RetainMessageNotifyMessage retainMsg = new RetainMessageNotifyMessage();
			retainMsg.setTopic(topic);
			retainMsg.setTimeout(timeout);
			retainMsg.setRetainMessage(message);
			for (String replica : replicas) {
				if (!clusterManager.getLocalNodeId().equals(replica)) {
					clusterManager.sendToNode(replica, retainMsg);
				}
			}
			return true;
		}
		delegate.addRetainMessage(topic, timeout, message);
		// V3 persistence: mirror to durable retain index when storage is enabled
		if (retainIndex != null) {
			retainIndex.put(topic, message, timeout);
		}
		if (clusterManager.isClusterEnabled()) {
			RetainMessageNotifyMessage retainMsg = new RetainMessageNotifyMessage();
			retainMsg.setTopic(topic);
			retainMsg.setTimeout(timeout);
			retainMsg.setRetainMessage(message);
			clusterManager.broadcast(retainMsg);
			logger.debug("[Cluster] Broadcasted retain message for topic: {}", topic);
		}
		return true;
	}

	@Override
	public boolean clearRetainMessage(String topic) {
		delegate.clearRetainMessage(topic);
		// V3 persistence: remove from durable retain index
		if (retainIndex != null) {
			retainIndex.remove(topic);
		}
		if (clusterManager.isClusterEnabled()) {
			RetainMessageNotifyMessage retainMsg = new RetainMessageNotifyMessage();
			retainMsg.setTopic(topic);
			retainMsg.setTimeout(0);
			retainMsg.setRetainMessage(null);
			clusterManager.broadcast(retainMsg);
			logger.debug("[Cluster] Broadcasted retain clear for topic: {}", topic);
		}
		return true;
	}

	@Override
	public List<Message> getRetainMessage(String topicFilter) {
		if (isRetainShardingActive()) {
			Map<String, Message> byTopic = new LinkedHashMap<>();
			for (Message message : getRetainMessageLocal(topicFilter)) {
				byTopic.put(message.getTopic(), message);
			}
			for (Message message : clusterManager.queryRemoteRetain(
				topicFilter, clusterManager.getRetainQueryTimeoutMs())) {
				byTopic.put(message.getTopic(), message);
			}
			return new ArrayList<>(byTopic.values());
		}
		return getRetainMessageLocal(topicFilter);
	}

	public List<Message> getRetainMessageLocal(String topicFilter) {
		List<Message> messages = delegate.getRetainMessage(topicFilter);
		if ((messages == null || messages.isEmpty()) && retainIndex != null) {
			// Fallback to durable index when the in-memory delegate has nothing
			// (e.g. just after a node restart before the first broadcast has arrived).
			messages = retainIndex.match(topicFilter);
			if (messages == null) {
				return new ArrayList<>(0);
			}
		}
		return messages == null ? new ArrayList<>(0) : messages;
	}

	public List<Message> getAllRetainMessageLocal() {
		RetainIndex index = retainIndex;
		return index == null ? new ArrayList<>(0) : index.snapshot();
	}

	public List<RetainIndex.RetainEntry> getAllRetainEntriesLocal() {
		RetainIndex index = retainIndex;
		return index == null ? new ArrayList<>(0) : index.snapshotEntries();
	}

	private boolean isRetainShardingActive() {
		return clusterManager.isRetainShardingEnabled() && retainIndex != null;
	}

	/**
	 * 仅本地存储遗嘱消息，不广播到集群。
	 * 用于处理从其他节点接收到的集群消息，避免无限广播。
	 *
	 * @param clientId 客户端标识
	 * @param message  遗嘱消息
	 * @return 是否成功
	 */
	public boolean addWillMessageLocal(String clientId, Message message) {
		return delegate.addWillMessage(clientId, message);
	}

	/**
	 * 仅本地清除遗嘱消息，不广播到集群。
	 *
	 * @param clientId 客户端标识
	 * @return 是否成功
	 */
	public boolean clearWillMessageLocal(String clientId) {
		return delegate.clearWillMessage(clientId);
	}

	/**
	 * 仅本地存储保留消息，不广播到集群。
	 * 用于处理从其他节点接收到的集群消息，避免无限广播。
	 *
	 * @param topic   主题
	 * @param timeout 过期时间
	 * @param message 保留消息
	 * @return 是否成功
	 */
	public boolean addRetainMessageLocal(String topic, int timeout, Message message) {
		boolean ok = delegate.addRetainMessage(topic, timeout, message);
		// V3 persistence: also write to durable index when receiving retain from peer
		if (retainIndex != null && ok && message != null) {
			retainIndex.put(topic, message, timeout);
		}
		return ok;
	}

	/**
	 * 仅本地清除保留消息，不广播到集群。
	 *
	 * @param topic 主题
	 * @return 是否成功
	 */
	public boolean clearRetainMessageLocal(String topic) {
		boolean ok = delegate.clearRetainMessage(topic);
		// V3 persistence: also remove from durable index
		if (retainIndex != null && ok) {
			retainIndex.remove(topic);
		}
		return ok;
	}

	@Override
	public void clean() throws java.io.IOException {
		delegate.clean();
	}
}
