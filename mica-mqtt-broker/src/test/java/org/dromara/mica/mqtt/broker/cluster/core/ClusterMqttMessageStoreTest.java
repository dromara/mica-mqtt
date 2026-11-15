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
import org.dromara.mica.mqtt.broker.cluster.message.WillMessageNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.store.H2MvStoreImpl;
import org.dromara.mica.mqtt.broker.cluster.store.RetainIndex;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.store.InMemoryMqttMessageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class ClusterMqttMessageStoreTest {

	private static ClusterMqttMessageStore store() {
		return store(new MqttClusterManager(new MqttClusterConfig().enabled(false), "local"));
	}

	private static ClusterMqttMessageStore store(MqttClusterManager manager) {
		return new ClusterMqttMessageStore(new InMemoryMqttMessageStore(), manager);
	}

	private static final class CountingManager extends MqttClusterManager {
		private final AtomicInteger willBroadcasts = new AtomicInteger();

		private CountingManager() {
			super(new MqttClusterConfig().enabled(true), "local");
		}

		@Override
		public void broadcast(ClusterMessage clusterMsg) {
			if (clusterMsg instanceof WillMessageNotifyMessage) {
				willBroadcasts.incrementAndGet();
			}
		}
	}

	private static Message message(String topic) {
		return message(topic, null);
	}

	private static Message message(String topic, String payload) {
		Message message = new Message();
		message.setTopic(topic);
		if (payload != null) {
			message.setPayload(payload.getBytes(StandardCharsets.UTF_8));
		}
		return message;
	}

	/**
	 * 重启后内存只有部分 retained 时，也必须返回持久化索引中的其他 topic；
	 * 同一 topic 以内存（更新、TTL 更准确）为准。
	 */
	@Test
	void retainResultsMergeMemoryAndPersistentIndex(@TempDir Path tempDir) throws Exception {
		H2MvStoreImpl engine = new H2MvStoreImpl();
		engine.open(tempDir);
		InMemoryMqttMessageStore delegate = new InMemoryMqttMessageStore();
		try {
			RetainIndex index = new RetainIndex(engine);
			assertTrue(index.put("a/1", message("a/1", "old-1"), 0));
			assertTrue(index.put("a/2", message("a/2", "old-2"), 0));

			delegate.addRetainMessage("a/1", 0, message("a/1", "new-1"));
			ClusterMqttMessageStore store = new ClusterMqttMessageStore(delegate,
				new MqttClusterManager(new MqttClusterConfig().enabled(false), "local"));
			store.setRetainIndex(index);

			List<Message> result = store.getRetainMessage("a/#");
			assertEquals(2, result.size());
			for (Message message : result) {
				String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
				if ("a/1".equals(message.getTopic())) {
					assertEquals("new-1", payload);
				} else {
					assertEquals("a/2", message.getTopic());
					assertEquals("old-2", payload);
				}
			}
		} finally {
			delegate.clean();
			engine.close();
		}
	}

	/**
	 * 新连接不带遗嘱时必须留下清理版本：即使本节点还没收到旧遗嘱，
	 * 迟到的旧 add 也不能复活。
	 */
	@Test
	void clearWithoutLocalWillCreatesBarrierAndRejectsLateAdd() {
		CountingManager manager = new CountingManager();
		ClusterMqttMessageStore store = store(manager);
		store.clearWillMessage("migrated-client");

		assertEquals(1, store.getWillVersionCount());
		assertEquals(1, manager.willBroadcasts.get());

		WillMessageNotifyMessage lateAdd = new WillMessageNotifyMessage();
		lateAdd.setClientId("migrated-client");
		lateAdd.setWillToken("old-token");
		lateAdd.setWillVersion(1);
		lateAdd.setWillMessage(message("old/topic"));
		store.applyWillMessageLocal(lateAdd);

		assertNull(store.getWillMessage("migrated-client"));
	}

	/**
	 * 每次无遗嘱连接都要推进清理版本，压制「旧墓碑之后、尚未到达」的更高版本 add。
	 */
	@Test
	void repeatedClearAdvancesBarrierAboveUnseenAdd() {
		CountingManager manager = new CountingManager();
		ClusterMqttMessageStore store = store(manager);
		store.clearWillMessage("client");
		long firstVersion = store.getWillVersion("client");
		store.clearWillMessage("client");
		long secondVersion = store.getWillVersion("client");

		assertTrue(secondVersion > firstVersion);
		assertEquals(2, manager.willBroadcasts.get());
		assertEquals(1, store.getWillVersionCount());

		// 模拟第一次 clear 之后另一节点产生的 add，直到第二次 clear 后才到达
		WillMessageNotifyMessage lateAdd = new WillMessageNotifyMessage();
		lateAdd.setClientId("client");
		lateAdd.setWillToken("late-token");
		lateAdd.setWillVersion(firstVersion + 1);
		lateAdd.setWillMessage(message("late/topic"));
		store.applyWillMessageLocal(lateAdd);

		assertNull(store.getWillMessage("client"));
	}

	/**
	 * 容量淘汰后要推进全局版本水位，被淘汰墓碑以下的 add 仍必须被拒绝。
	 */
	@Test
	void capacityEvictionAdvancesVersionFloor() {
		ClusterMqttMessageStore store = store();
		store.clearWillMessage("a");
		store.clearWillMessage("b");
		store.clearWillMessage("c");
		long evictedVersion = store.getWillVersion("a");
		long maxVersion = Math.max(store.getWillVersion("b"), store.getWillVersion("c"));

		// 压到 2 条，最旧的 a 被淘汰
		assertEquals(1, store.capWillVersions(2));
		assertEquals(2, store.getWillVersionCount());
		assertTrue(store.getWillVersionFloor() >= evictedVersion);

		// a 的迟到 add 版本与已淘汰墓碑相同，必须被水位拒绝
		WillMessageNotifyMessage lateAdd = new WillMessageNotifyMessage();
		lateAdd.setClientId("a");
		lateAdd.setWillToken("late-a");
		lateAdd.setWillVersion(evictedVersion);
		lateAdd.setWillMessage(message("late/a"));
		store.applyWillMessageLocal(lateAdd);
		assertNull(store.getWillMessage("a"));

		// 水位之上的新遗嘱仍可正常接收
		WillMessageNotifyMessage freshAdd = new WillMessageNotifyMessage();
		freshAdd.setClientId("fresh");
		freshAdd.setWillToken("fresh-token");
		freshAdd.setWillVersion(maxVersion + 1);
		freshAdd.setWillMessage(message("fresh/topic"));
		store.applyWillMessageLocal(freshAdd);
		assertSame(freshAdd.getWillMessage(), store.getWillMessage("fresh"));
	}

	@Test
	void anonymousClearsAreBoundedAndPrunable() {
		ClusterMqttMessageStore store = store();
		for (int i = 0; i < 10_000; i++) {
			store.clearWillMessage("anonymous-" + i);
		}
		assertEquals(10_000, store.getWillVersionCount());
		assertEquals(10_000, store.pruneWillVersions(Long.MAX_VALUE));
		assertEquals(0, store.getWillVersionCount());
	}

	@Test
	void clearExistingWillStillBroadcasts() {
		CountingManager manager = new CountingManager();
		ClusterMqttMessageStore store = store(manager);
		store.addWillMessage("client", message("will"));
		assertEquals(1, manager.willBroadcasts.get());
		store.clearWillMessage("client");
		assertEquals(2, manager.willBroadcasts.get());
		assertEquals(1, store.getWillVersionCount());
		// 每次 clear 都要推进版本，重复连接同样需要广播
		store.clearWillMessage("client");
		assertEquals(3, manager.willBroadcasts.get());
		assertEquals(1, store.getWillVersionCount());
	}

	@Test
	void clearExistingWillCreatesTombstoneThatRejectsStaleAdd() {
		ClusterMqttMessageStore store = store();
		Message oldWill = message("old");
		Message newWill = message("new");
		store.addWillMessage("client", oldWill);

		WillMessageNotifyMessage oldAdd = new WillMessageNotifyMessage();
		oldAdd.setClientId("client");
		oldAdd.setWillToken("old");
		oldAdd.setWillVersion(1);
		oldAdd.setWillMessage(oldWill);

		store.clearWillMessage("client");
		store.applyWillMessageLocal(oldAdd);

		assertNull(store.getWillMessage("client"));
		assertEquals(1, store.getWillVersionCount());

		WillMessageNotifyMessage newAdd = new WillMessageNotifyMessage();
		newAdd.setClientId("client");
		newAdd.setWillToken("new");
		newAdd.setWillVersion(Long.MAX_VALUE);
		newAdd.setWillMessage(newWill);
		store.applyWillMessageLocal(newAdd);
		assertSame(newWill, store.getWillMessage("client"));
	}

	/**
	 * 只回收 cleared 且在 TTL 之外的墓碑，活跃遗嘱必须保留。
	 */
	@Test
	void pruneOnlyRemovesExpiredClearedTombstones() {
		ClusterMqttMessageStore store = store();
		Message will = message("live");
		store.addWillMessage("active", will);
		store.addWillMessage("cleared", message("gone"));
		store.clearWillMessage("cleared");

		assertEquals(1, store.pruneWillVersions(Long.MAX_VALUE));
		assertEquals(1, store.getWillVersionCount());
		assertSame(will, store.getWillMessage("active"));
	}

	/**
	 * 大量一次性客户端产生的墓碑可以被一次性回收，避免长期运行内存无界增长。
	 */
	@Test
	void bulkTombstonesCanBePruned() {
		ClusterMqttMessageStore store = store();
		for (int i = 0; i < 1100; i++) {
			store.addWillMessage("client-" + i, message("t" + i));
			store.clearWillMessage("client-" + i);
		}
		assertEquals(1100, store.getWillVersionCount());
		assertEquals(1100, store.pruneWillVersions(Long.MAX_VALUE));
		assertEquals(0, store.getWillVersionCount());
	}

	/**
	 * 空 clientId 不应进入版本表（避免 null/空串污染）。
	 */
	@Test
	void blankClientIdIsIgnored() {
		ClusterMqttMessageStore store = store();
		store.clearWillMessage(null);
		store.clearWillMessage("");
		assertEquals(0, store.getWillVersionCount());
	}

	/**
	 * 墓碑被 TTL 回收后，比保留窗口更旧的 add 仍必须被拒绝，避免旧遗嘱复活。
	 */
	@Test
	void staleAddIsRejectedEvenWithoutTombstone() {
		ClusterMqttMessageStore store = store();
		store.setWillVersionTtlMs(1000);
		// 清空版本表，模拟墓碑已被 TTL 回收
		store.pruneWillVersions(Long.MAX_VALUE);

		WillMessageNotifyMessage staleAdd = new WillMessageNotifyMessage();
		staleAdd.setClientId("client");
		staleAdd.setWillToken("old-token");
		staleAdd.setWillVersion(1);
		staleAdd.setWillMessage(message("old/topic"));
		store.applyWillMessageLocal(staleAdd);

		assertNull(store.getWillMessage("client"));
	}

	/**
	 * 只接收通知的副本节点也要触发墓碑回收。
	 */
	@Test
	void tombstonesArePrunedOnReplicaNodes() {
		ClusterMqttMessageStore store = store();
		for (int i = 0; i < 1100; i++) {
			WillMessageNotifyMessage clear = new WillMessageNotifyMessage();
			clear.setClientId("client-" + i);
			clear.setWillToken("token-" + i);
			clear.setWillVersion(System.currentTimeMillis() + i);
			clear.setClear(true);
			store.applyWillMessageLocal(clear);
		}
		assertEquals(1100, store.getWillVersionCount());
		assertEquals(1100, store.pruneWillVersions(Long.MAX_VALUE));
		assertEquals(0, store.getWillVersionCount());
	}
}
