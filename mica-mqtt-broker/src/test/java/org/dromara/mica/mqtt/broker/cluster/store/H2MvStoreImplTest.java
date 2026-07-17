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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link H2MvStoreImpl}.
 * <p>
 * Tests cover basic CRUD, prefix scan, transactions, and crash-recovery.
 * Each test uses a fresh temporary directory provided by JUnit 5's {@code @TempDir}.
 * </p>
 *
 * @author L.cm
 */
class H2MvStoreImplTest {

	@TempDir
	Path tempDir;

	private H2MvStoreImpl store;

	@BeforeEach
	void setUp() {
		store = new H2MvStoreImpl();
		store.open(tempDir);
	}

	@AfterEach
	void tearDown() {
		if (store != null) {
			store.close();
		}
	}

	@Test
	void testPutAndGet() {
		store.put("key1", "value1".getBytes());
		byte[] val = store.get("key1");
		assertNotNull(val);
		assertEquals("value1", new String(val));
	}

	@Test
	void testGetMissingKey() {
		assertNull(store.get("nonexistent"));
	}

	@Test
	void testDelete() {
		store.put("k", "v".getBytes());
		assertNotNull(store.get("k"));
		store.delete("k");
		assertNull(store.get("k"));
	}

	@Test
	void testScanByPrefix() {
		store.put("session:clientA", "a".getBytes());
		store.put("session:clientB", "b".getBytes());
		store.put("retain:topic/temp", "t".getBytes());

		List<LocalKvStore.KeyValue> results = store.scan("session:");
		assertEquals(2, results.size());
		assertTrue(results.stream().allMatch(kv -> kv.getKey().startsWith("session:")));
	}

	@Test
	void testScanEmptyPrefixReturnsAll() {
		store.put("a", "1".getBytes());
		store.put("b", "2".getBytes());
		store.put("c", "3".getBytes());

		List<LocalKvStore.KeyValue> results = store.scan("");
		assertEquals(3, results.size());
	}

	@Test
	void testScanSortedOrder() {
		store.put("z:3", "c".getBytes());
		store.put("z:1", "a".getBytes());
		store.put("z:2", "b".getBytes());

		List<LocalKvStore.KeyValue> results = store.scan("z:");
		assertEquals(3, results.size());
		assertEquals("z:1", results.get(0).getKey());
		assertEquals("z:2", results.get(1).getKey());
		assertEquals("z:3", results.get(2).getKey());
	}

	@Test
	void testScanNoMatch() {
		store.put("a:1", "x".getBytes());
		List<LocalKvStore.KeyValue> results = store.scan("b:");
		assertTrue(results.isEmpty());
	}

	@Test
	void testTransaction() {
		store.executeInTransaction(() -> {
			store.put("tx:1", "one".getBytes());
			store.put("tx:2", "two".getBytes());
		});
		assertEquals("one", new String(store.get("tx:1")));
		assertEquals("two", new String(store.get("tx:2")));
	}

	@Test
	void testStats() {
		store.put("k", "v".getBytes());
		store.get("k");
		store.scan("k");
		store.openMap("mqtt_test_stats").put("named", "value".getBytes());
		LocalKvStore.StoreStats stats = store.stats();
		assertTrue(stats.isHealthy());
		assertEquals(2L, stats.getEntryCount());
		assertEquals(2L, stats.getReadOperations());
		assertEquals(1L, stats.getWriteOperations());
	}

	@Test
	void testCrashRecovery() {
		// Write data and close (close calls commit, which flushes WAL to disk)
		store.put("recover:key", "survived".getBytes());
		store.close();
		store = null;

		// Re-open the same directory
		H2MvStoreImpl reopened = new H2MvStoreImpl();
		reopened.open(tempDir);
		try {
			byte[] val = reopened.get("recover:key");
			assertNotNull(val, "Entry should survive a close-then-reopen");
			assertEquals("survived", new String(val));
		} finally {
			reopened.close();
		}
	}
}
