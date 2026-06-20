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

import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link H2SessionStore}, especially the takeover round-trip:
 * the bytes returned by {@code loadRaw} must be installable on another node
 * via {@code restoreRaw} without any further serialization.
 *
 * @author L.cm
 */
class H2SessionStoreTest {

	@TempDir
	Path tempDir;

	private H2MvStoreImpl engine;
	private H2SessionStore store;

	@BeforeEach
	void setUp() {
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		store = new H2SessionStore(engine);
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		engine.close();
	}

	@Test
	void saveAndLoad() {
		Subscribe sub = new Subscribe("sensors/+/temp", "client-a", 1);
		SessionStore.Session session = new SessionStore.Session(
			"client-a", Collections.singletonList(sub), false, 3600L, "node-1");
		store.save("client-a", session);
		SessionStore.Session loaded = store.load("client-a");
		assertNotNull(loaded);
		assertEquals("client-a", loaded.getClientId());
		assertFalse(loaded.isCleanSession());
		assertEquals(3600L, loaded.getSessionExpirySeconds());
		assertEquals("node-1", loaded.getOwnerNodeId());
		assertEquals(1, loaded.getSubscriptions().size());
		assertEquals("sensors/+/temp", loaded.getSubscriptions().get(0).getTopicFilter());
	}

	@Test
	void takeoverRawBytesRoundTrip() {
		Subscribe sub1 = new Subscribe("a/b", "client-b", 1);
		Subscribe sub2 = new Subscribe("a/c", "client-b", 2);
		SessionStore.Session original = new SessionStore.Session(
			"client-b", Arrays.asList(sub1, sub2), false, 7200L, "node-1");
		store.save("client-b", original);

		byte[] raw = store.loadRaw("client-b");
		assertNotNull(raw);
		assertTrue(raw.length > 0);

		// Simulate a different node receiving the takeover payload and restoring
		// it into its own store.
		Path otherDir = tempDir.resolve("other-node");
		H2MvStoreImpl otherEngine = new H2MvStoreImpl();
		otherEngine.open(otherDir);
		try {
			H2SessionStore otherStore = new H2SessionStore(otherEngine);
			SessionStore.Session restored = otherStore.restoreRaw("client-b", raw);
			assertNotNull(restored);
			assertEquals("client-b", restored.getClientId());
			assertEquals(7200L, restored.getSessionExpirySeconds());
			assertEquals(2, restored.getSubscriptions().size());

			// The restored session must also be loadable through the new node.
			assertNotNull(otherStore.load("client-b"));
		} finally {
			otherEngine.close();
		}
	}

	@Test
	void delete() {
		store.save("client-c", new SessionStore.Session(
			"client-c", Collections.emptyList(), true, 0L, "node-1"));
		assertNotNull(store.load("client-c"));
		store.delete("client-c");
		assertNull(store.load("client-c"));
	}

	@Test
	void survivesRestart() throws InterruptedException {
		store.save("client-d", new SessionStore.Session(
			"client-d", Collections.emptyList(), false, 600L, "node-2"));
		engine.close();
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		store = new H2SessionStore(engine);
		assertNotNull(store.load("client-d"));
	}

	@Test
	void emptySubscriptionsHandledGracefully() {
		SessionStore.Session empty = new SessionStore.Session(
			"client-e", null, true, 0L, "node-1");
		store.save("client-e", empty);
		SessionStore.Session loaded = store.load("client-e");
		assertNotNull(loaded);
		// Either null or empty is acceptable; both indicate no subscriptions.
		List<Subscribe> subs = loaded.getSubscriptions();
		assertTrue(subs == null || subs.isEmpty());
	}
}