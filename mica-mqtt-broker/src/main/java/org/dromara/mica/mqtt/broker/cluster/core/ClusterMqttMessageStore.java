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
 * 集群消息存储装饰器
 * <p>
 * 包装原有的 IMqttMessageStore，当遗嘱消息或保留消息操作发生时，
 * 自动向集群广播通知，确保所有节点的消息存储一致。
 * </p>
 *
 * @author L.cm
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

	@Override
	public void clean() throws java.io.IOException {
		delegate.clean();
	}
}