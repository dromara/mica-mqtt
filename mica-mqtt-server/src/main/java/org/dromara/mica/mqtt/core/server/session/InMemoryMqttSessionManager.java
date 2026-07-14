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
import org.dromara.mica.mqtt.core.server.model.Subscribe;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
	 * PR9（P2.8）会话状态：客户端维度存储 Clean Start 标志 + Session Expiry Interval。
	 * <p>
	 * Key 为 clientId；value 在每次 CONNECT 时刷新（同一 clientId 重新连接覆盖）。
	 * 用于 DISCONNECT 决定是"立即清理"还是"调度过期"。
	 */
	private final ConcurrentMap<String, SessionState> sessionStates = new ConcurrentHashMap<>();

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
		data.put(messageId, pendingPublish);
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
		org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry entry = queue.poll();
		if (entry != null && queue.isEmpty()) {
			// 队列空时安全删除，避免内存膨胀
			pendingPublishBacklogStore.remove(clientId, queue);
		}
		return entry;
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
		// PR9：清理 session state
		sessionStates.remove(clientId);
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
	}

	// ----------------- PR9（P2.8）Session Expiry Interval -----------------

	@Override
	public void setSessionExpiryInterval(String clientId, int sessionExpirySeconds, boolean cleanStart) {
		if (clientId == null || clientId.isEmpty()) {
			return;
		}
		sessionStates.put(clientId, new SessionState(cleanStart, sessionExpirySeconds));
	}

	@Override
	public int getSessionExpiryInterval(String clientId) {
		SessionState state = sessionStates.get(clientId);
		return state == null ? 0 : state.sessionExpirySeconds;
	}

	@Override
	public boolean isCleanStart(String clientId) {
		SessionState state = sessionStates.get(clientId);
		// spec 3.1.2.4 / 3.1.2.11.4: 未声明时缺省值与协议版本相关
		// 3.1.x 缺省 true（cleanSession = true）；5.0 缺省 true。
		// 显式记录后以记录为准；未记录返回 true 保持 3.x 兼容。
		return state == null ? true : state.cleanStart;
	}

	/**
	 * 客户端维度会话状态：Clean Start + Session Expiry Interval。
	 */
	private static class SessionState {
		final boolean cleanStart;
		final int sessionExpirySeconds;

		SessionState(boolean cleanStart, int sessionExpirySeconds) {
			this.cleanStart = cleanStart;
			this.sessionExpirySeconds = sessionExpirySeconds;
		}
	}

}
