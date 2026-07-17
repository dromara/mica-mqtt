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

package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.config.MqttStorageConfig;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in end-to-end MQTT acceptance test over three real brokers and clients.
 * Run with {@code mvn -pl mica-mqtt-broker -Pcluster-integration -DforkCount=0 test}.
 */
class ClusterMqttIT {
	@TempDir
	Path tempDir;

	@Test
	void sharedQosRoutingAndSubscriberMigrationAreEndToEnd() throws Exception {
		List<Integer> mqttPorts = reservePorts(3);
		List<Integer> clusterPorts = reservePorts(3);
		List<String> seeds = new ArrayList<>();
		for (int clusterPort : clusterPorts) {
			seeds.add("127.0.0.1:" + clusterPort);
		}
		List<BrokerNode> brokers = new ArrayList<>();
		List<MqttClient> clients = new ArrayList<>();
		try {
			for (int i = 0; i < 3; i++) {
				brokers.add(startBroker(i, mqttPorts.get(i), clusterPorts.get(i),
					new ArrayList<>(seeds.subList(0, i))));
			}
			await(Duration.ofSeconds(20), () -> brokers.stream()
				.allMatch(node -> node.creator.getClusterManager().getClusterNodeIds().size() == 3
					&& node.creator.getClusterManager().isTopologyHealthy()));

			AtomicInteger sharedOne = new AtomicInteger();
			AtomicInteger sharedTwo = new AtomicInteger();
			AtomicInteger normal = new AtomicInteger();
			MqttClient subscriberOne = connect(mqttPorts.get(0), "shared-one");
			MqttClient subscriberTwo = connect(mqttPorts.get(1), "shared-two");
			MqttClient normalSubscriber = connect(mqttPorts.get(0), "normal-one");
			MqttClient publisher = connect(mqttPorts.get(2), "publisher");
			clients.add(subscriberOne);
			clients.add(subscriberTwo);
			clients.add(normalSubscriber);
			clients.add(publisher);
			await(Duration.ofSeconds(10), () -> clients.stream().allMatch(MqttClient::isConnected));

			subscriberOne.subQos2("$share/workers/cluster/jobs/#",
				(context, topic, message, payload) -> sharedOne.incrementAndGet());
			subscriberTwo.subQos2("$share/workers/cluster/jobs/#",
				(context, topic, message, payload) -> sharedTwo.incrementAndGet());
			normalSubscriber.subQos2("cluster/jobs/#",
				(context, topic, message, payload) -> normal.incrementAndGet());
			awaitRoutes(brokers.get(2), 3);

			for (int i = 0; i < 40; i++) {
				assertTrue(publisher.publish("cluster/jobs/first", new byte[]{(byte) i}, MqttQoS.QOS2));
			}
			await(Duration.ofSeconds(20), () -> sharedOne.get() + sharedTwo.get() == 40 && normal.get() == 40);
			assertEquals(40, sharedOne.get() + sharedTwo.get(), "shared group must receive exactly once");
			assertEquals(40, normal.get(), "normal remote subscription must receive every publish");

			// Move the first shared subscriber to another broker and re-subscribe.
			subscriberOne.stop();
			clients.remove(subscriberOne);
			MqttClient migrated = connect(mqttPorts.get(1), "shared-one");
			clients.add(migrated);
			await(Duration.ofSeconds(10), migrated::isConnected);
			migrated.subQos1("$share/workers/cluster/jobs/#",
				(context, topic, message, payload) -> sharedOne.incrementAndGet());
			awaitRoutes(brokers.get(2), 3);

			for (int i = 0; i < 20; i++) {
				assertTrue(publisher.publish("cluster/jobs/second", new byte[]{(byte) i}, MqttQoS.QOS1));
			}
			await(Duration.ofSeconds(20), () -> sharedOne.get() + sharedTwo.get() == 60 && normal.get() == 60);
			assertEquals(60, sharedOne.get() + sharedTwo.get());
			assertEquals(60, normal.get());
		} finally {
			for (MqttClient client : clients) {
				client.stop();
			}
			for (int i = brokers.size() - 1; i >= 0; i--) {
				brokers.get(i).close();
			}
		}
	}

	private BrokerNode startBroker(int index, int mqttPort, int clusterPort, List<String> seeds) {
		MqttClusterConfig config = new MqttClusterConfig()
			.enabled(true)
			.clusterHost("127.0.0.1")
			.clusterPort(clusterPort)
			.seedMembers(seeds)
			.sharedSubStrategy("round_robin")
			.heartbeatInterval(500L)
			.nodeTimeout(3_000L)
			.storageConfig(new MqttStorageConfig()
				.enabled(true)
				.dataDir(tempDir.resolve("broker-" + index).toString()));
		MqttClusterBrokerCreator creator = MqttBroker.create(MqttServer.create()
			.name("cluster-mqtt-it-" + index)
			.nodeName("127.0.0.1:" + clusterPort)
			.enableMqtt(mqttPort))
			.clusterConfig(config);
		creator.start();
		return new BrokerNode(creator);
	}

	private static MqttClient connect(int port, String clientId) {
		return MqttClient.create()
			.ip("127.0.0.1")
			.port(port)
			.clientId(clientId)
			.reconnect(false)
			.connectSync();
	}

	private static void awaitRoutes(BrokerNode publisherNode, int expected) throws Exception {
		Duration timeout = Duration.ofSeconds(15);
		try {
			await(timeout, () -> {
			List<Subscribe> subscriptions = publisherNode.creator.getClusterSessionManager()
				.searchAllSubscribe("cluster/jobs/probe");
			return subscriptions.size() == expected;
			});
		} catch (AssertionError error) {
			List<Subscribe> subscriptions = publisherNode.creator.getClusterSessionManager()
				.searchAllSubscribe("cluster/jobs/probe");
			throw new AssertionError("Expected " + expected + " routes but found " + subscriptions.size()
				+ ": " + subscriptions, error);
		}
	}

	private static void await(Duration timeout, Condition condition) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.evaluate()) {
				return;
			}
			Thread.sleep(25L);
		}
		throw new AssertionError("Condition did not become true within " + timeout);
	}

	private static List<Integer> reservePorts(int count) throws IOException {
		List<ServerSocket> sockets = new ArrayList<>(count);
		List<Integer> ports = new ArrayList<>(count);
		try {
			for (int i = 0; i < count; i++) {
				ServerSocket socket = new ServerSocket(0);
				sockets.add(socket);
				ports.add(socket.getLocalPort());
			}
		} finally {
			for (ServerSocket socket : sockets) {
				socket.close();
			}
		}
		return ports;
	}

	private interface Condition {
		boolean evaluate() throws Exception;
	}

	private static class BrokerNode implements AutoCloseable {
		private final MqttClusterBrokerCreator creator;

		private BrokerNode(MqttClusterBrokerCreator creator) {
			this.creator = creator;
		}

		@Override
		public void close() {
			creator.getClusterManager().stop();
			if (creator.getClusterStorage() != null) {
				creator.getClusterStorage().stop();
			}
		}
	}
}
