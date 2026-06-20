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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link H2SharedSubStore}.
 *
 * @author L.cm
 */
class H2SharedSubStoreTest {

	@TempDir
	Path tempDir;

	private H2MvStoreImpl engine;
	private H2SharedSubStore store;

	@BeforeEach
	void setUp() {
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		store = new H2SharedSubStore(engine);
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		engine.close();
	}

	@Test
	void saveAndGet() {
		SharedSubStore.SharedSubGroup group = new SharedSubStore.SharedSubGroup(
			"group-a", "sensors/+/temp",
			Arrays.asList("client-1", "client-2"), "node-1", "node-2", 1L, System.currentTimeMillis());
		store.save(group);
		SharedSubStore.SharedSubGroup loaded = store.get("group-a");
		assertNotNull(loaded);
		assertEquals("group-a", loaded.getGroupName());
		assertEquals("sensors/+/temp", loaded.getTopicFilter());
		assertEquals(Arrays.asList("client-1", "client-2"), loaded.getMembers());
		assertEquals("node-1", loaded.getOwnerNodeId());
		assertEquals("node-2", loaded.getBackupNodeId());
		assertEquals(1L, loaded.getVersion());
	}

	@Test
	void delete() {
		SharedSubStore.SharedSubGroup group = new SharedSubStore.SharedSubGroup(
			"g", "t", Arrays.asList("c"), "node-1", null, 1L, System.currentTimeMillis());
		store.save(group);
		assertNotNull(store.get("g"));
		store.delete("g");
		assertNull(store.get("g"));
	}

	@Test
	void listAllReturnsAllPersistedGroups() {
		store.save(group("alpha", Arrays.asList("c1", "c2"), 1L));
		store.save(group("beta", Arrays.asList("c3"), 2L));
		store.save(group("gamma", Arrays.asList("c4"), 3L));

		List<SharedSubStore.SharedSubGroup> all = store.listAll();
		assertEquals(3, all.size());
	}

	@Test
	void updateIfVersionSucceedsWhenVersionMatches() {
		store.save(group("g", Arrays.asList("c1"), 1L));
		SharedSubStore.SharedSubGroup updated = group("g", Arrays.asList("c1", "c2"), 2L);
		boolean result = store.updateIfVersion(updated, 1L);
		assertTrue(result);
		assertEquals(2, store.get("g").getVersion());
		assertEquals(2, store.get("g").getMembers().size());
	}

	@Test
	void updateIfVersionFailsOnStaleVersion() {
		store.save(group("g", Arrays.asList("c1"), 1L));
		SharedSubStore.SharedSubGroup stale = group("g", Arrays.asList("c1", "c2"), 2L);
		boolean result = store.updateIfVersion(stale, 999L);
		assertFalse(result);
		// original record preserved
		assertEquals(1, store.get("g").getMembers().size());
	}

	@Test
	void survivesRestart() throws InterruptedException {
		store.save(group("p", Arrays.asList("c"), 1L));
		engine.close();
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		store = new H2SharedSubStore(engine);
		assertNotNull(store.get("p"));
	}

	private static SharedSubStore.SharedSubGroup group(String name, List<String> members, long version) {
		return new SharedSubStore.SharedSubGroup(
			name, "topic/" + name, members, "node-1", "node-2", version, System.currentTimeMillis());
	}
}