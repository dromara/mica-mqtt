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
import org.dromara.mica.mqtt.broker.cluster.store.InflightStore;
import org.dromara.mica.mqtt.broker.cluster.store.H2MvStoreImpl;
import org.dromara.mica.mqtt.broker.cluster.store.H2SessionStore;
import org.dromara.mica.mqtt.broker.cluster.store.H2SharedSubStore;
import org.dromara.mica.mqtt.broker.cluster.store.SessionStore;
import org.dromara.mica.mqtt.broker.cluster.store.SharedSubStore;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterMqttSessionManagerRoutingTest {
	@TempDir
	Path tempDir;

	@Test
	void preservesRawSharedGroupsForClusterDispatcher() {
		MqttClusterConfig config = new MqttClusterConfig().enabled(true);
		MqttClusterManager clusterManager = new MqttClusterManager(config, "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);
		clusterManager.setSessionManager(sessions);

		sessions.addSubscribe(new TopicFilter("sensors/+/temp"), "normal-local", 1, false, false, 0);
		sessions.addSubscribe(new TopicFilter("$share/g1/sensors/+/temp"), "shared-local-1", 1, false, false, 0);
		sessions.addSubscribe(new TopicFilter("$share/g1/sensors/+/temp"), "shared-local-2", 1, false, false, 0);
		sessions.syncRemoteSubscriptions("shared-remote", "node-2", Arrays.asList(
			new Subscribe("$share/g1/sensors/+/temp", "shared-remote", 1)
		));

		List<Subscribe> all = sessions.searchAllSubscribe("sensors/a/temp");
		assertEquals(4, all.size());
		assertTrue(all.stream().allMatch(sub -> sub.getTopicFilter() != null));
		assertEquals(3, all.stream().filter(sub -> sub.getTopicFilter().startsWith("$share/g1/")).count());

		List<Subscribe> localDelivery = sessions.searchSubscribe("sensors/a/temp");
		assertEquals(1, localDelivery.size());
		assertEquals("normal-local", localDelivery.get(0).getClientId());
	}

	@Test
	void unsubscribeRemovesEntryFromClusterRouteTable() {
		MqttClusterConfig config = new MqttClusterConfig().enabled(true);
		MqttClusterManager clusterManager = new MqttClusterManager(config, "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);

		sessions.addSubscribe(new TopicFilter("$queue/jobs/#"), "worker", 1, false, false, 0);
		assertEquals(1, sessions.searchAllSubscribe("jobs/a").size());
		sessions.removeSubscribe("$queue/jobs/#", "worker");
		assertTrue(sessions.searchAllSubscribe("jobs/a").isEmpty());
	}

	@Test
	void fullStateIncludesLocalOwnersAndPersistentOfflineRoutes() {
		MqttClusterManager clusterManager = new MqttClusterManager(
			new MqttClusterConfig().enabled(true), "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager);
		sessions.markLocalClient("persistent");
		sessions.setSessionExpiryInterval("persistent", 3600, false);
		sessions.addSubscribe(new TopicFilter("devices/#"), "persistent", 1, false, false, 0);

		assertEquals("node-1", sessions.getStateClientNodeMap().get("persistent"));
		assertTrue(sessions.isPersistentSession("persistent"));
		sessions.markRemoteClientOffline("persistent");
		assertEquals(1, sessions.searchAllSubscribe("devices/a").size());
	}

	@Test
	void pendingPublishLifecycleUsesRealClientAndPacketId() {
		MqttClusterManager clusterManager = new MqttClusterManager(new MqttClusterConfig().enabled(true), "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);
		RecordingInflightStore inflight = new RecordingInflightStore();
		sessions.setInflightStore(inflight, 30_000L);
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("jobs/a")
			.payload(new byte[]{1, 2, 3})
			.qos(MqttQoS.QOS1)
			.messageId(42)
			.build();

		sessions.addPendingPublish("client-1", 42, new MqttPendingPublish(message, MqttQoS.QOS2));
		assertEquals("client-1", inflight.clientId);
		assertEquals(42, inflight.packetId);
		assertEquals("jobs/a", inflight.topic);
		assertEquals(1, inflight.qos);
		sessions.removePendingPublish("client-1", 42);
		assertTrue(inflight.removed);
		sessions.markPendingPublishPubRel("client-1", 42);
		assertEquals(InflightStore.PHASE_PUBREL, inflight.phase);
	}

	@Test
	void takeoverRestoresSubscriptionsAsLocalRoutes() {
		MqttClusterManager clusterManager = new MqttClusterManager(new MqttClusterConfig().enabled(true), "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);
		SessionStore.Session session = new SessionStore.Session("client-1", Arrays.asList(
			new Subscribe("devices/+/state", "client-1", 1),
			new Subscribe("$share/workers/jobs/#", "client-1", 1)
		), false, 3600L, "node-2");

		sessions.restoreLocalSession(session);

		assertEquals(2, sessions.searchAllSubscribe("jobs/a").size()
			+ sessions.searchAllSubscribe("devices/a/state").size());
		assertEquals("client-1", sessions.searchSubscribe("devices/a/state").get(0).getClientId());
		assertTrue(sessions.searchSubscribe("jobs/a").isEmpty());
	}

	@Test
	void startupRestoresPersistentSessionAndDropsCleanSession() {
		H2MvStoreImpl engine = new H2MvStoreImpl();
		engine.open(tempDir);
		try {
			H2SessionStore store = new H2SessionStore(engine);
			Subscribe persistentSub = new Subscribe(
				"devices/+", "persistent", 1, true, true, 2, 77);
			store.save("persistent", new SessionStore.Session(
				"persistent", Collections.singletonList(persistentSub), false, 3600L, "node-1"));
			store.save("clean", new SessionStore.Session(
				"clean", Collections.emptyList(), true, 0L, "node-1"));

			MqttClusterManager clusterManager = new MqttClusterManager(
				new MqttClusterConfig().enabled(true), "node-1");
			ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
				new InMemoryMqttSessionManager(), clusterManager);
			sessions.setSessionStore(store);

			Subscribe restored = sessions.searchSubscribe("devices/a").get(0);
			assertEquals("persistent", restored.getClientId());
			assertTrue(restored.isNoLocal());
			assertTrue(restored.isRetainAsPublished());
			assertEquals(2, restored.getRetainHandling());
			assertEquals(77, restored.getSubscriptionId());
			assertEquals(3600, sessions.getSessionExpiryInterval("persistent"));
			assertTrue(store.load("clean") == null);

			sessions.applySessionMigration("persistent", "node-2");
			assertEquals("node-2", sessions.getStateClientNodeMap().get("persistent"));
			assertTrue(store.load("persistent") == null);
		} finally {
			engine.close();
		}
	}

	@Test
	void restoresInflightWithOriginalPacketIdAndDupFlag() {
		MqttClusterManager clusterManager = new MqttClusterManager(new MqttClusterConfig().enabled(true), "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);
		RecordingInflightStore inflight = new RecordingInflightStore();
		sessions.setInflightStore(inflight, 30_000L);
		long now = 100_000L;

		List<MqttMessage> restored = sessions.restoreInflight(Arrays.asList(
			new InflightStore.InflightEntry("client-1", 42, now + 10_000L, "jobs/a", new byte[]{1}, 1),
			new InflightStore.InflightEntry("client-1", 44, now + 10_000L, "jobs/qos2", new byte[]{4}, 2,
				InflightStore.PHASE_PUBREL),
			new InflightStore.InflightEntry("client-1", 43, now - 1L, "jobs/expired", new byte[]{2}, 1),
			new InflightStore.InflightEntry("client-1", 0, now + 10_000L, "jobs/invalid", new byte[]{3}, 1)
		), now);

		assertEquals(2, restored.size());
		MqttMessage message = restored.get(0);
		assertEquals(42, ((MqttPublishVariableHeader) message.variableHeader()).packetId());
		assertTrue(message.fixedHeader().isDup());
		assertEquals(MqttQoS.QOS1, message.fixedHeader().qosLevel());
		assertTrue(sessions.getPendingPublish("client-1", 42) != null);
		MqttMessage pubRel = restored.get(1);
		assertEquals(MqttMessageType.PUBREL, pubRel.fixedHeader().messageType());
		assertEquals(44, ((MqttMessageIdVariableHeader) pubRel.variableHeader()).messageId());
		assertTrue(pubRel.fixedHeader().isDup());
		assertTrue(sessions.getPendingPublish("client-1", 44) != null);
		assertTrue(inflight.removed);
	}

	@Test
	void remoteSharedStateUsesGroupAndTopicAndPromotesAfterNodeLeave() {
		MqttClusterManager clusterManager = new MqttClusterManager(new MqttClusterConfig().enabled(true), "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);
		MemorySharedSubStore store = new MemorySharedSubStore();
		sessions.setSharedSubStore(store);
		sessions.syncRemoteSubscriptions("client-a", "node-2", Arrays.asList(
			new Subscribe("$share/workers/jobs/a/#", "client-a", 1),
			new Subscribe("$share/workers/jobs/b/#", "client-a", 1)
		));
		sessions.syncRemoteSubscriptions("client-b", "node-3", Arrays.asList(
			new Subscribe("$share/workers/jobs/a/#", "client-b", 1)
		));

		assertEquals(2, store.get("workers", "jobs/a/#").getMembers().size());
		assertEquals(1, store.get("workers", "jobs/b/#").getMembers().size());
		assertTrue(store.get("workers", "jobs/a/#").getBackupNodeId() != null);

		sessions.clearNodeClientsAndSubscriptions("node-2");

		SharedSubStore.SharedSubGroup surviving = store.get("workers", "jobs/a/#");
		assertEquals(Collections.singletonList("client-b"), surviving.getMembers());
		assertEquals("node-3", surviving.getOwnerNodeId());
		assertTrue(surviving.getBackupNodeId() == null);
		assertTrue(store.get("workers", "jobs/b/#") == null);
	}

	@Test
	void sessionMigrationPreservesRoutesAndChangesOnlyOwnership() {
		MqttClusterManager clusterManager = new MqttClusterManager(new MqttClusterConfig().enabled(true), "node-1");
		ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
			new InMemoryMqttSessionManager(), clusterManager
		);
		sessions.addSubscribe(new TopicFilter("devices/+/state"), "client-1", 1, false, false, 0);

		sessions.applySessionMigration("client-1", "node-2");

		assertEquals(1, sessions.searchAllSubscribe("devices/a/state").size());
		assertTrue(sessions.searchSubscribe("devices/a/state").isEmpty());
		assertEquals("node-2", sessions.getClientNode("client-1"));

		sessions.applySessionMigration("client-1", "node-1");

		assertEquals(1, sessions.searchSubscribe("devices/a/state").size());
		assertTrue(sessions.getClientNode("client-1") == null);
	}

	@Test
	void concurrentSharedSubscriptionsConvergeAndSurviveTwoNodeDepartures() throws Exception {
		H2MvStoreImpl engine = new H2MvStoreImpl();
		engine.open(tempDir);
		try {
			MqttClusterManager clusterManager = new MqttClusterManager(
				new MqttClusterConfig().enabled(true), "node-1");
			ClusterMqttSessionManager sessions = new ClusterMqttSessionManager(
				new InMemoryMqttSessionManager(), clusterManager);
			H2SharedSubStore store = new H2SharedSubStore(engine);
			sessions.setSharedSubStore(store);
			int clients = 40;
			ExecutorService executor = Executors.newFixedThreadPool(8);
			CountDownLatch ready = new CountDownLatch(8);
			CountDownLatch start = new CountDownLatch(1);
			for (int i = 0; i < clients; i++) {
				final int index = i;
				executor.execute(() -> {
					ready.countDown();
					try {
						start.await();
						sessions.syncRemoteSubscriptions("client-" + index, "node-" + (2 + index % 4),
							Collections.singletonList(new Subscribe("$share/workers/jobs/#", "client-" + index, 1)));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			executor.shutdown();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

			SharedSubStore.SharedSubGroup initial = store.get("workers", "jobs/#");
			assertEquals(clients, initial.getMembers().size());
			assertTrue(initial.getOwnerNodeId() != null);
			assertTrue(initial.getBackupNodeId() != null);

			ExecutorService departures = Executors.newFixedThreadPool(2);
			departures.execute(() -> sessions.clearNodeClientsAndSubscriptions("node-2"));
			departures.execute(() -> sessions.clearNodeClientsAndSubscriptions("node-3"));
			departures.shutdown();
			assertTrue(departures.awaitTermination(10, TimeUnit.SECONDS));

			SharedSubStore.SharedSubGroup surviving = store.get("workers", "jobs/#");
			assertEquals(20, surviving.getMembers().size());
			assertTrue(surviving.getMembers().stream().noneMatch(client -> {
				int index = Integer.parseInt(client.substring("client-".length()));
				return index % 4 == 0 || index % 4 == 1;
			}));
			assertTrue(surviving.getOwnerNodeId().equals("node-4")
				|| surviving.getOwnerNodeId().equals("node-5"));
			assertTrue(surviving.getBackupNodeId() != null);
		} finally {
			engine.close();
		}
	}

	@Test
	void concurrentTakeoverGrantAllowsOneRequesterAndIsIdempotent() throws Exception {
		MqttClusterManager manager = new MqttClusterManager(
			new MqttClusterConfig().enabled(true), "node-1");
		ExecutorService executor = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger granted = new AtomicInteger();
		AtomicReference<String> winner = new AtomicReference<>();
		AtomicInteger winnerAttempt = new AtomicInteger();
		for (int i = 0; i < 16; i++) {
			final int index = i;
			executor.execute(() -> {
				try {
					start.await();
					if (manager.tryGrantTakeover("client-1", "node-" + index, index + 1L, 10_000L)) {
						granted.incrementAndGet();
						winner.set("node-" + index);
						winnerAttempt.set(index + 1);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}
		start.countDown();
		executor.shutdown();
		assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		assertEquals(1, granted.get());
		assertTrue(manager.tryGrantTakeover("client-1", winner.get(), winnerAttempt.get(), 10_000L));
		assertTrue(!manager.tryGrantTakeover("client-1", "loser", 99L, 10_000L));
	}

	private static class RecordingInflightStore implements InflightStore {
		private String clientId;
		private int packetId;
		private int qos;
		private String topic;
		private boolean removed;
		private int phase = InflightStore.PHASE_PUBLISH;
		private long expireAt;

		@Override
		public void put(String clientId, int packetId, long expireAt, String topic, byte[] payload, int qos) {
			this.clientId = clientId;
			this.packetId = packetId;
			this.topic = topic;
			this.qos = qos;
			this.expireAt = expireAt;
		}

		@Override
		public void remove(String clientId, int packetId) {
			this.removed = true;
		}

		@Override
		public void updatePhase(String clientId, int packetId, int phase) {
			this.phase = phase;
		}

		@Override
		public List<InflightEntry> listByClient(String clientId) {
			return Collections.emptyList();
		}

		@Override
		public int removeExpired(long nowMs) {
			return 0;
		}

		@Override
		public long count() {
			return 0;
		}
	}

	private static class MemorySharedSubStore implements SharedSubStore {
		private final Map<String, SharedSubGroup> groups = new HashMap<>();

		@Override
		public void save(SharedSubGroup group) {
			groups.put(key(group.getGroupName(), group.getTopicFilter()), group);
		}

		@Override
		public boolean updateIfVersion(SharedSubGroup group, long expectedVersion) {
			SharedSubGroup current = get(group.getGroupName(), group.getTopicFilter());
			if (current != null && current.getVersion() != expectedVersion) {
				return false;
			}
			save(group);
			return true;
		}

		@Override
		public void delete(String groupName) {
			groups.entrySet().removeIf(entry -> groupName.equals(entry.getValue().getGroupName()));
		}

		@Override
		public void delete(String groupName, String topicFilter) {
			groups.remove(key(groupName, topicFilter));
		}

		@Override
		public SharedSubGroup get(String groupName) {
			for (SharedSubGroup group : groups.values()) {
				if (groupName.equals(group.getGroupName())) {
					return group;
				}
			}
			return null;
		}

		@Override
		public SharedSubGroup get(String groupName, String topicFilter) {
			return groups.get(key(groupName, topicFilter));
		}

		@Override
		public List<SharedSubGroup> listAll() {
			return new ArrayList<>(groups.values());
		}

		private static String key(String groupName, String topicFilter) {
			return groupName + '\u0000' + topicFilter;
		}
	}
}
