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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
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
	/**
	 * 墓碑默认保留时长。
	 * <p>
	 * 墓碑只用于压制「乱序到达的旧 add」。集群消息延迟超过该时长时，
	 * 旧 add 理论上仍可能复活，因此该值必须大于可预期的最大节点间消息延迟
	 * 与分区恢复时间，可通过 {@link #setWillVersionTtlMs(long)} 调整。
	 * 该值不是“消息必然在窗口内到达”的证明，而是部署时需要保证的运行时假设。
	 * 版本号基于节点时间（Lamport 式取 max），因此还要求节点间时钟大致同步（NTP），
	 * 时钟偏差应远小于该 TTL。
	 * </p>
	 */
	public static final long DEFAULT_WILL_VERSION_TTL_MS = TimeUnit.MINUTES.toMillis(30);
	/**
	 * 触发墓碑清理的阈值，避免每次 add/clear 都扫描 map。
	 */
	private static final int WILL_VERSION_PRUNE_THRESHOLD = 1024;
	/**
	 * 墓碑硬上限。极端情况下（大量不同 clientId 在 TTL 内反复设置遗嘱）
	 * 仍然需要兜底，超过后按时间从旧到新淘汰 cleared 条目。
	 */
	private static final int WILL_VERSION_MAX_ENTRIES = 65_536;

	private final IMqttMessageStore delegate;
	private final MqttClusterManager clusterManager;
	private final ConcurrentMap<String, String> willTokens = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, WillVersion> willVersions = new ConcurrentHashMap<>();
	private final AtomicLong willClock = new AtomicLong();
	private final AtomicLong willOps = new AtomicLong();
	/**
	 * 已被淘汰墓碑的最高版本。任何不高于该版本的 will add 都视为过期，
	 * 即使对应 clientId 的墓碑已被 TTL 或容量策略移除。
	 */
	private final AtomicLong willVersionFloor = new AtomicLong();
	// 按客户端分片，保证同一连接的保存和清理通知顺序
	private final Object[] willLocks = new Object[64];
	private volatile RetainIndex retainIndex;
	private volatile long willVersionTtlMs = DEFAULT_WILL_VERSION_TTL_MS;

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

	/**
	 * 设置遗嘱墓碑保留时长。必须大于集群最大消息延迟/分区恢复时间，
	 * 否则超时回收后，延迟到达的旧 add 可能重新变成有效遗嘱。
	 *
	 * @param willVersionTtlMs 保留时长（毫秒）
	 */
	public void setWillVersionTtlMs(long willVersionTtlMs) {
		if (willVersionTtlMs <= 0) {
			throw new IllegalArgumentException("willVersionTtlMs must be greater than zero.");
		}
		this.willVersionTtlMs = willVersionTtlMs;
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
				willVersions.put(clientId, new WillVersion(version, token, false, System.currentTimeMillis()));
				broadcastWill(clientId, message, token, version, false);
			}
			return added;
		}
	}

	@Override
	public boolean clearWillMessage(String clientId) {
		return clearWillMessage(clientId, true);
	}

	@Override
	public boolean clearWillMessageOnConnect(String clientId) {
		// 版本屏障必须本地推进，但传播交给 CLIENT_CONNECT 上线通知：
		// 每次无遗嘱连接都单独广播 WILL_MESSAGE 会造成 N-1 倍广播放大。
		return clearWillMessage(clientId, false);
	}

	/**
	 * 清理遗嘱并推进版本屏障。
	 *
	 * @param clientId  客户端标识
	 * @param broadcast 是否立即广播清理通知
	 * @return 是否清除了本地遗嘱
	 */
	private boolean clearWillMessage(String clientId, boolean broadcast) {
		if (clientId == null || clientId.isEmpty()) {
			return false;
		}
		synchronized (willLock(clientId)) {
			// 每次「无遗嘱连接」都必须推进清理版本，即使本地已有 cleared 墓碑：
			// 其他节点可能在本墓碑之后又产生了更高版本的 will add，且尚未到达。
			// 若复用旧版本直接返回，该 add 会高于旧墓碑并被错误接受。
			willTokens.remove(clientId);
			boolean removed = delegate.clearWillMessage(clientId);
			String token = UUID.randomUUID().toString();
			long version = nextWillVersion();
			willVersions.put(clientId, new WillVersion(version, token, true, System.currentTimeMillis()));
			if (broadcast) {
				broadcastWill(clientId, null, token, version, true);
			}
			return removed;
		}
	}

	/**
	 * 返回当前尚未被新遗嘱覆盖的清理状态，供上线通知捎带。
	 *
	 * @param clientId 客户端标识
	 * @return 清理通知，当前存在活跃遗嘱时返回 null
	 */
	WillMessageNotifyMessage getClearedWillState(String clientId) {
		if (clientId == null || clientId.isEmpty()) {
			return null;
		}
		WillVersion version = willVersions.get(clientId);
		if (version == null || !version.cleared) {
			return null;
		}
		WillMessageNotifyMessage notification = new WillMessageNotifyMessage();
		notification.setClientId(clientId);
		notification.setWillToken(version.token);
		notification.setWillVersion(version.sequence);
		notification.setClear(true);
		return notification;
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
						willVersions.put(clientId, new WillVersion(sequence, token, true, System.currentTimeMillis()));
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
		pruneWillVersionsIfNeeded();
		return willClock.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()));
	}

	/**
	 * 定期回收已清理客户端的墓碑，防止 {@link #willVersions} 无界增长。
	 * <p>
	 * 只回收 {@code cleared=true} 且超过 {@link #willVersionTtlMs} 的条目；
	 * 活跃遗嘱（cleared=false）必须保留，否则乱序的旧 clear 会误删新遗嘱。
	 * </p>
	 */
	private void pruneWillVersionsIfNeeded() {
		if (willOps.incrementAndGet() % WILL_VERSION_PRUNE_THRESHOLD != 0) {
			return;
		}
		pruneWillVersions();
		capWillVersions();
	}

	/**
	 * 墓碑数量超过硬上限时，按时间从旧到新淘汰 cleared 条目。
	 * 活跃遗嘱（cleared=false）不参与淘汰。
	 */
	private void capWillVersions() {
		capWillVersions(WILL_VERSION_MAX_ENTRIES);
	}

	/**
	 * 将墓碑数量压到上限；淘汰时推进版本水位，保证被移除墓碑以下的 add 仍被拒绝。
	 *
	 * @param maxEntries 最大条目数
	 * @return 淘汰的条目数
	 */
	int capWillVersions(int maxEntries) {
		int overflow = willVersions.size() - maxEntries;
		if (overflow <= 0) {
			return 0;
		}
		List<Map.Entry<String, WillVersion>> candidates = new ArrayList<>();
		for (Map.Entry<String, WillVersion> entry : willVersions.entrySet()) {
			if (entry.getValue().cleared) {
				candidates.add(entry);
			}
		}
		candidates.sort(Comparator.comparingLong(entry -> entry.getValue().timestamp));
		long maxRemoved = 0;
		int removed = 0;
		for (int i = 0; i < overflow && i < candidates.size(); i++) {
			Map.Entry<String, WillVersion> entry = candidates.get(i);
			if (willVersions.remove(entry.getKey(), entry.getValue())) {
				maxRemoved = Math.max(maxRemoved, entry.getValue().sequence);
				removed++;
			}
		}
		// 被淘汰的墓碑仍然要压制其版本以下的迟到 add
		advanceWillVersionFloor(maxRemoved);
		return removed;
	}

	/**
	 * 立即清理过期墓碑。
	 *
	 * @return 清理的条目数
	 */
	int pruneWillVersions() {
		return pruneWillVersions(System.currentTimeMillis() - willVersionTtlMs);
	}

	/**
	 * 清理时间戳不晚于指定时刻的墓碑（仅供测试注入过期时间）。
	 *
	 * @param expireBefore 过期时间阈值
	 * @return 清理的条目数
	 */
	int pruneWillVersions(long expireBefore) {
		int[] removed = new int[1];
		long[] maxRemoved = new long[1];
		willVersions.entrySet().removeIf(entry -> {
			WillVersion version = entry.getValue();
			if (version.cleared && version.timestamp <= expireBefore) {
				removed[0]++;
				maxRemoved[0] = Math.max(maxRemoved[0], version.sequence);
				return true;
			}
			return false;
		});
		advanceWillVersionFloor(maxRemoved[0]);
		return removed[0];
	}

	private void advanceWillVersionFloor(long version) {
		if (version > 0) {
			willVersionFloor.accumulateAndGet(version, Math::max);
		}
	}

	/**
	 * 当前墓碑数量，供监控和测试使用。
	 *
	 * @return 版本记录数量
	 */
	int getWillVersionCount() {
		return willVersions.size();
	}

	/**
	 * 已淘汰墓碑的版本水位，供监控和测试使用。
	 *
	 * @return 最低可接受的 will add 版本（含）
	 */
	long getWillVersionFloor() {
		return willVersionFloor.get();
	}

	/**
	 * 当前 clientId 的遗嘱版本，供测试验证版本推进。
	 *
	 * @param clientId 客户端标识
	 * @return 版本号，不存在时返回 0
	 */
	long getWillVersion(String clientId) {
		WillVersion version = willVersions.get(clientId);
		return version == null ? 0 : version.sequence;
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
		// 内存副本只包含本节点启动后收到/写入的消息；持久化索引可能还有更早的
		// topic（例如重启后只更新了 a/1，而 a/2 仍在索引里）。必须按 topic 合并，
		// 不能因为内存非空就完全跳过索引。同一 topic 以内存为准（更新、TTL 更准确）。
		Map<String, Message> merged = new LinkedHashMap<>();
		RetainIndex index = this.retainIndex;
		if (index != null) {
			List<Message> persisted = index.match(topicFilter);
			if (persisted != null) {
				for (Message message : persisted) {
					if (message != null && message.getTopic() != null) {
						merged.put(message.getTopic(), message);
					}
				}
			}
		}
		List<Message> inMemory = delegate.getRetainMessage(topicFilter);
		if (inMemory != null) {
			for (Message message : inMemory) {
				if (message != null && message.getTopic() != null) {
					merged.put(message.getTopic(), message);
				}
			}
		}
		return new ArrayList<>(merged.values());
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
		// 仅接收通知的副本节点不会走本地 nextWillVersion，这里也要触发墓碑回收
		pruneWillVersionsIfNeeded();
		synchronized (willLock(clientId)) {
			long sequence = notification.getWillVersion();
			if (sequence > 0 && token != null) {
				// 墓碑被 TTL/容量回收后，仍要拒绝不高于淘汰水位的 add；
				// 再叠加时间上界，避免旧遗嘱在保留窗口之外复活。
				if (!notification.isClear()) {
					long floor = willVersionFloor.get();
					if (sequence <= floor) {
						logger.warn("Rejecting will add below eviction floor for clientId:{} version:{} floor:{}",
							clientId, sequence, floor);
						return;
					}
					if (sequence < System.currentTimeMillis() - willVersionTtlMs) {
						logger.warn("Rejecting stale will add for clientId:{} version:{}", clientId, sequence);
						return;
					}
				}
				willClock.updateAndGet(previous -> Math.max(previous, sequence));
				WillVersion current = willVersions.get(clientId);
				if (current != null) {
					int order = Long.compare(sequence, current.sequence);
					if (order == 0) {
						if (current.cleared) {
							// Lamport 版本相同即并发：墓碑确定性获胜。无遗嘱重连是客户端
							// 「当前没有遗嘱」的最近表态，并发的同版本 add 不能覆盖它，
							// 否则旧遗嘱可能借版本碰撞复活（结果也不再取决于随机 token）。
							return;
						}
						order = token.compareTo(current.token);
					}
					if (order < 0 || (order == 0 && !notification.isClear())) {
						return;
					}
				}
				willVersions.put(clientId, new WillVersion(sequence, token, notification.isClear(), System.currentTimeMillis()));
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
		private final long timestamp;

		private WillVersion(long sequence, String token, boolean cleared, long timestamp) {
			this.sequence = sequence;
			this.token = token;
			this.cleared = cleared;
			this.timestamp = timestamp;
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
