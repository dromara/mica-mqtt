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

package org.dromara.mica.mqtt.broker.cluster.pipeline;

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.broker.cluster.message.SharedDispatchToClientMessage;
import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.SharedSubscriptionStrategy;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClusterPublishHandlerTest {

	@Test
	void mqttPublishUsesSingleSharedDispatchAndPerNodeNormalForward() {
		CapturingClusterManager clusterManager = new CapturingClusterManager();
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);
		clusterManager.setSessionManager(sessions);
		sessions.syncRemoteSubscriptions("normal-1", "node-2", Arrays.asList(
			new Subscribe("sensors/#", "normal-1", 1)
		));
		sessions.syncRemoteSubscriptions("normal-2", "node-2", Arrays.asList(
			new Subscribe("sensors/#", "normal-2", 1)
		));
		sessions.syncRemoteSubscriptions("shared-1", "node-2", Arrays.asList(
			new Subscribe("$share/g1/sensors/#", "shared-1", 1)
		));
		sessions.syncRemoteSubscriptions("shared-2", "node-3", Arrays.asList(
			new Subscribe("$share/g1/sensors/#", "shared-2", 1)
		));
		sessions.addSubscribe(new TopicFilter("$share/g1/sensors/#"), "publisher", 1, true, false, 0);

		SharedSubscriptionStrategy first = new SharedSubscriptionStrategy() {
			@Override
			public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
				return candidates.get(0);
			}

			@Override
			public String name() {
				return "first";
			}
		};
		ClusterPublishHandler handler = new ClusterPublishHandler(null, clusterManager, sessions, first);
		PublishContext context = PublishContext.builder()
			.clientId("publisher")
			.topic("sensors/a")
			.payload("value".getBytes(StandardCharsets.UTF_8))
			.qos(MqttQoS.QOS1)
			.timestamp(System.currentTimeMillis())
			.build();

		handler.handle(context);

		assertEquals(2, clusterManager.sent.size());
		assertInstanceOf(PublishForwardMessage.class, clusterManager.sent.get(0).message);
		assertEquals("node-2", clusterManager.sent.get(0).nodeId);
		assertInstanceOf(SharedDispatchToClientMessage.class, clusterManager.sent.get(1).message);
		SharedDispatchToClientMessage shared =
			(SharedDispatchToClientMessage) clusterManager.sent.get(1).message;
		assertNotEquals("publisher", shared.getClientId());
		assertEquals("g1", shared.getGroupName());
		assertEquals(1, clusterManager.getMetrics().getPublishForwardSent());
		assertEquals(1, clusterManager.getMetrics().getSharedDispatchSent());
	}

	@Test
	void unavailableSharedTargetIsRepickedWithoutBlindRetry() {
		CapturingClusterManager clusterManager = new CapturingClusterManager();
		clusterManager.failNextSend = true;
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager);
		clusterManager.setSessionManager(sessions);
		sessions.syncRemoteSubscriptions("shared-1", "node-2", Collections.singletonList(
			new Subscribe("$share/g1/jobs/#", "shared-1", 1)));
		sessions.syncRemoteSubscriptions("shared-2", "node-3", Collections.singletonList(
			new Subscribe("$share/g1/jobs/#", "shared-2", 1)));
		SharedSubscriptionStrategy first = new SharedSubscriptionStrategy() {
			@Override
			public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
				return candidates.get(0);
			}

			@Override
			public String name() {
				return "first";
			}
		};
		ClusterPublishHandler handler = new ClusterPublishHandler(null, clusterManager, sessions, first);
		handler.handle(PublishContext.builder()
			.clientId("publisher").topic("jobs/a").payload(new byte[]{1})
			.qos(MqttQoS.QOS1).timestamp(System.currentTimeMillis()).build());

		assertEquals(2, clusterManager.sent.size());
		assertNotEquals(clusterManager.sent.get(0).nodeId, clusterManager.sent.get(1).nodeId);
		assertEquals(1, clusterManager.getMetrics().getSharedDispatchSent());
		assertEquals(0, clusterManager.getMetrics().getSharedDispatchDropped());
	}

	private static class CapturingClusterManager extends MqttClusterManager {
		private final List<SentMessage> sent = new ArrayList<>();
		private boolean failNextSend;

		private CapturingClusterManager() {
			super(new MqttClusterConfig().enabled(true), "node-1");
		}

		@Override
		public boolean sendToNode(String nodeId, ClusterMessage clusterMsg) {
			sent.add(new SentMessage(nodeId, clusterMsg));
			if (failNextSend) {
				failNextSend = false;
				return false;
			}
			return true;
		}
	}

	private static class SentMessage {
		private final String nodeId;
		private final ClusterMessage message;

		private SentMessage(String nodeId, ClusterMessage message) {
			this.nodeId = nodeId;
			this.message = message;
		}
	}
}
