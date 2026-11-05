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

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.server.TioServerConfig;
import net.dreamlu.mica.net.server.cluster.core.ClusterImpl;
import org.dromara.mica.mqtt.broker.cluster.MqttBroker;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterClientTakeoverTest {

	private static final TioServerConfig TEST_CONFIG = new TioServerConfig("cluster-takeover-test", null, null);

	/**
	 * 连接已失效时 online() 不广播上线，也不接管该 clientId 的远端归属。
	 * <p>
	 * 远端归属由 CLIENT_CONNECT / CLIENT_DISCONNECT 广播维护，是当前事实。若此时清掉它，
	 * 本节点会误以为该 client 归自己，getRemoteNodesWithSubscriber 便不再把它算作远端
	 * 订阅者，本节点发布的消息也就不会再转发给真正的 owner（消息丢失）。
	 * </p>
	 * <p>
	 * 归属仍在远端时不下线广播也是对的：远端会在自己的客户端断开时广播下线。
	 * </p>
	 */
	@Test
	void staleOnlineMustNotStealRemoteOwnershipNorBroadcastConnect() {
		RecordingManager manager = new RecordingManager("local");
		ClusterMqttSessionManager sessions = sessions(manager);
		sessions.registerRemoteClient("device", "old-owner");
		ClusterMqttConnectStatusListener listener = new ClusterMqttConnectStatusListener(null, manager);
		listener.setSessionManager(sessions);

		listener.online(deadContext(), "device", "user");
		// 连接已失效，不广播上线
		assertEquals(0, manager.connects.get());
		// 远端归属保持不变，本节点仍会把该 clientId 的消息转发给真正的 owner
		assertEquals("old-owner", sessions.getClientNode("device"));

		// 归属仍归远端，本机不是 owner，不广播下线
		listener.offline(deadContext(), "device", "user", "closed");
		assertEquals(0, manager.disconnects.get());
	}

	/**
	 * 归属已移交远端时，本地清理只清连接态，订阅副本必须保留：
	 * V1 全量复制下，订阅副本正是本节点把消息转发给新 owner 的依据。
	 */
	@Test
	void localCleanupAfterRemoteTakeoverMustKeepSubscriptionsAndClearConnectionState() {
		RecordingManager manager = new RecordingManager("old-owner");
		RecordingSessionManager delegate = new RecordingSessionManager();
		ClusterMqttSessionManager sessions = sessions(manager, delegate);
		sessions.registerRemoteClient("device", "new-owner");
		sessions.addSubscribe(new TopicFilter("new/topic"), "device", 0, false);

		sessions.remove("device");

		// 远端归属保留，订阅副本保留
		assertEquals("new-owner", sessions.getClientNode("device"));
		assertEquals(1, sessions.getSubscriptions("device").size());
		// 只清连接态，不整体删除
		assertEquals(0, delegate.removed.get());
		assertEquals(1, delegate.sessionStateRemoved.get());
	}

	/**
	 * 归属仍在本地时，本地清理要整体删除（连接态 + 订阅）。
	 */
	@Test
	void localCleanupWithLocalOwnershipMustRemoveEverything() {
		RecordingManager manager = new RecordingManager("local");
		RecordingSessionManager delegate = new RecordingSessionManager();
		ClusterMqttSessionManager sessions = sessions(manager, delegate);
		sessions.addSubscribe(new TopicFilter("local/topic"), "device", 0, false);

		sessions.remove("device");

		assertEquals(1, delegate.removed.get());
		assertEquals(0, delegate.sessionStateRemoved.get());
		assertTrue(sessions.getSubscriptions("device").isEmpty());
	}

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

	/**
	 * 本机已有该 clientId 的活连接时，旧节点发来的下线不能清掉它的订阅。
	 * <p>
	 * 覆盖的是「归属条目已不在本节点」这条分支：下线来自旧 owner，归属不匹配，
	 * 既不摘除路由也不删除订阅。
	 * </p>
	 */
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
	@Timeout(90)
	void duplicateClientOnAnotherBrokerMustCloseOldSocketAndKeepNewSubscription() throws Exception {
		// freePort() 是「先占后放再绑定」，存在 TOCTOU 竞态：端口可能恰好被别的进程抢占。
		// 这里对端口冲突做有限次重试，避免 CI 上偶发失败。
		BindException last = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				runTakeoverScenario();
				return;
			} catch (BindException e) {
				last = e;
			}
		}
		throw last;
	}

	private static void runTakeoverScenario() throws Exception {
		int mqtt1 = freePort(), mqtt2 = freePort(), cluster1 = freePort(), cluster2 = freePort();
		MqttClusterBrokerCreator first = broker(mqtt1, cluster1, cluster2);
		MqttClusterBrokerCreator second = broker(mqtt2, cluster2, cluster1);
		try {
			start(first);
			start(second);
			// 等集群链路真正建立再发业务流量：连接事件是一次性广播，落空就没了。
			// t-io cluster 客户端 1s 重连，这里轮询等待成员通道连上，比固定 sleep 更稳。
			awaitClusterLink(first.getClusterManager());
			awaitClusterLink(second.getClusterManager());
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
					// 旧 owner 节点发来的迟到下线，不能清掉新 owner 的订阅与路由。
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

	/**
	 * 启动 broker，端口被占用时把包装过的异常还原成 {@link BindException}，便于重试。
	 */
	private static void start(MqttClusterBrokerCreator creator) throws BindException {
		try {
			creator.start();
		} catch (RuntimeException e) {
			BindException bind = findBindException(e);
			if (bind != null) {
				throw bind;
			}
			throw e;
		}
	}

	private static BindException findBindException(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof BindException) {
				return (BindException) current;
			}
			current = current.getCause();
		}
		return null;
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
		return sessions(manager, new InMemoryMqttSessionManager());
	}

	private static ClusterMqttSessionManager sessions(MqttClusterManager manager, IMqttSessionManager delegate) {
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(delegate, manager);
		manager.setSessionManager(sessions);
		return sessions;
	}

	/**
	 * 等待集群链路建立：成员通道连上后，本节点的广播才能送达对端。
	 */
	private static void awaitClusterLink(MqttClusterManager manager) throws Exception {
		Field field = MqttClusterManager.class.getDeclaredField("cluster");
		field.setAccessible(true);
		Object cluster = field.get(manager);
		assertTrue(cluster instanceof ClusterImpl, "unexpected cluster implementation: " + cluster);
		ClusterImpl clusterImpl = (ClusterImpl) cluster;
		await(() -> !clusterImpl.getOnlineMembers().isEmpty());
	}

	private static void disconnect(MqttClusterManager manager, String id, String source) throws Exception {
		ClientDisconnectMessage message = new ClientDisconnectMessage(); message.setClientId(id);
		Method handler = MqttClusterManager.class.getDeclaredMethod("handleClusterMessageInternal", ClusterMessage.class, String.class);
		handler.setAccessible(true); handler.invoke(manager, message, source);
	}

	/**
	 * 虚拟 context 默认 closed=true，模拟连接已失效。
	 */
	private static ChannelContext deadContext() {
		return new TestChannelContext(TEST_CONFIG, "dead-context");
	}

	private static final class TestChannelContext extends ChannelContext {
		TestChannelContext(TioServerConfig tioConfig, String id) {
			super(tioConfig, id);
		}
		@Override protected Node createClientNode(AsynchronousSocketChannel asynchronousSocketChannel) {
			return new Node("127.0.0.1", 1883);
		}
		@Override public boolean isServer() { return true; }
	}

	private static final class RecordingSessionManager extends InMemoryMqttSessionManager {
		private final AtomicInteger removed = new AtomicInteger();
		private final AtomicInteger sessionStateRemoved = new AtomicInteger();

		@Override
		public void remove(String clientId) {
			removed.incrementAndGet();
			super.remove(clientId);
		}

		@Override
		public void removeSessionState(String clientId) {
			sessionStateRemoved.incrementAndGet();
			super.removeSessionState(clientId);
		}
	}

	private static final class RecordingManager extends MqttClusterManager {
		private final AtomicInteger connects = new AtomicInteger();
		private final AtomicInteger disconnects = new AtomicInteger();
		private boolean localClient;
		private RecordingManager(String nodeId) { super(new MqttClusterConfig().enabled(true), nodeId); }
		@Override boolean hasLocalClient(String clientId) { return localClient; }
		@Override public void broadcast(ClusterMessage message) {
			if (message instanceof ClientConnectMessage) connects.incrementAndGet();
			if (message instanceof ClientDisconnectMessage) disconnects.incrementAndGet();
		}
	}
}
