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
import org.dromara.mica.mqtt.broker.cluster.store.H2InflightStore;
import org.dromara.mica.mqtt.broker.cluster.store.H2MvStoreImpl;
import org.dromara.mica.mqtt.broker.cluster.store.H2SessionStore;
import org.dromara.mica.mqtt.broker.cluster.store.H2SharedSubStore;
import org.dromara.mica.mqtt.broker.cluster.store.InflightStore;
import org.dromara.mica.mqtt.broker.cluster.store.InflightTtlCleaner;
import org.dromara.mica.mqtt.broker.cluster.store.LocalKvStore;
import org.dromara.mica.mqtt.broker.cluster.store.RetainIndex;
import org.dromara.mica.mqtt.broker.cluster.store.SessionStore;
import org.dromara.mica.mqtt.broker.cluster.store.SharedSubStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Aggregates all V3 persistence components and owns their lifecycle.
 * <p>
 * Constructed by {@link org.dromara.mica.mqtt.broker.cluster.config.MqttClusterBrokerCreator}
 * when {@link MqttStorageConfig#isEnabled()} is {@code true}.  Holds the
 * single {@link H2MvStoreImpl} engine and exposes typed views (session,
 * shared-sub, retain, inflight).  When the storage layer is disabled, all
 * getters return {@code null} and downstream code falls back to V1/V2
 * in-memory behavior.
 * </p>
 * <p>
 * <strong>Degraded startup</strong> (INV-6): if {@link #start()} fails to
 * open the H2 file, the engine is left null and the broker continues in
 * pure-memory mode rather than refusing to start.
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class ClusterStorage {
	private static final Logger logger = LoggerFactory.getLogger(ClusterStorage.class);

	private final MqttStorageConfig config;
	private H2MvStoreImpl engine;
	private H2InflightStore inflightStore;
	private InflightTtlCleaner inflightCleaner;
	private H2SessionStore sessionStore;
	private H2SharedSubStore sharedSubStore;
	private RetainIndex retainIndex;

	private final boolean active;

	public ClusterStorage(MqttStorageConfig config) {
		this.config = config;
		this.active = config != null && config.isEnabled();
	}

	/**
	 * Opens the H2 store and constructs all sub-components.
	 * <p>
	 * Safe to call when storage is disabled — the method returns immediately
	 * without touching the filesystem.
	 * </p>
	 *
	 * @return {@code true} on success; {@code false} on startup failure (broker
	 *         should continue in pure-memory mode)
	 */
	public boolean start() {
		if (!active) {
			logger.info("[Storage] V3 persistence disabled, using V1/V2 in-memory mode");
			return false;
		}
		try {
			Path dataDir = config.dataDirPath();
			engine = new H2MvStoreImpl();
			engine.open(dataDir);
			inflightStore = new H2InflightStore(engine);
			inflightCleaner = new InflightTtlCleaner(inflightStore, config.getInflightCleanPeriodMs());
			inflightCleaner.start();
			sessionStore = new H2SessionStore(engine);
			sharedSubStore = new H2SharedSubStore(engine);
			retainIndex = new RetainIndex(engine);
			retainIndex.loadFromStore();
			logger.info("[Storage] V3 persistence started at {}", dataDir.toAbsolutePath());
			return true;
		} catch (Exception e) {
			logger.error("[Storage] Failed to start H2 store, falling back to in-memory mode", e);
			engine = null;
			inflightStore = null;
			inflightCleaner = null;
			sessionStore = null;
			sharedSubStore = null;
			retainIndex = null;
			return false;
		}
	}

	/**
	 * Closes the H2 store and stops the cleaner.  No-op when storage is
	 * disabled.
	 */
	public void stop() {
		if (!active) {
			return;
		}
		try {
			if (inflightCleaner != null) {
				inflightCleaner.stop();
			}
			if (inflightStore != null) {
				inflightStore.shutdown();
			}
			if (engine != null) {
				engine.close();
			}
			logger.info("[Storage] V3 persistence stopped");
		} catch (Exception e) {
			logger.error("[Storage] Error stopping H2 store", e);
		}
	}

	public boolean isActive() {
		return active && engine != null;
	}

	public H2MvStoreImpl getEngine() {
		return engine;
	}

	public InflightStore getInflightStore() {
		return inflightStore;
	}

	public InflightTtlCleaner getInflightCleaner() {
		return inflightCleaner;
	}

	public SessionStore getSessionStore() {
		return sessionStore;
	}

	public SharedSubStore getSharedSubStore() {
		return sharedSubStore;
	}

	public RetainIndex getRetainIndex() {
		return retainIndex;
	}

	public MqttStorageConfig getConfig() {
		return config;
	}

	/**
	 * Returns the raw {@link LocalKvStore} for ad-hoc access (e.g. tests).
	 *
	 * @return the H2 engine, or {@code null} when storage is disabled
	 */
	public LocalKvStore getRawStore() {
		return engine;
	}
}