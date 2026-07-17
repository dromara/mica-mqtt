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

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.RetainMessageNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.message.WillMessageNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.store.MemoryKvStoreImpl;
import org.dromara.mica.mqtt.broker.cluster.store.RetainIndex;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.store.InMemoryMqttMessageStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterMqttMessageStoreTest {

	@Test
	void shardedWriteOnlyStoresOnSelectedReplicas() {
		CapturingManager manager = new CapturingManager();
		manager.replicas = Arrays.asList("node-2", "node-3");
		InMemoryMqttMessageStore delegate = new InMemoryMqttMessageStore();
		ClusterMqttMessageStore store = new ClusterMqttMessageStore(delegate, manager);
		store.setRetainIndex(new RetainIndex(new MemoryKvStoreImpl()));

		store.addRetainMessage("devices/a", 0, message("devices/a", 1));

		assertTrue(delegate.getRetainMessage("devices/a").isEmpty());
		assertEquals(Arrays.asList("node-2", "node-3"), manager.sentNodes);
		assertTrue(manager.sent.stream().allMatch(message -> message instanceof RetainMessageNotifyMessage));
	}

	@Test
	void shardedReadMergesAndDeduplicatesReplicaResults() {
		CapturingManager manager = new CapturingManager();
		manager.replicas = Arrays.asList("node-1", "node-2");
		manager.remoteResults = Arrays.asList(message("devices/a", 2), message("devices/b", 3));
		ClusterMqttMessageStore store = new ClusterMqttMessageStore(new InMemoryMqttMessageStore(), manager);
		store.setRetainIndex(new RetainIndex(new MemoryKvStoreImpl()));
		store.addRetainMessage("devices/a", 0, message("devices/a", 1));

		List<Message> retained = store.getRetainMessage("devices/#");

		assertEquals(2, retained.size());
		assertEquals(2, retained.stream().filter(message -> "devices/a".equals(message.getTopic())
			|| "devices/b".equals(message.getTopic())).count());
	}

	@Test
	void clearingWillBroadcastsReplicaTombstone() {
		CapturingManager manager = new CapturingManager();
		ClusterMqttMessageStore store = new ClusterMqttMessageStore(new InMemoryMqttMessageStore(), manager);
		store.addWillMessage("client-1", message("will/client-1", 1));
		store.clearWillMessage("client-1");

		assertEquals(2, manager.broadcasts.size());
		WillMessageNotifyMessage tombstone = (WillMessageNotifyMessage) manager.broadcasts.get(1);
		assertEquals("client-1", tombstone.getClientId());
		assertTrue(tombstone.getWillMessage() == null);
	}

	private static Message message(String topic, int value) {
		Message message = new Message();
		message.setTopic(topic);
		message.setPayload(new byte[]{(byte) value});
		message.setQos(1);
		message.setRetain(true);
		return message;
	}

	private static class CapturingManager extends MqttClusterManager {
		private List<String> replicas = Collections.emptyList();
		private List<Message> remoteResults = Collections.emptyList();
		private final List<String> sentNodes = new ArrayList<>();
		private final List<ClusterMessage> sent = new ArrayList<>();
		private final List<ClusterMessage> broadcasts = new ArrayList<>();

		private CapturingManager() {
			super(new MqttClusterConfig().enabled(true).retainShardingEnabled(true), "node-1");
		}

		@Override
		public List<String> getRetainReplicaNodes(String topic) {
			return replicas;
		}

		@Override
		public boolean sendToNode(String nodeId, ClusterMessage clusterMsg) {
			sentNodes.add(nodeId);
			sent.add(clusterMsg);
			return true;
		}

		@Override
		public List<Message> queryRemoteRetain(String topicFilter, long timeoutMs) {
			return remoteResults;
		}

		@Override
		public void broadcast(ClusterMessage clusterMsg) {
			broadcasts.add(clusterMsg);
		}
	}
}
