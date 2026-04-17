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
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decorator for {@link IMqttMessageStore} that synchronizes will and retained messages across cluster nodes.
 * <p>
 * This store wraps an existing {@link IMqttMessageStore} and extends it with
 * cluster synchronization. When a will message is set or a retained message is
 * published/cleared, it broadcasts the change to all other cluster nodes.
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

	public ClusterMqttMessageStore(IMqttMessageStore delegate, MqttClusterManager clusterManager) {
		this.delegate = delegate;
		this.clusterManager = clusterManager;
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
		return delegate.clearWillMessage(clientId);
	}

	@Override
	public Message getWillMessage(String clientId) {
		return delegate.getWillMessage(clientId);
	}

	@Override
	public boolean addRetainMessage(String topic, int timeout, Message message) {
		delegate.addRetainMessage(topic, timeout, message);
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
		return delegate.getRetainMessage(topicFilter);
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
		return delegate.addRetainMessage(topic, timeout, message);
	}

	/**
	 * 仅本地清除保留消息，不广播到集群。
	 *
	 * @param topic 主题
	 * @return 是否成功
	 */
	public boolean clearRetainMessageLocal(String topic) {
		return delegate.clearRetainMessage(topic);
	}

	@Override
	public void clean() throws java.io.IOException {
		delegate.clean();
	}
}