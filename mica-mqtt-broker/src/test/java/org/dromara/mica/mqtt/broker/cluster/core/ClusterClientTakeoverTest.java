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

import org.dromara.mica.mqtt.broker.cluster.MqttBroker;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterClientTakeoverTest {
	@Test
	void lateDisconnectFromPreviousNodeMustNotRemoveNewOwnerSubscriptions() throws Exception {
		RecordingManager manager = new RecordingManager("observer");
		ClusterMqttSessionManager sessions = sessions(manager);
		sessions.registerRemoteClient("device", "new-owner");
		sessions.addSubscribe(new TopicFilter("new/topic"), "device", 0, false);
		disconnect(manager, "device", "old-owner");
		assertEquals("new-owner", sessions.getClientNode("device"));
		assertEquals(1, sessions.getSubscriptions("device").size());
		// A disconnect from the current owner must still clean up.
		disconnect(manager, "device", "new-owner");
		assertNull(sessions.getClientNode("device"));
		assertTrue(sessions.getSubscriptions("device").isEmpty());
	}

	@Test
	void localOnlineMustStopRoutingTheClientToItsPreviousNode() {
		RecordingManager manager = new RecordingManager("local");
		ClusterMqttSessionManager sessions = sessions(manager);
		sessions.registerRemoteClient("device", "old-owner");
		ClusterMqttConnectStatusListener listener = new ClusterMqttConnectStatusListener(null, manager);
		listener.setSessionManager(sessions);
		listener.online(null, "device", "user");
		assertNull(sessions.getClientNode("device"));
	}

	@Test
	void displacedLocalConnectionMustNotBroadcastDisconnectForNewOwner() {
		RecordingManager manager = new RecordingManager("old-owner");
		ClusterMqttSessionManager sessions = sessions(manager);
		sessions.registerRemoteClient("device", "new-owner");
		ClusterMqttConnectStatusListener listener = new ClusterMqttConnectStatusListener(null, manager);
		listener.setSessionManager(sessions);
		listener.offline(null, "device", "user", "taken over");
		assertEquals(0, manager.disconnects.get());
	}

	@Test
	void localCleanupAfterRemoteTakeoverMustKeepRemoteRouteAndSubscriptions() {
		RecordingManager manager = new RecordingManager("old-owner");
		ClusterMqttSessionManager sessions = sessions(manager);
		sessions.registerRemoteClient("device", "new-owner");
		sessions.addSubscribe(new TopicFilter("new/topic"), "device", 0, false);
		sessions.remove("device");
		assertEquals("new-owner", sessions.getClientNode("device"));
		assertEquals(1, sessions.getSubscriptions("device").size());
	}

	@Test
	void disconnectMustNotCleanAnAlreadyConnectedLocalReplacementBeforeOnlineCallback() throws Exception {
		RecordingManager manager = new RecordingManager("local");
		manager.localClient = true;
		ClusterMqttSessionManager sessions = sessions(manager);
		sessions.registerRemoteClient("device", "old-owner");
		sessions.addSubscribe(new TopicFilter("new/topic"), "device", 0, false);
		disconnect(manager, "device", "old-owner");
		assertNull(sessions.getClientNode("device"));
		assertEquals(1, sessions.getSubscriptions("device").size());
	}

	@Test
	void normalLocalDisconnectMustStillBeBroadcastAndCleaned() {
		RecordingManager manager = new RecordingManager("local");
		ClusterMqttSessionManager sessions = sessions(manager);
		sessions.addSubscribe(new TopicFilter("local/topic"), "device", 0, false);
		ClusterMqttConnectStatusListener listener = new ClusterMqttConnectStatusListener(null, manager);
		listener.setSessionManager(sessions);
		sessions.remove("device");
		listener.offline(null, "device", "user", "closed");
		assertEquals(1, manager.disconnects.get());
		assertTrue(sessions.getSubscriptions("device").isEmpty());
	}

	@Test
	@Timeout(25)
	void duplicateClientOnAnotherBrokerMustCloseOldSocketAndKeepNewSubscription() throws Exception {
		int mqtt1 = freePort(), mqtt2 = freePort(), cluster1 = freePort(), cluster2 = freePort();
		MqttClusterBrokerCreator first = broker(mqtt1, cluster1, cluster2);
		MqttClusterBrokerCreator second = broker(mqtt2, cluster2, cluster1);
		try {
			first.start();
			second.start();
			Thread.sleep(1500);
			try (Socket oldSocket = connect(mqtt1, "device")) {
				await(() -> first.getServerCreator().getNodeName().equals(
					second.getClusterSessionManager().getClientNode("device")));
				try (Socket replacement = connect(mqtt2, "device")) {
					await(() -> second.getServerCreator().getNodeName().equals(
						first.getClusterSessionManager().getClientNode("device")));
					oldSocket.setSoTimeout(3000);
					assertEquals(-1, oldSocket.getInputStream().read(), "old broker must close the displaced socket");
					subscribe(replacement, "new/topic");
					await(() -> !second.getClusterSessionManager().getSubscriptions("device").isEmpty());
					disconnect(second.getClusterManager(), "device", first.getServerCreator().getNodeName());
					assertEquals(1, second.getClusterSessionManager().getSubscriptions("device").size());
					assertNull(second.getClusterSessionManager().getClientNode("device"));
					// Verify the replacement is still usable after the old socket's close callback.
					replacement.getOutputStream().write(new byte[]{(byte) 0xc0, 0});
					assertEquals(0xd0, replacement.getInputStream().read());
					assertEquals(0, replacement.getInputStream().read());
				}
			}
		} finally {
			if (second.getClusterManager() != null) second.getClusterManager().stop();
			if (first.getClusterManager() != null) first.getClusterManager().stop();
		}
	}

	private static MqttClusterBrokerCreator broker(int mqttPort, int clusterPort, int peerPort) {
		return MqttBroker.create(MqttServer.create().enableMqtt(mqttPort))
			.clusterConfig(new MqttClusterConfig().enabled(true).clusterHost("127.0.0.1")
				.clusterPort(clusterPort).seedMembers(Arrays.asList("127.0.0.1:" + peerPort)));
	}

	private static Socket connect(int port, String id) throws Exception {
		Socket socket = new Socket("127.0.0.1", port);
		socket.setSoTimeout(3000);
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(body);
		data.writeUTF("MQTT"); data.writeByte(4); data.writeByte(2); data.writeShort(60); data.writeUTF(id);
		socket.getOutputStream().write(0x10);
		socket.getOutputStream().write(body.size());
		socket.getOutputStream().write(body.toByteArray());
		InputStream in = socket.getInputStream();
		assertEquals(0x20, in.read()); assertEquals(2, in.read()); assertEquals(0, in.read()); assertEquals(0, in.read());
		return socket;
	}

	private static void subscribe(Socket socket, String topic) throws Exception {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(body);
		data.writeShort(1); data.writeUTF(topic); data.writeByte(0);
		socket.getOutputStream().write(0x82); socket.getOutputStream().write(body.size());
		socket.getOutputStream().write(body.toByteArray());
		InputStream in = socket.getInputStream();
		assertEquals(0x90, in.read()); assertEquals(3, in.read());
		assertEquals(0, in.read()); assertEquals(1, in.read()); assertEquals(0, in.read());
	}

	private static int freePort() throws Exception {
		try (ServerSocket server = new ServerSocket(0)) { return server.getLocalPort(); }
	}

	private static void await(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + 8_000_000_000L;
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
		assertTrue(condition.getAsBoolean(), "cluster state did not converge");
	}

	private static ClusterMqttSessionManager sessions(MqttClusterManager manager) {
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(new InMemoryMqttSessionManager(), manager);
		manager.setSessionManager(sessions);
		return sessions;
	}

	private static void disconnect(MqttClusterManager manager, String id, String source) throws Exception {
		ClientDisconnectMessage message = new ClientDisconnectMessage(); message.setClientId(id);
		Method handler = MqttClusterManager.class.getDeclaredMethod("handleClusterMessageInternal", ClusterMessage.class, String.class);
		handler.setAccessible(true); handler.invoke(manager, message, source);
	}

	private static final class RecordingManager extends MqttClusterManager {
		private final AtomicInteger disconnects = new AtomicInteger();
		private boolean localClient;
		private RecordingManager(String nodeId) { super(new MqttClusterConfig().enabled(true), nodeId); }
		@Override boolean hasLocalClient(String clientId) { return localClient; }
		@Override public void broadcast(ClusterMessage message) {
			if (message instanceof ClientDisconnectMessage) disconnects.incrementAndGet();
		}
	}
}
