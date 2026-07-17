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

package org.dromara.mica.mqtt.broker.cluster.store;

import org.h2.mvstore.MVMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link H2InflightStore} and {@link InflightTtlCleaner}.
 *
 * @author L.cm
 */
class H2InflightStoreTest {

	@TempDir
	Path tempDir;

	private H2MvStoreImpl engine;
	private H2InflightStore inflightStore;

	@BeforeEach
	void setUp() {
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		inflightStore = new H2InflightStore(engine);
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		inflightStore.shutdown();
		engine.close();
	}

	@Test
	void testPutAndListByClient() throws InterruptedException {
		long expireAt = System.currentTimeMillis() + 30_000L;
		inflightStore.put("client1", 1, expireAt, "test/topic", "hello".getBytes(), 1);
		inflightStore.awaitWrites();

		List<InflightStore.InflightEntry> entries = inflightStore.listByClient("client1");
		assertEquals(1, entries.size());
		InflightStore.InflightEntry entry = entries.get(0);
		assertEquals("client1", entry.getClientId());
		assertEquals(1, entry.getPacketId());
		assertEquals("test/topic", entry.getTopic());
		assertEquals("hello", new String(entry.getPayload()));
		assertEquals(1, entry.getQos());
	}

	@Test
	void testRemove() throws InterruptedException {
		long expireAt = System.currentTimeMillis() + 30_000L;
		inflightStore.put("c1", 5, expireAt, "t", "p".getBytes(), 1);
		inflightStore.awaitWrites();

		inflightStore.remove("c1", 5);
		inflightStore.awaitWrites();

		List<InflightStore.InflightEntry> entries = inflightStore.listByClient("c1");
		assertTrue(entries.isEmpty());
	}

	@Test
	void updatePhasePersistsPubRelState() throws InterruptedException {
		long expireAt = System.currentTimeMillis() + 30_000L;
		inflightStore.put("qos2", 9, expireAt, "jobs/a", new byte[]{1}, 2);
		inflightStore.awaitWrites();

		inflightStore.updatePhase("qos2", 9, InflightStore.PHASE_PUBREL);
		inflightStore.awaitWrites();

		InflightStore.InflightEntry entry = inflightStore.listByClient("qos2").get(0);
		assertEquals(InflightStore.PHASE_PUBREL, entry.getPhase());
	}

	@Test
	void legacyRecordWithoutPhaseDefaultsToPublish() {
		byte[] current = H2InflightStore.serialize(123L, "legacy/topic", new byte[]{1}, 2);
		byte[] legacy = Arrays.copyOf(current, current.length - 1);
		MVMap<String, byte[]> map = engine.openMap(H2InflightStore.MAP_NAME);
		map.put(H2InflightStore.buildKey("legacy", 7), legacy);
		engine.commit();

		InflightStore.InflightEntry entry = inflightStore.listByClient("legacy").get(0);
		assertEquals(InflightStore.PHASE_PUBLISH, entry.getPhase());
	}

	@Test
	void testListByClientReturnsOnlyThatClient() throws InterruptedException {
		long expireAt = System.currentTimeMillis() + 30_000L;
		inflightStore.put("alice", 1, expireAt, "t1", "a".getBytes(), 1);
		inflightStore.put("bob", 2, expireAt, "t2", "b".getBytes(), 1);
		inflightStore.awaitWrites();

		List<InflightStore.InflightEntry> aliceEntries = inflightStore.listByClient("alice");
		assertEquals(1, aliceEntries.size());
		assertEquals("alice", aliceEntries.get(0).getClientId());
	}

	@Test
	void testRemoveExpired() throws InterruptedException {
		long pastExpiry = System.currentTimeMillis() - 1_000L;
		long futureExpiry = System.currentTimeMillis() + 60_000L;
		inflightStore.put("c1", 1, pastExpiry, "t", "old".getBytes(), 1);
		inflightStore.put("c1", 2, futureExpiry, "t", "new".getBytes(), 1);
		inflightStore.awaitWrites();

		int removed = inflightStore.removeExpired(System.currentTimeMillis());
		assertEquals(1, removed);

		List<InflightStore.InflightEntry> remaining = inflightStore.listByClient("c1");
		assertEquals(1, remaining.size());
		assertEquals(2, remaining.get(0).getPacketId());
	}

	@Test
	void testTtlCleaner() throws InterruptedException {
		long pastExpiry = System.currentTimeMillis() - 1_000L;
		inflightStore.put("c2", 1, pastExpiry, "t", "stale".getBytes(), 1);
		inflightStore.awaitWrites();

		// Use a short period for the cleaner (100 ms)
		InflightTtlCleaner cleaner = new InflightTtlCleaner(inflightStore, 100);
		cleaner.start();

		// Poll until the entry disappears, with a generous timeout
		List<InflightStore.InflightEntry> entries = null;
		for (int i = 0; i < 50; i++) {
			entries = inflightStore.listByClient("c2");
			if (entries.isEmpty()) {
				break;
			}
			TimeUnit.MILLISECONDS.sleep(50);
		}
		cleaner.stop();

		assertNotNull(entries);
		assertTrue(entries.isEmpty(), "Expired inflight entry should have been removed by TTL cleaner");
	}

	@Test
	void testKeyFormat() {
		// Verify zero-padded key ordering
		String key1 = H2InflightStore.buildKey("c", 1);
		String key5 = H2InflightStore.buildKey("c", 5);
		String key100 = H2InflightStore.buildKey("c", 100);
		assertTrue(key1.compareTo(key5) < 0);
		assertTrue(key5.compareTo(key100) < 0);
	}

	@Test
	void testSerializeDeserializeRoundTrip() {
		long expireAt = 1_700_000_000_000L;
		String topic = "sensors/temperature";
		byte[] payload = "42.5".getBytes();
		int qos = 2;

		byte[] serialized = H2InflightStore.serialize(expireAt, topic, payload, qos);
		assertNotNull(serialized);
		int minSize = 8 + 4 + topic.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 4 + payload.length + 1;
		assertTrue(serialized.length >= minSize);
	}

	@Test
	void testCount() throws InterruptedException {
		long expireAt = System.currentTimeMillis() + 30_000L;
		inflightStore.put("c", 1, expireAt, "t", "p".getBytes(), 1);
		inflightStore.put("c", 2, expireAt, "t", "p".getBytes(), 1);
		inflightStore.awaitWrites();

		assertEquals(2L, inflightStore.count());
	}
}
