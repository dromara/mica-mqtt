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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

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
	private final ConcurrentMap<String, String> willTokens = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, WillVersion> willVersions = new ConcurrentHashMap<>();
	private final AtomicLong willClock = new AtomicLong();
	// 按客户端分片，保证同一连接的保存和清理通知顺序
	private final Object[] willLocks = new Object[64];
	private volatile RetainIndex retainIndex;

	public ClusterMqttMessageStore(IMqttMessageStore delegate, MqttClusterManager clusterManager) {
		this.delegate = delegate;
		this.clusterManager = clusterManager;
		for (int i = 0; i < willLocks.length; i++) {
			willLocks[i] = new Object();
		}
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
		if (clientId == null || clientId.isEmpty()) {
			return false;
		}
		synchronized (willLock(clientId)) {
			boolean added = delegate.addWillMessage(clientId, message);
			if (added) {
				String token = UUID.randomUUID().toString();
				long version = nextWillVersion();
				willTokens.put(clientId, token);
				willVersions.put(clientId, new WillVersion(version, token, false));
				broadcastWill(clientId, message, token, version, false);
			}
			return added;
		}
	}

	@Override
	public boolean clearWillMessage(String clientId) {
		if (clientId == null || clientId.isEmpty()) {
			return false;
		}
		synchronized (willLock(clientId)) {
			willTokens.remove(clientId);
			boolean removed = delegate.clearWillMessage(clientId);
			String token = UUID.randomUUID().toString();
			long version = nextWillVersion();
			willVersions.put(clientId, new WillVersion(version, token, true));
			broadcastWill(clientId, null, token, version, true);
			return removed;
		}
	}

	@Override
	public boolean clearWillMessage(String clientId, Message expected) {
		if (clientId == null || clientId.isEmpty()) {
			return false;
		}
		synchronized (willLock(clientId)) {
			boolean removed = delegate.clearWillMessage(clientId, expected);
			if (removed) {
				String token = willTokens.remove(clientId);
				if (token != null) {
					WillVersion version = willVersions.get(clientId);
					long sequence = version == null ? 0 : version.sequence;
					if (version != null && token.equals(version.token)) {
						willVersions.put(clientId, new WillVersion(sequence, token, true));
					}
					broadcastWill(clientId, null, token, sequence, true);
				}
			}
			return removed;
		}
	}

	/**
	 * 获取客户端对应的遗嘱操作锁。
	 *
	 * @param clientId 客户端标识
	 * @return 分片锁
	 */
	private Object willLock(String clientId) {
		return willLocks[(clientId.hashCode() & Integer.MAX_VALUE) % willLocks.length];
	}

	private long nextWillVersion() {
		return willClock.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()));
	}

	private void broadcastWill(String clientId, Message message, String token, long version, boolean clear) {
		if (!clusterManager.isClusterEnabled()) {
			return;
		}
		WillMessageNotifyMessage notification = new WillMessageNotifyMessage();
		notification.setClientId(clientId);
		notification.setWillMessage(message);
		notification.setWillToken(token);
		notification.setWillVersion(version);
		notification.setClear(clear);
		clusterManager.broadcast(notification);
	}

	@Override
	public Message getWillMessage(String clientId) {
		if (clientId == null || clientId.isEmpty()) {
			return null;
		}
		return delegate.getWillMessage(clientId);
	}

	@Override
	public boolean addRetainMessage(String topic, int timeout, Message message) {
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
		List<Message> messages = delegate.getRetainMessage(topicFilter);
		if ((messages == null || messages.isEmpty()) && retainIndex != null) {
			// Fallback to durable index when the in-memory delegate has nothing
			// (e.g. just after a node restart before the first broadcast has arrived).
			messages = retainIndex.match(topicFilter);
			if (messages == null) {
				return new ArrayList<>(0);
			}
		}
		return messages;
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
		if (clientId == null || clientId.isEmpty()) {
			return false;
		}
		synchronized (willLock(clientId)) {
			willTokens.remove(clientId);
			return delegate.addWillMessage(clientId, message);
		}
	}

	/**
	 * 应用远端遗嘱通知；清理时仅移除版本匹配的副本。
	 *
	 * @param notification 远端遗嘱通知
	 */
	public void applyWillMessageLocal(WillMessageNotifyMessage notification) {
		String clientId = notification.getClientId();
		if (clientId == null || clientId.isEmpty()) {
			return;
		}
		String token = notification.getWillToken();
		synchronized (willLock(clientId)) {
			long sequence = notification.getWillVersion();
			if (sequence > 0 && token != null) {
				willClock.updateAndGet(previous -> Math.max(previous, sequence));
				WillVersion current = willVersions.get(clientId);
				if (current != null) {
					int order = Long.compare(sequence, current.sequence);
					if (order == 0) {
						order = token.compareTo(current.token);
					}
					if (order < 0 || (order == 0 && (current.cleared || !notification.isClear()))) {
						return;
					}
				}
				willVersions.put(clientId, new WillVersion(sequence, token, notification.isClear()));
				if (notification.isClear()) {
					delegate.clearWillMessage(clientId);
					willTokens.remove(clientId);
				} else if (notification.getWillMessage() != null) {
					delegate.addWillMessage(clientId, notification.getWillMessage());
					willTokens.put(clientId, token);
				}
				return;
			}
			if (willVersions.containsKey(clientId)) {
				return;
			}
			if (notification.isClear()) {
				if (token != null && token.equals(willTokens.get(clientId))) {
					delegate.clearWillMessage(clientId);
					willTokens.remove(clientId);
				}
			} else if (notification.getWillMessage() != null) {
				delegate.addWillMessage(clientId, notification.getWillMessage());
				if (token == null) {
					willTokens.remove(clientId);
				} else {
					willTokens.put(clientId, token);
				}
			}
		}
	}

	private static final class WillVersion {
		private final long sequence;
		private final String token;
		private final boolean cleared;

		private WillVersion(long sequence, String token, boolean cleared) {
			this.sequence = sequence;
			this.token = token;
			this.cleared = cleared;
		}
	}

	/**
	 * 仅本地清除遗嘱消息，不广播到集群。
	 *
	 * @param clientId 客户端标识
	 * @return 是否成功
	 */
	public boolean clearWillMessageLocal(String clientId) {
		if (clientId == null || clientId.isEmpty()) {
			return false;
		}
		synchronized (willLock(clientId)) {
			willTokens.remove(clientId);
			return delegate.clearWillMessage(clientId);
		}
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
