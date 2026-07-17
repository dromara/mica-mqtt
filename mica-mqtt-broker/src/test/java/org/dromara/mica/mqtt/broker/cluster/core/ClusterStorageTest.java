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

import org.dromara.mica.mqtt.broker.cluster.config.MqttStorageConfig;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.store.SharedSubStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClusterStorage} — verifies that the coordinator
 * correctly aggregates the V3 persistence components and supports the
 * INV-6 degraded-startup guarantee.
 *
 * @author L.cm
 */
class ClusterStorageTest {

	@TempDir
	Path tempDir;

	@Test
	void disabledConfigReturnsInactive() {
		MqttStorageConfig config = new MqttStorageConfig().enabled(false);
		ClusterStorage storage = new ClusterStorage(config);
		assertFalse(storage.start(), "disabled storage should report inactive start");
		assertFalse(storage.isActive());
		assertNull(storage.getEngine());
		assertNull(storage.getInflightStore());
		assertNull(storage.getSessionStore());
		assertNull(storage.getSharedSubStore());
		assertNull(storage.getRetainIndex());
		// stop on disabled storage must be a no-op and not throw
		storage.stop();
	}

	@Test
	void nullConfigBehavesLikeDisabled() {
		ClusterStorage storage = new ClusterStorage(null);
		assertFalse(storage.start());
		assertFalse(storage.isActive());
		storage.stop();
	}

	@Test
	void enabledConfigActivatesAllSubComponents() {
		MqttStorageConfig config = new MqttStorageConfig()
			.enabled(true)
			.dataDir(tempDir.toString())
			.inflightTtlMs(10_000L)
			.inflightCleanPeriodMs(1_000L);
		ClusterStorage storage = new ClusterStorage(config);
		assertTrue(storage.start());
		try {
			assertTrue(storage.isActive());
			assertNotNull(storage.getEngine());
			assertNotNull(storage.getInflightStore());
			assertNotNull(storage.getSessionStore());
			assertNotNull(storage.getSharedSubStore());
			assertNotNull(storage.getRetainIndex());
			assertNotNull(storage.getInflightCleaner());
		} finally {
			storage.stop();
		}
	}

	@Test
	void featureFlagsDisableOptionalStores() {
		MqttStorageConfig config = new MqttStorageConfig()
			.enabled(true)
			.dataDir(tempDir.toString())
			.persistSession(false)
			.persistSharedSub(false)
			.persistRetain(false);
		ClusterStorage storage = new ClusterStorage(config);
		assertTrue(storage.start());
		try {
			assertTrue(storage.isActive());
			assertNotNull(storage.getInflightStore());
			assertNull(storage.getSessionStore());
			assertNull(storage.getSharedSubStore());
			assertNull(storage.getRetainIndex());
		} finally {
			storage.stop();
		}
	}

	@Test
	void invalidDataDirFallsBackToInactive() {
		MqttStorageConfig config = new MqttStorageConfig()
			.enabled(true)
			.dataDir("\0\0invalid\0null\0path\0");
		ClusterStorage storage = new ClusterStorage(config);
		// Either succeeds (filesystem tolerates the path) or fails back to
		// inactive; what we require is that no exception escapes.
		try {
			storage.start();
		} catch (Exception e) {
			fail("start() must not propagate I/O errors: " + e);
		} finally {
			storage.stop();
		}
	}

	@Test
	void roundTripAcrossSubComponents() {
		MqttStorageConfig config = new MqttStorageConfig()
			.enabled(true)
			.dataDir(tempDir.toString());
		ClusterStorage storage = new ClusterStorage(config);
		assertTrue(storage.start());
		try {
			// SharedSubStore
			SharedSubStore.SharedSubGroup group = new SharedSubStore.SharedSubGroup(
				"g1", "t", Collections.singletonList("c1"), "node-1", "node-2", 1L,
				System.currentTimeMillis());
			storage.getSharedSubStore().save(group);
			assertNotNull(storage.getSharedSubStore().get("g1"));

			// SessionStore
			storage.getSessionStore().save("client-1", new org.dromara.mica.mqtt.broker.cluster.store.SessionStore.Session(
				"client-1", Collections.emptyList(), false, 3600L, "node-1"));
			assertNotNull(storage.getSessionStore().load("client-1"));

			// RetainIndex
			org.dromara.mica.mqtt.core.server.model.Message msg = new org.dromara.mica.mqtt.core.server.model.Message();
			msg.setTopic("a");
			msg.setPayload("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			msg.setQos(0);
			msg.setRetain(true);
			assertTrue(storage.getRetainIndex().put("a", msg));
			assertEquals(1, storage.getRetainIndex().match("a").size());
		} finally {
			storage.stop();
		}
	}

	@Test
	void managerPrometheusExportIncludesStorageGauges() {
		MqttStorageConfig storageConfig = new MqttStorageConfig()
			.enabled(true)
			.dataDir(tempDir.toString());
		ClusterStorage storage = new ClusterStorage(storageConfig);
		assertTrue(storage.start());
		try {
			storage.getInflightStore().put("client-1", 7, System.currentTimeMillis() + 10_000L,
				"topic/a", new byte[]{1}, 1);
			long deadline = System.currentTimeMillis() + 2_000L;
			while (storage.getInflightStore().count() != 1L && System.currentTimeMillis() < deadline) {
				Thread.yield();
			}
			MqttClusterManager manager = new MqttClusterManager(
				new MqttClusterConfig().enabled(false), "node-1");
			manager.setClusterStorage(storage);
			String metrics = manager.toPrometheus();
			assertTrue(metrics.contains("mqtt_cluster_storage_healthy 1"));
			assertTrue(metrics.contains("mqtt_cluster_storage_file_size_bytes"));
			assertTrue(metrics.contains("mqtt_cluster_storage_write_operations_total"));
			assertTrue(metrics.contains("mqtt_cluster_storage_startup_duration_millis"));
			assertTrue(metrics.contains("mqtt_cluster_inflight_entries 1"));
			assertTrue(metrics.contains("mqtt_cluster_inflight_expired_total 0"));
		} finally {
			storage.stop();
		}
	}
}
