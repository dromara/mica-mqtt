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

package org.dromara.mica.mqtt.core.server.session;

import org.dromara.mica.mqtt.core.server.model.Message;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 单个 clientId 的会话状态：连接归属、持久标志、会话过期时间与离线消息队列。
 *
 * <p>持久会话（spec 3.1.2.11.4 Session Expiry Interval）判定：
 * <ul>
 *     <li>MQTT 3.1.1：Clean Session = false 且服务端开启持久会话；</li>
 *     <li>MQTT 5.0：Clean Start 只决定本次是否丢弃旧会话，是否保留由 Session Expiry Interval 决定
 *         （0 = 连接结束即结束、0xFFFFFFFF = 不限期、未携带取服务端默认值）。</li>
 * </ul>
 *
 * <p>线程安全：队列与可变状态统一由本对象的 monitor 保护，读多写少的状态用 volatile 提升可见性。
 *
 * @author wcmzllx
 */
public class MqttSessionState {
	/**
	 * MQTT 5.0 Session Expiry Interval 的"不限期"取值（spec 3.1.2.11.4）。
	 */
	public static final long NEVER_EXPIRE_SECONDS = 0xFFFFFFFFL;
	private final String clientId;
	/**
	 * 离线消息队列上限，&lt;= 0 表示不保存离线消息
	 */
	private final int maxOfflineQueueSize;
	/**
	 * 离线消息保存时长（毫秒），0 表示不限期
	 */
	private final long offlineMessageTtlMillis;
	private final Deque<Message> offlineQueue = new ArrayDeque<>();
	/**
	 * 当前持有该会话的连接 id（ChannelContext#getId）
	 */
	private volatile String connectionId;
	/**
	 * CONNECT 中的 Clean Start / Clean Session 标志
	 */
	private volatile boolean cleanStart = true;
	/**
	 * 是否持久会话：断开连接后是否跨连接保留
	 */
	private volatile boolean persistent;
	/**
	 * 会话过期时长（秒），0 表示不自动过期
	 */
	private volatile int sessionExpirySeconds;
	/**
	 * 过期时刻（毫秒），0 表示未标记过期
	 */
	private volatile long expireAt;

	public MqttSessionState(String clientId, int maxOfflineQueueSize, long offlineMessageTtlMillis) {
		this.clientId = clientId;
		this.maxOfflineQueueSize = maxOfflineQueueSize;
		this.offlineMessageTtlMillis = offlineMessageTtlMillis;
	}

	public String getClientId() {
		return clientId;
	}

	/**
	 * 绑定连接：更新归属连接与本次连接声明的会话策略，同时取消已有的过期标记。
	 * <p>
	 * 离线队列不会被清空——这正是持久会话的语义，重连时需要回放。
	 *
	 * @param connectionId        持有该会话的连接 id
	 * @param cleanStart          CONNECT 中的 Clean Start / Clean Session
	 * @param persistent          是否持久会话
	 * @param sessionExpirySeconds 会话过期时长（秒），0 表示不自动过期
	 */
	public synchronized void bind(String connectionId, boolean cleanStart, boolean persistent, int sessionExpirySeconds) {
		this.connectionId = connectionId;
		this.cleanStart = cleanStart;
		this.persistent = persistent;
		this.sessionExpirySeconds = sessionExpirySeconds;
		this.expireAt = 0L;
	}

	/**
	 * 会话归属是否仍指向该连接。
	 * <p>
	 * 互踢场景下旧连接的关闭回调晚于新连接的 CONNECT，用归属判断避免旧连接误删新会话。
	 */
	public boolean isOwner(String connectionId) {
		return this.connectionId != null && this.connectionId.equals(connectionId);
	}

	public String getConnectionId() {
		return connectionId;
	}

	public boolean isPersistent() {
		return persistent;
	}

	public boolean isCleanStart() {
		return cleanStart;
	}

	public int getSessionExpirySeconds() {
		return sessionExpirySeconds;
	}

	/**
	 * 连接断开后标记过期时间；过期时长为 0 表示不自动过期。
	 */
	public synchronized void markExpiry(long now) {
		this.expireAt = sessionExpirySeconds > 0 ? now + sessionExpirySeconds * 1000L : 0L;
	}

	/**
	 * 是否已被标记为待过期。
	 * <p>
	 * 连接断开时由 {@link #markExpiry(long)} 置位，重连 {@link #bind} 后复位。
	 * {@link SessionExpireScheduler} 触发回收前会先用它复核：定时器到期与重连可能并发，
	 * 而 {@code cancel()} 拦不住已经开始执行的任务，没有这道复核就会把刚被接管的会话误删。
	 */
	public boolean isExpiryMarked() {
		return expireAt > 0;
	}

	/**
	 * 离线消息入队；队列满时丢最旧，队列被禁用（容量 &lt;= 0）时直接拒绝。
	 *
	 * @param message 离线消息
	 * @return 是否已入队
	 */
	public synchronized boolean offer(Message message) {
		if (maxOfflineQueueSize <= 0) {
			return false;
		}
		while (offlineQueue.size() >= maxOfflineQueueSize) {
			offlineQueue.pollFirst();
		}
		offlineQueue.addLast(message);
		return true;
	}

	/**
	 * 取出队首消息；TTL 已过的消息被丢弃后继续取下一条。
	 */
	public synchronized Message poll() {
		long now = System.currentTimeMillis();
		while (!offlineQueue.isEmpty()) {
			Message message = offlineQueue.pollFirst();
			if (!isMessageExpired(message, now)) {
				return message;
			}
		}
		return null;
	}

	/**
	 * 放回队首（发送失败时保持原有顺序）。
	 */
	public synchronized void pushFirst(Message message) {
		if (maxOfflineQueueSize > 0) {
			offlineQueue.addFirst(message);
		}
	}

	public synchronized int queueSize() {
		return offlineQueue.size();
	}

	/**
	 * 丢弃全部离线消息（会话被清理时调用）。
	 */
	public synchronized void clearQueue() {
		offlineQueue.clear();
	}

	private boolean isMessageExpired(Message message, long now) {
		if (offlineMessageTtlMillis <= 0) {
			return false;
		}
		return now - message.getTimestamp() >= offlineMessageTtlMillis;
	}

	@Override
	public String toString() {
		return "MqttSessionState{clientId='" + clientId + '\''
			+ ", connectionId='" + connectionId + '\''
			+ ", cleanStart=" + cleanStart
			+ ", persistent=" + persistent
			+ ", sessionExpirySeconds=" + sessionExpirySeconds
			+ ", offlineQueueSize=" + offlineQueue.size()
			+ '}';
	}
}
