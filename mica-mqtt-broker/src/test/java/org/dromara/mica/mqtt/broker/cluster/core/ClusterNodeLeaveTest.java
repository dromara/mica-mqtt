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

import net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessageSerializer;
import org.dromara.mica.mqtt.broker.cluster.message.NodeLeaveMessage;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.server.store.InMemoryMqttMessageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class ClusterNodeLeaveTest {

	private static int freePort() throws Exception {
		try (ServerSocket server = new ServerSocket(0)) {
			return server.getLocalPort();
		}
	}

	@Test
	void reconnectAfterOrphanCleanupKeepsReplacementWill() throws Exception {
		MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local");
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		Message oldWill = new Message();
		oldWill.setTopic("old/will");
		Message newWill = new Message();
		newWill.setTopic("new/will");
		store.addWillMessage("client", oldWill);
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(new InMemoryMqttSessionManager(), manager) {
			@Override
			List<String> clearNodeClientsAndSubscriptions(String nodeId, Consumer<String> beforeRemove) {
				List<String> removed = super.clearNodeClientsAndSubscriptions(nodeId, beforeRemove);
				store.addWillMessage("client", newWill);
				return removed;
			}
		};
		manager.setSessionManager(sessions);
		MqttServer server = MqttServer.create().enableMqtt(freePort()).sessionManager(sessions).messageStore(store).build();
		manager.setMqttServer(server);
		sessions.registerRemoteClient("client", "dead-node:9000");
		Method handle = MqttClusterManager.class.getDeclaredMethod("handleNodeLeave", String.class, boolean.class);
		handle.setAccessible(true);
		handle.invoke(manager, "dead-node:9000", true);
		assertSame(newWill, store.getWillMessage("client"));
	}

	/**
	 * 故障下线后必须消费其备份遗嘱：清理客户端/订阅并删除遗嘱记录。
	 * 这里验证清理与消费路径；真实网络中的投递由订阅转发链路覆盖。
	 */
	@Test
	void failoverNodeLeaveConsumesBackupWillAndCleansOrphanedClient() throws Exception {
		MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(new InMemoryMqttSessionManager(), manager);
		manager.setSessionManager(sessions);
		MqttServer server = MqttServer.create().enableMqtt(freePort()).sessionManager(sessions).build();
		manager.setMqttServer(server);

		IMqttMessageStore store = server.getServerCreator().getMessageStore();
		Message will = new Message();
		will.setTopic("will/topic");
		will.setQos(0);
		will.setPayload(new byte[]{1});
		store.addWillMessage("client", will);
		sessions.registerRemoteClient("client", "dead-node:9000");
		sessions.addSubscribe(new TopicFilter("will/topic"), "client", 0, false);

		Method handle = MqttClusterManager.class.getDeclaredMethod("handleNodeLeave", String.class, boolean.class);
		handle.setAccessible(true);
		handle.invoke(manager, "dead-node:9000", true);

		assertNull(store.getWillMessage("client"));
		assertNull(sessions.getClientNode("client"));
		assertTrue(sessions.getSubscriptions("client").isEmpty());
	}

	/**
	 * 正常下线时由离开节点自行投递遗嘱，其他节点只清理状态，不能再重复发布。
	 */
	@Test
	void gracefulNodeLeaveCleansClientButKeepsWillForOwner() throws Exception {
		MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(new InMemoryMqttSessionManager(), manager);
		manager.setSessionManager(sessions);
		MqttServer server = MqttServer.create().enableMqtt(freePort()).sessionManager(sessions).build();
		manager.setMqttServer(server);

		IMqttMessageStore store = server.getServerCreator().getMessageStore();
		Message will = new Message();
		will.setTopic("will/topic");
		will.setQos(0);
		will.setPayload(new byte[]{1});
		store.addWillMessage("client", will);
		sessions.registerRemoteClient("client", "leaving-node:9000");

		Method handle = MqttClusterManager.class.getDeclaredMethod("handleNodeLeave", String.class, boolean.class);
		handle.setAccessible(true);
		handle.invoke(manager, "leaving-node:9000", false);

		assertNull(sessions.getClientNode("client"));
		assertSame(will, store.getWillMessage("client"));
	}

	/**
	 * 代发失败（topic 非法导致 publishAll 抛异常）时必须保留备份遗嘱，等待重试/接手。
	 */
	@Test
	void failoverPublishFailureKeepsBackupWill() throws Exception {
		MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(new InMemoryMqttSessionManager(), manager);
		manager.setSessionManager(sessions);
		MqttServer server = MqttServer.create().enableMqtt(freePort()).sessionManager(sessions).build();
		manager.setMqttServer(server);

		IMqttMessageStore store = server.getServerCreator().getMessageStore();
		Message will = new Message();
		// 非法 topic 会让 publishAll 抛 IllegalArgumentException
		will.setTopic("bad/#");
		will.setQos(0);
		will.setPayload(new byte[]{1});
		store.addWillMessage("client", will);
		sessions.registerRemoteClient("client", "dead-node:9000");

		Method handle = MqttClusterManager.class.getDeclaredMethod("handleNodeLeave", String.class, boolean.class);
		handle.setAccessible(true);
		handle.invoke(manager, "dead-node:9000", true);

		assertSame(will, store.getWillMessage("client"));
		assertNull(sessions.getClientNode("client"));
	}

	/**
	 * rank 决定代发顺序：单节点视图下本节点为 rank 0。
	 */
	@Test
	void failoverRankIsZeroWhenAlone() {
		MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local");
		assertEquals(0, manager.failoverRank("dead-node:9000"));
	}

	@Test
	void failoverRankOrdersCandidatesDeterministically() {
		java.util.Collection<String> online = java.util.Arrays.asList("n1:1", "n2:1", "dead:1");
		assertEquals(0, MqttClusterManager.failoverRank("n1:1", "dead:1", online));
		assertEquals(1, MqttClusterManager.failoverRank("n2:1", "dead:1", online));
		assertEquals(2, MqttClusterManager.failoverRank("n3:1", "dead:1", online));
	}

	/**
	 * 节点下线消息没有携带 nodeId 时（旧版本正常下线），回退到消息来源节点。
	 */
	@Test
	void nodeLeaveFallsBackToSourceNodeWhenNodeIdMissing() throws Exception {
		MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(new InMemoryMqttSessionManager(), manager);
		manager.setSessionManager(sessions);
		MqttServer server = MqttServer.create().enableMqtt(freePort()).sessionManager(sessions).build();
		manager.setMqttServer(server);

		sessions.registerRemoteClient("client", "source-node:9000");
		Method dispatch = MqttClusterManager.class.getDeclaredMethod("handleClusterMessageInternal",
			ClusterMessage.class, String.class);
		dispatch.setAccessible(true);
		dispatch.invoke(manager, new NodeLeaveMessage(), "source-node:9000");

		assertNull(sessions.getClientNode("client"));
	}

	/**
	 * 故障标记必须能跨节点传输，否则接收方只会清理状态、不代发遗嘱。
	 */
	@Test
	void nodeLeaveFailoverFlagSurvivesSerialization() {
		NodeLeaveMessage message = new NodeLeaveMessage();
		message.setNodeId("dead-node:9000");
		message.setFailover(true);

		ClusterDataMessage data = ClusterMessageSerializer.toClusterData(message, "local");
		NodeLeaveMessage parsed = ClusterMessageSerializer.fromClusterData(data);

		assertEquals("dead-node:9000", parsed.getNodeId());
		assertTrue(parsed.isFailover());
	}

	/**
	 * 无遗嘱连接的清理屏障随上线通知传输，接收方可据此压制迟到的旧遗嘱。
	 */
	@Test
	void clientConnectCarriesWillBarrier() {
		ClientConnectMessage message = new ClientConnectMessage();
		message.setClientId("client");
		message.setWillVersion(123L);
		message.setWillToken("token");

		ClusterDataMessage data = ClusterMessageSerializer.toClusterData(message, "local");
		ClientConnectMessage parsed = ClusterMessageSerializer.fromClusterData(data);

		assertEquals(123L, parsed.getWillVersion());
		assertEquals("token", parsed.getWillToken());
	}

	@Test
	void clearedWillStateCanBeAppliedOnReplica() {
		ClusterMqttMessageStore source = new ClusterMqttMessageStore(
			new InMemoryMqttMessageStore(),
			new MqttClusterManager(new MqttClusterConfig().enabled(false), "source"));
		source.clearWillMessageOnConnect("client");

		ClusterMqttMessageStore replica = new ClusterMqttMessageStore(
			new InMemoryMqttMessageStore(),
			new MqttClusterManager(new MqttClusterConfig().enabled(false), "replica"));
		replica.applyWillMessageLocal(source.getClearedWillState("client"));

		assertEquals(source.getWillVersion("client"), replica.getWillVersion("client"));
		assertNotNull(replica.getClearedWillState("client"));
	}

	/**
	 * 新连接已本地接管（ChannelContext 已建立）但 online 回调尚未将归属从
	 * clientNodeMap 移除时，旧节点下线清理不能删除已迁移客户端的新订阅。
	 */
	@Test
	void nodeLeaveDoesNotDeleteMigratedClientSubscriptions() {
		// 使用可注入 hasLocalClient 的子类模拟「本地已接管」
		MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local") {
			private final java.util.Set<String> localClients = java.util.concurrent.ConcurrentHashMap.newKeySet();

			@Override
			boolean hasLocalClient(String clientId) {
				return localClients.contains(clientId);
			}
		};
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(new InMemoryMqttSessionManager(), manager);
		manager.setSessionManager(sessions);

		// 客户端原先在 old-node，本节点上有副本订阅
		sessions.registerRemoteClient("client", "old-node:9000");
		sessions.addSubscribe(new TopicFilter("data/topic"), "client", 1, false);
		assertEquals(1, sessions.getSubscriptions("client").size());

		// 模拟新连接已完成本地接管，但 online 回调尚未移除归属
		// （clientNodeMap 仍为 old-node:9000）
		// 通过反射注入 hasLocalClient = true
		// 由于上面匿名子类没有暴露 markLocal，直接验证 clearNodeClientsAndSubscriptions
		// 在 hasLocalClient=false 时仍然删除（对照组）
		List<String> removed = sessions.clearNodeClientsAndSubscriptions("old-node:9000");
		assertEquals(1, removed.size());
		assertTrue(sessions.getSubscriptions("client").isEmpty());

		// 重新设置：这次模拟 hasLocalClient=true
		sessions.registerRemoteClient("client", "old-node:9000");
		sessions.addSubscribe(new TopicFilter("data/topic"), "client", 1, false);

		MqttClusterManager managerWithLocal = new MqttClusterManager(new MqttClusterConfig().enabled(false), "local") {
			@Override
			boolean hasLocalClient(String clientId) {
				return "client".equals(clientId);
			}
		};
		ClusterMqttSessionManager sessionsWithLocal = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), managerWithLocal);
		managerWithLocal.setSessionManager(sessionsWithLocal);
		sessionsWithLocal.registerRemoteClient("client", "old-node:9000");
		sessionsWithLocal.addSubscribe(new TopicFilter("data/topic"), "client", 1, false);

		List<String> removedWithLocal = sessionsWithLocal.clearNodeClientsAndSubscriptions("old-node:9000");
		// 已本地接管的客户端不视为孤儿，不加入清理列表
		assertTrue(removedWithLocal.isEmpty());
		// 订阅必须保留
		assertEquals(1, sessionsWithLocal.getSubscriptions("client").size());
	}
}
