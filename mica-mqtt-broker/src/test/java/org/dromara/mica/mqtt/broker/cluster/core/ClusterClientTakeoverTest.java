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
import org.dromara.mica.mqtt.broker.cluster.message.SharedDispatchToClientMessage;
import org.dromara.mica.mqtt.broker.cluster.message.WillMessageNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.pipeline.ClusterMessageDispatcher;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.InMemoryMqttMessageStore;
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
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

	@Test
	void 忽略没有客户端标识的遗嘱操作() {
		ClusterMqttMessageStore store = new ClusterMqttMessageStore(new InMemoryMqttMessageStore(), new RecordingManager("local"));
		assertFalse(store.addWillMessage(null, new Message()));
		assertFalse(store.clearWillMessage(""));
		assertNull(store.getWillMessage(null));
		store.applyWillMessageLocal(new WillMessageNotifyMessage());
	}

	@Test
	void 延迟的旧遗嘱清理通知不删除新副本() {
		ClusterMqttMessageStore store = new ClusterMqttMessageStore(new InMemoryMqttMessageStore(), new RecordingManager("local"));
		Message oldWill = new Message();
		Message newWill = new Message();
		WillMessageNotifyMessage oldAdd = new WillMessageNotifyMessage();
		oldAdd.setClientId("device");
		oldAdd.setWillToken("old");
		oldAdd.setWillMessage(oldWill);
		store.applyWillMessageLocal(oldAdd);
		WillMessageNotifyMessage newAdd = new WillMessageNotifyMessage();
		newAdd.setClientId("device");
		newAdd.setWillToken("new");
		newAdd.setWillMessage(newWill);
		store.applyWillMessageLocal(newAdd);
		WillMessageNotifyMessage oldClear = new WillMessageNotifyMessage();
		oldClear.setClientId("device");
		oldClear.setWillToken("old");
		oldClear.setClear(true);
		store.applyWillMessageLocal(oldClear);
		assertSame(newWill, store.getWillMessage("device"));
	}

	@Test
	void 遗嘱添加与清理乱序时保留最新状态() {
		ClusterMqttMessageStore store = new ClusterMqttMessageStore(new InMemoryMqttMessageStore(), new RecordingManager("local"));
		long baseVersion = System.currentTimeMillis();
		WillMessageNotifyMessage oldAdd = new WillMessageNotifyMessage();
		oldAdd.setClientId("device");
		oldAdd.setWillToken("old");
		oldAdd.setWillVersion(baseVersion);
		oldAdd.setWillMessage(new Message());
		WillMessageNotifyMessage newAdd = new WillMessageNotifyMessage();
		newAdd.setClientId("device");
		newAdd.setWillToken("new");
		newAdd.setWillVersion(baseVersion + 1);
		Message newWill = new Message();
		newAdd.setWillMessage(newWill);
		store.applyWillMessageLocal(oldAdd);
		store.applyWillMessageLocal(newAdd);
		store.applyWillMessageLocal(oldAdd);
		assertSame(newWill, store.getWillMessage("device"));
		WillMessageNotifyMessage clear = new WillMessageNotifyMessage();
		clear.setClientId("device");
		clear.setWillToken("new");
		clear.setWillVersion(baseVersion + 1);
		clear.setClear(true);
		store.applyWillMessageLocal(clear);
		store.applyWillMessageLocal(newAdd);
		assertNull(store.getWillMessage("device"));
	}

	@Test
	@Timeout(90)
	void 跨节点无遗嘱重连后异常断开不发布旧遗嘱() throws Exception {
		int mqtt1 = freePort(), mqtt2 = freePort(), cluster1 = freePort(), cluster2 = freePort();
		MqttClusterBrokerCreator first = broker(mqtt1, cluster1, cluster2);
		MqttClusterBrokerCreator second = broker(mqtt2, cluster2, cluster1);
		try {
			start(first);
			start(second);
			awaitClusterLink(first.getClusterManager());
			awaitClusterLink(second.getClusterManager());
			try (Socket observer = connect(mqtt1, "will-observer")) {
				subscribe(observer, "will/topic");
				try (Socket old = connectWithWill(mqtt1, "will-device")) {
					await(() -> second.getServerCreator().getMessageStore().getWillMessage("will-device") != null);
					old.getOutputStream().write(new byte[]{(byte) 0xe0, 0});
					old.getOutputStream().flush();
					await(() -> first.getServerCreator().getMessageStore().getWillMessage("will-device") == null);
					await(() -> second.getServerCreator().getMessageStore().getWillMessage("will-device") == null);
				}
				try (Socket replacement = connect(mqtt2, "will-device")) {
					await(() -> second.getServerCreator().getNodeName().equals(
						first.getClusterSessionManager().getClientNode("will-device")));
					replacement.setSoLinger(true, 0);
				}
				await(() -> first.getClusterSessionManager().getClientNode("will-device") == null);
				assertEquals(0, readPublish(observer, 1500), "无遗嘱连接异常断开时不能发布旧遗嘱");
			}
		} finally {
			if (second.getClusterManager() != null) second.getClusterManager().stop();
			if (first.getClusterManager() != null) first.getClusterManager().stop();
		}
	}

	private static Socket connectWithWill(int port, String id) throws Exception {
		Socket socket = new Socket("127.0.0.1", port);
		socket.setSoTimeout(3000);
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(body);
		data.writeUTF("MQTT");
		data.writeByte(4);
		data.writeByte(6);
		data.writeShort(60);
		data.writeUTF(id);
		data.writeUTF("will/topic");
		data.writeUTF("old-will");
		socket.getOutputStream().write(0x10);
		socket.getOutputStream().write(body.size());
		socket.getOutputStream().write(body.toByteArray());
		InputStream input = socket.getInputStream();
		assertEquals(0x20, input.read());
		assertEquals(2, input.read());
		assertEquals(0, input.read());
		assertEquals(0, input.read());
		return socket;
	}

	@Test
	@Timeout(90)
	void sharedSubscribersAcrossNodesReceiveExactlyOneMessagePerGroup() throws Exception {
		int mqtt1 = freePort(), mqtt2 = freePort(), cluster1 = freePort(), cluster2 = freePort();
		MqttClusterBrokerCreator first = MqttBroker.create(MqttServer.create().enableMqtt(mqtt1))
			.clusterConfig(new MqttClusterConfig().enabled(true).clusterHost("127.0.0.1")
				.clusterPort(cluster1).seedMembers(Arrays.asList("127.0.0.1:" + cluster2)).sharedSubStrategy("round_robin"));
		MqttClusterBrokerCreator second = broker(mqtt2, cluster2, cluster1);
		try {
			start(first);
			start(second);
			awaitClusterLink(first.getClusterManager());
			awaitClusterLink(second.getClusterManager());
			try (Socket publisher = connect(mqtt1, "publisher");
				 Socket local = connect(mqtt1, "local-shared");
				 Socket remote = connect(mqtt2, "remote-shared");
				 Socket otherLocal = connect(mqtt1, "local-other");
				 Socket otherRemote = connect(mqtt2, "remote-other");
				 Socket queueLocal = connect(mqtt1, "local-queue");
				 Socket queueRemote = connect(mqtt2, "remote-queue");
				 Socket normal = connect(mqtt2, "remote-normal")) {
				subscribe(local, "$share/group/cluster/test");
				subscribe(remote, "$share/group/cluster/test");
				subscribe(otherLocal, "$share/other/cluster/test");
				subscribe(otherRemote, "$share/other/cluster/test");
				subscribe(queueLocal, "$queue/cluster/test");
				subscribe(queueRemote, "$queue/cluster/test");
				subscribe(normal, "cluster/test");
				await(() -> first.getClusterSessionManager().searchAllSubscribe("cluster/test").size() == 7);
				await(() -> second.getClusterSessionManager().searchAllSubscribe("cluster/test").size() == 7);
				int localCount = 0;
				int remoteCount = 0;
				for (int i = 0; i < 4; i++) {
					if ((i & 1) == 0) {
						ByteArrayOutputStream body = new ByteArrayOutputStream();
						DataOutputStream data = new DataOutputStream(body);
						data.writeUTF("cluster/test");
						data.writeByte(i);
						publisher.getOutputStream().write(0x30);
						publisher.getOutputStream().write(body.size());
						publisher.getOutputStream().write(body.toByteArray());
						publisher.getOutputStream().flush();
					} else {
						first.getClusterManager().publish("cluster/test", new byte[]{(byte) i}, 0, false);
					}
					int localReceived = readPublish(local, 800);
					int remoteReceived = readPublish(remote, 800);
					assertEquals(1, localReceived + remoteReceived, "每条消息应由组内一个订阅者收到");
					assertEquals(1, readPublish(otherLocal, 800) + readPublish(otherRemote, 800), "另一共享组应收到一次");
					assertEquals(1, readPublish(queueLocal, 800) + readPublish(queueRemote, 800), "队列订阅应收到一次");
					assertEquals(1, readPublish(normal, 1500), "普通订阅者应收到每条消息");
					localCount += localReceived;
					remoteCount += remoteReceived;
				}
				assertTrue(localCount > 0 && remoteCount > 0, "轮询策略应覆盖两个节点");
				for (Socket subscriber : Arrays.asList(local, remote, otherLocal, otherRemote, queueLocal, queueRemote, normal)) {
					assertEquals(0, readPublish(subscriber, 200), "不应残留重复消息");
				}
				Message message = new Message();
				message.setMessageType(MessageType.UP_STREAM);
				message.setTopic("cluster/test");
				message.setPayload(new byte[]{9});
				message.setQos(0);
				SharedDispatchToClientMessage dispatch = new SharedDispatchToClientMessage();
				dispatch.setClientId("already-disconnected");
				dispatch.setGroupKey("$share/group");
				dispatch.setTopic("cluster/test");
				dispatch.setMessage(message);
				first.getClusterManager().sendToNode(second.getServerCreator().getNodeName(), dispatch);
				assertEquals(1, readPublish(remote, 2000), "目标失效后应在原组重选");
				assertEquals(0, readPublish(otherLocal, 300) + readPublish(otherRemote, 300)
					+ readPublish(queueLocal, 300) + readPublish(queueRemote, 300), "重选不能投递到其他组");
				ClusterMessageDispatcher dispatcher = new ClusterMessageDispatcher(first.build(), first.getClusterManager(),
					first.getClusterSessionManager(), null);
				PublishContext context = PublishContext.builder().clientId("publisher").topic("cluster/test")
					.qos(MqttQoS.QOS0).payload(new byte[]{10}).build();
				dispatcher.retryLocalSubscriber(context, new Subscribe("$share/group/cluster/test", "already-disconnected", 0),
					MqttProperties.NO_PROPERTIES);
				assertEquals(1, readPublish(local, 1500) + readPublish(remote, 1500), "本地目标失效后应在原组重选");
				assertEquals(0, readPublish(otherLocal, 300) + readPublish(otherRemote, 300), "重选不能投递到其他组");
			}
		} finally {
			if (second.getClusterManager() != null) second.getClusterManager().stop();
			if (first.getClusterManager() != null) first.getClusterManager().stop();
		}
	}

	private static int readPublish(Socket socket, int timeout) throws Exception {
		socket.setSoTimeout(timeout);
		InputStream input = socket.getInputStream();
		int header;
		try {
			header = input.read();
		} catch (SocketTimeoutException e) {
			return 0;
		}
		assertEquals(0x30, header);
		int remaining = input.read();
		while (remaining-- > 0) {
			assertTrue(input.read() >= 0);
		}
		return 1;
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
