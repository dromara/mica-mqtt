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

import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存 session 管理
 *
 * @author L.cm
 */
public class InMemoryMqttSessionManager implements IMqttSessionManager {
	/**
	 * messageId 存储 clientId: messageId
	 */
	private final ConcurrentMap<String, AtomicInteger> messageIdStore = new ConcurrentHashMap<>();
	/**
	 * 订阅存储，支持共享订阅
	 */
	private final TrieTopicManager topicManager = new TrieTopicManager();
	/**
	 * qos1 消息过程存储 clientId: {msgId: Object}
	 */
	private final ConcurrentMap<String, ConcurrentMap<Integer, MqttPendingPublish>> pendingPublishStore = new ConcurrentHashMap<>();
	/**
	 * qos2 消息过程存储 clientId: {msgId: Object}
	 */
	private final ConcurrentMap<String, ConcurrentMap<Integer, MqttPendingQos2Publish>> pendingQos2PublishStore = new ConcurrentHashMap<>();
	/**
	 * 客户端在 CONNECT 中声明的 Receive Maximum（缺省视为 65535）
	 */
	private final ConcurrentMap<String, Integer> clientReceiveMaximumStore = new ConcurrentHashMap<>();
	/**
	 * PUBLISH 等待队列（PR7 / Receive Maximum 限流回补）。
	 * <p>
	 * 客户端维度的 FIFO 队列，存储"待发送"的 PUBLISH 快照（{@link org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry}）。
	 * 当 in-flight 数达到 Receive Maximum 上限时，QoS>0 的 PUBLISH 被缓存到这里，
	 * 直到 PUBACK/PUBCOMP 释放 in-flight 配额后由 {@code MqttServer.drainPublishBacklog} 出队发送。
	 * <p>
	 * 用 ConcurrentLinkedQueue 而非 BlockingQueue：入队不需要阻塞（应用层只需在 publish 时尝试一次）；
	 * 出队在 ACK 处理线程上调用，无消费者线程竞争。
	 */
	private final ConcurrentMap<String, java.util.concurrent.ConcurrentLinkedQueue<org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry>> pendingPublishBacklogStore = new ConcurrentHashMap<>();
	/**
	 * 会话状态：连接归属、Clean Start / 持久标志、会话过期时长与离线消息队列。
	 * <p>
	 * Key 为 clientId；value 在每次 CONNECT 时通过 {@link #bindSession} 刷新（离线队列不会被清空）。
	 */
	private final ConcurrentMap<String, MqttSessionState> sessionStates = new ConcurrentHashMap<>();
	/**
	 * QoS2 在途消息已收到 PUBREC 的标记，重连后据此重发 PUBREL 而不是 PUBLISH。
	 */
	private final ConcurrentMap<String, Set<Integer>> pubRecReceivedStore = new ConcurrentHashMap<>();
	/**
	 * 离线消息队列上限，&lt;= 0 表示不保存离线消息
	 */
	private final int maxOfflineQueueSize;
	/**
	 * 离线消息保存时长（毫秒），0 表示不限期
	 */
	private final long offlineMessageTtlMillis;
	/**
	 * 每会话在途消息上限，&lt;= 0 表示不限制
	 */
	private final int maxInflightPerSession;

	public InMemoryMqttSessionManager() {
		this(1000, 0L, 0);
	}

	public InMemoryMqttSessionManager(int maxOfflineQueueSize, long offlineMessageTtlMillis, int maxInflightPerSession) {
		this.maxOfflineQueueSize = maxOfflineQueueSize;
		this.offlineMessageTtlMillis = offlineMessageTtlMillis;
		this.maxInflightPerSession = maxInflightPerSession;
	}

	@Override
	public boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS,
								boolean noLocal, boolean retainAsPublished, int retainHandling) {
		return topicManager.addSubscribe(topicFilter, clientId, (short) mqttQoS, noLocal, retainAsPublished, retainHandling);
	}

	@Override
	public boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS,
								boolean noLocal, boolean retainAsPublished, int retainHandling, int subscriptionId) {
		return topicManager.addSubscribe(topicFilter, clientId, (short) mqttQoS, noLocal, retainAsPublished, retainHandling, subscriptionId);
	}

	@Override
	public void removeSubscribe(String topicFilter, String clientId) {
		topicManager.removeSubscribe(topicFilter, clientId);
	}

	public void removeSubscribe(String clientId) {
		topicManager.removeSubscribe(clientId);
	}

	@Override
	public Byte searchSubscribe(String topicName, String clientId) {
		return topicManager.searchSubscribe(topicName, clientId);
	}

	@Override
	public List<Subscribe> searchSubscribe(String topicName) {
		return topicManager.searchSubscribe(topicName);
	}

	@Override
	public List<Subscribe> getSubscriptions(String clientId) {
		return topicManager.getSubscriptions(clientId);
	}

	@Override
	public void addPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.computeIfAbsent(clientId, (key) -> new ConcurrentHashMap<>(16));
		// 在途上限：溢出丢最旧的 packetId，避免单个会话无限堆积
		while (maxInflightPerSession > 0 && data.size() >= maxInflightPerSession) {
			Integer oldest = data.keySet().stream().min(Integer::compareTo).orElse(null);
			if (oldest == null) {
				break;
			}
			data.remove(oldest);
			clearPubRecReceived(clientId, oldest);
		}
		data.put(messageId, pendingPublish);
	}

	@Override
	public boolean tryAddPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish) {
		int receiveMaximum = getClientReceiveMaximum(clientId);
		if (receiveMaximum < 1) {
			return false;
		}
		AtomicBoolean added = new AtomicBoolean();
		pendingPublishStore.compute(clientId, (key, data) -> {
			ConcurrentMap<Integer, MqttPendingPublish> pending = data;
			if (pending == null) {
				pending = new ConcurrentHashMap<>(16);
			}
			if (pending.size() < receiveMaximum) {
				pending.put(messageId, pendingPublish);
				added.set(true);
			}
			return pending;
		});
		return added.get();
	}

	@Override
	public MqttPendingPublish getPendingPublish(String clientId, int messageId) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.get(clientId);
		if (data == null) {
			return null;
		}
		return data.get(messageId);
	}

	@Override
	public void removePendingPublish(String clientId, int messageId) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.get(clientId);
		if (data != null) {
			data.remove(messageId);
		}
		// 在途消息被确认或淘汰后，同步清理 PUBREC 标记，避免标记泄漏与误判
		clearPubRecReceived(clientId, messageId);
	}

	@Override
	public void markPendingPublishPubRel(String clientId, int messageId) {
		if (clientId == null || clientId.isEmpty()) {
			return;
		}
		pubRecReceivedStore.computeIfAbsent(clientId, (key) -> ConcurrentHashMap.newKeySet()).add(messageId);
	}

	@Override
	public boolean isPubRecReceived(String clientId, int messageId) {
		Set<Integer> pubRecSet = pubRecReceivedStore.get(clientId);
		return pubRecSet != null && pubRecSet.contains(messageId);
	}

	private void clearPubRecReceived(String clientId, int messageId) {
		Set<Integer> pubRecSet = pubRecReceivedStore.get(clientId);
		if (pubRecSet != null) {
			pubRecSet.remove(messageId);
		}
	}

	@Override
	public List<MqttPendingPublish> getPendingPublishes(String clientId) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.get(clientId);
		if (data == null || data.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayList<>(data.values());
	}

	@Override
	public void addPendingPublishBacklog(String clientId, org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry entry) {
		java.util.concurrent.ConcurrentLinkedQueue<org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry> queue =
			pendingPublishBacklogStore.computeIfAbsent(clientId, key -> new java.util.concurrent.ConcurrentLinkedQueue<>());
		queue.offer(entry);
	}

	@Override
	public org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry pollPendingPublishBacklog(String clientId) {
		java.util.concurrent.ConcurrentLinkedQueue<org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry> queue =
			pendingPublishBacklogStore.get(clientId);
		if (queue == null) {
			return null;
		}
		return queue.poll();
	}

	@Override
	public int getPendingPublishBacklogSize(String clientId) {
		java.util.concurrent.ConcurrentLinkedQueue<org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry> queue =
			pendingPublishBacklogStore.get(clientId);
		return queue == null ? 0 : queue.size();
	}

	@Override
	public void addPendingQos2Publish(String clientId, int messageId, MqttPendingQos2Publish pendingQos2Publish) {
		Map<Integer, MqttPendingQos2Publish> data = pendingQos2PublishStore.computeIfAbsent(clientId, (key) -> new ConcurrentHashMap<>(16));
		data.put(messageId, pendingQos2Publish);
	}

	@Override
	public MqttPendingQos2Publish getPendingQos2Publish(String clientId, int messageId) {
		Map<Integer, MqttPendingQos2Publish> data = pendingQos2PublishStore.get(clientId);
		if (data == null) {
			return null;
		}
		return data.get(messageId);
	}

	@Override
	public void removePendingQos2Publish(String clientId, int messageId) {
		Map<Integer, MqttPendingQos2Publish> data = pendingQos2PublishStore.get(clientId);
		if (data != null) {
			data.remove(messageId);
		}
	}

	@Override
	public void setClientReceiveMaximum(String clientId, int receiveMaximum) {
		if (receiveMaximum == MQTT5_DEFAULT_RECEIVE_MAXIMUM) {
			clientReceiveMaximumStore.remove(clientId);
		} else {
			clientReceiveMaximumStore.put(clientId, receiveMaximum);
		}
	}

	@Override
	public int getClientReceiveMaximum(String clientId) {
		Integer receiveMaximum = clientReceiveMaximumStore.get(clientId);
		return receiveMaximum == null ? MQTT5_DEFAULT_RECEIVE_MAXIMUM : receiveMaximum;
	}

	@Override
	public int getPendingPublishCount(String clientId) {
		Map<Integer, MqttPendingPublish> data = pendingPublishStore.get(clientId);
		return data == null ? 0 : data.size();
	}

	@Override
	public int getPacketId(String clientId) {
		AtomicInteger packetIdGen = messageIdStore.computeIfAbsent(clientId, (key) -> new AtomicInteger(1));
		return packetIdGen.getAndUpdate(current -> (current % 0xffff) == 0 ? 1 : current + 1);
	}

	@Override
	public boolean hasSession(String clientId) {
		return pendingQos2PublishStore.containsKey(clientId)
			|| pendingPublishStore.containsKey(clientId)
			|| messageIdStore.containsKey(clientId)
			|| sessionStates.containsKey(clientId)
			|| !topicManager.getSubscriptions(clientId).isEmpty();
	}

	@Override
	public boolean expire(String clientId, int sessionExpirySeconds) {
		return false;
	}

	@Override
	public boolean active(String clientId) {
		return false;
	}

	@Override
	public void remove(String clientId) {
		removeSubscribe(clientId);
		pendingPublishStore.remove(clientId);
		pendingQos2PublishStore.remove(clientId);
		clientReceiveMaximumStore.remove(clientId);
		messageIdStore.remove(clientId);
		// PR7：清理 PublishBacklog 队列，避免内存泄露
		pendingPublishBacklogStore.remove(clientId);
		// 清理 session state（含离线消息队列）与 PUBREC 标记
		sessionStates.remove(clientId);
		pubRecReceivedStore.remove(clientId);
	}

	@Override
	public void clean() {
		topicManager.clear();
		pendingPublishStore.clear();
		pendingQos2PublishStore.clear();
		clientReceiveMaximumStore.clear();
		messageIdStore.clear();
		pendingPublishBacklogStore.clear();
		sessionStates.clear();
		pubRecReceivedStore.clear();
	}

	// ----------------- 会话状态（Clean Start / Session Expiry Interval）-----------------

	@Override
	public void setSessionExpiryInterval(String clientId, int sessionExpirySeconds, boolean cleanStart) {
		if (clientId == null || clientId.isEmpty()) {
			return;
		}
		MqttSessionState state = sessionStates.computeIfAbsent(clientId,
			(key) -> new MqttSessionState(key, maxOfflineQueueSize, offlineMessageTtlMillis));
		// 兼容入口：不跟踪连接归属，持久标志按 cleanStart + 过期时长推导
		state.bind(state.getConnectionId(), cleanStart, !cleanStart && sessionExpirySeconds > 0, sessionExpirySeconds);
	}

	@Override
	public int getSessionExpiryInterval(String clientId) {
		MqttSessionState state = sessionStates.get(clientId);
		return state == null ? 0 : state.getSessionExpirySeconds();
	}

	@Override
	public boolean isCleanStart(String clientId) {
		MqttSessionState state = sessionStates.get(clientId);
		// spec 3.1.2.4 / 3.1.2.11.4: 未声明时缺省值与协议版本相关
		// 3.1.x 缺省 true（cleanSession = true）；5.0 缺省 true。
		// 显式记录后以记录为准；未记录返回 true 保持 3.x 兼容。
		return state == null || state.isCleanStart();
	}

	// ----------------- 持久会话与离线消息 -----------------

	@Override
	public void bindSession(String clientId, String connectionId, boolean cleanStart, boolean persistent, int sessionExpirySeconds) {
		if (clientId == null || clientId.isEmpty()) {
			return;
		}
		MqttSessionState state = sessionStates.computeIfAbsent(clientId,
			(key) -> new MqttSessionState(key, maxOfflineQueueSize, offlineMessageTtlMillis));
		state.bind(connectionId, cleanStart, persistent, sessionExpirySeconds);
	}

	@Override
	public boolean isSessionOwner(String clientId, String connectionId) {
		MqttSessionState state = sessionStates.get(clientId);
		// 未跟踪归属的实现按"始终归属当前连接"处理，保持兼容
		return state == null || state.isOwner(connectionId);
	}

	@Override
	public boolean isPersistentSession(String clientId) {
		MqttSessionState state = sessionStates.get(clientId);
		return state != null && state.isPersistent();
	}

	@Override
	public void markSessionExpiry(String clientId) {
		MqttSessionState state = sessionStates.get(clientId);
		if (state != null) {
			state.markExpiry(System.currentTimeMillis());
		}
	}

	@Override
	public boolean isSessionPendingExpiry(String clientId) {
		MqttSessionState state = sessionStates.get(clientId);
		return state != null && state.isExpiryMarked();
	}

	@Override
	public boolean addOfflineMessage(String clientId, Message message) {
		MqttSessionState state = sessionStates.get(clientId);
		// 非持久会话不缓存离线消息（spec 3.1.2.11.4）
		if (state == null || !state.isPersistent()) {
			return false;
		}
		return state.offer(message);
	}

	@Override
	public Message pollOfflineMessage(String clientId) {
		MqttSessionState state = sessionStates.get(clientId);
		return state == null ? null : state.poll();
	}

	@Override
	public void pushOfflineMessageFirst(String clientId, Message message) {
		MqttSessionState state = sessionStates.get(clientId);
		if (state != null) {
			state.pushFirst(message);
		}
	}

	@Override
	public int getOfflineMessageCount(String clientId) {
		MqttSessionState state = sessionStates.get(clientId);
		return state == null ? 0 : state.queueSize();
	}

}
