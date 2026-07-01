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

import org.h2.mvstore.Cursor;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * H2 MVStore-backed implementation of {@link LocalKvStore}.
 * <p>
 * Uses the embedded H2 MVStore engine (~2 MB, pure Java, WAL-based crash safety) to
 * persist MQTT broker state to a local file.  All MQTT state categories (sessions,
 * retain messages, shared subscription membership, QoS inflight messages) share a
 * single store file but use different named maps to avoid key collisions.
 * </p>
 *
 * <h2>Thread safety</h2>
 * <p>
 * {@link MVMap} operations in H2 are themselves thread-safe, but {@link MVStore#commit()}
 * and {@link MVStore#close()} are guarded by a write lock here to prevent concurrent
 * partial commits during shutdown.
 * </p>
 *
 * <h2>Crash safety</h2>
 * <p>
 * MVStore writes are append-only (WAL).  An abrupt process kill ({@code kill -9}) leaves
 * at most the last uncommitted chunk unreplayed; MVStore recovers automatically on the
 * next open via WAL replay.
 * </p>
 *
 * <h2>File locking</h2>
 * <p>
 * H2 uses {@code FILE_LOCK=FILE} by default, which prevents two JVM processes from
 * opening the same store file simultaneously.  Each broker node must therefore use its
 * own data directory (e.g. {@code data/node-9001/}).
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class H2MvStoreImpl implements LocalKvStore {
	private static final Logger logger = LoggerFactory.getLogger(H2MvStoreImpl.class);

	/** Store file name within the data directory. */
	static final String STORE_FILE_NAME = "mica-mqtt-store";

	/** Default map name used for key-value pairs. */
	private static final String DEFAULT_MAP_NAME = "mica_mqtt_data";

	private volatile MVStore store;
	private volatile MVMap<String, byte[]> dataMap;

	/**
	 * Guards store-level operations (commit, close) from concurrent access.
	 * Read lock is acquired for individual map reads/writes; write lock for commit/close.
	 */
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	@Override
	public void open(Path dataDir) {
		File dir = dataDir.toFile();
		if (!dir.exists() && !dir.mkdirs()) {
			throw new RuntimeException("Cannot create data directory: " + dataDir);
		}
		String filePath = dataDir.resolve(STORE_FILE_NAME).toString();
		try {
			store = new MVStore.Builder()
				.fileName(filePath)
				// Disable auto-commit so callers control flush timing.
				// Sub-stores (e.g. H2InflightStore) commit after every N writes;
				// a final commit is always performed in close() to flush uncommitted data.
				.autoCommitDisabled()
				.open();
			dataMap = store.openMap(DEFAULT_MAP_NAME);
			logger.info("[H2Store] Opened store at {} (entries={})", filePath, dataMap.sizeAsLong());
		} catch (Exception e) {
			throw new RuntimeException("Failed to open H2 MVStore at: " + filePath, e);
		}
	}

	@Override
	public void close() {
		lock.writeLock().lock();
		try {
			if (store != null && !store.isClosed()) {
				store.commit();
				store.close();
				logger.info("[H2Store] Store closed");
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public byte[] get(String key) {
		lock.readLock().lock();
		try {
			return dataMap.get(key);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void put(String key, byte[] value) {
		lock.readLock().lock();
		try {
			dataMap.put(key, value);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void delete(String key) {
		lock.readLock().lock();
		try {
			dataMap.remove(key);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public List<KeyValue> scan(String prefix) {
		lock.readLock().lock();
		try {
			List<KeyValue> result = new ArrayList<>();
			if (prefix == null || prefix.isEmpty()) {
				Cursor<String, byte[]> cursor = dataMap.cursor(null);
				while (cursor.hasNext()) {
					String key = cursor.next();
					result.add(new KeyValue(key, cursor.getValue()));
				}
			} else {
				// cursor(fromKey) positions the iterator at the first key >= fromKey.
				// Iterate while the current key starts with the prefix.
				Cursor<String, byte[]> cursor = dataMap.cursor(prefix);
				while (cursor.hasNext()) {
					String key = cursor.next();
					if (!key.startsWith(prefix)) {
						break;
					}
					result.add(new KeyValue(key, cursor.getValue()));
				}
			}
			return result;
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void executeInTransaction(Runnable body) {
		lock.writeLock().lock();
		try {
			body.run();
			store.commit();
		} catch (RuntimeException e) {
			// MVStore has no rollback for in-flight changes in the open transaction model;
			// log the failure and rethrow so callers can take compensating action.
			logger.error("[H2Store] Transaction body failed, changes may be partially written", e);
			throw e;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public StoreStats stats() {
		if (store == null || store.isClosed()) {
			return new StoreStats(-1L, 0L, false);
		}
		long fileSize = -1L;
		try {
			if (store.getFileStore() != null) {
				fileSize = store.getFileStore().size();
			}
		} catch (Exception e) {
			logger.debug("[H2Store] Could not read file size", e);
		}
		long entryCount = dataMap == null ? 0L : dataMap.sizeAsLong();
		return new StoreStats(fileSize, entryCount, true);
	}

	/**
	 * Opens a named map within the store.
	 * <p>
	 * Callers that need a dedicated map (e.g. {@code H2InflightStore}) should call this
	 * method after {@link #open(Path)} to obtain an isolated namespace for their keys.
	 * </p>
	 *
	 * @param mapName the logical name of the map
	 * @return the opened (or existing) map
	 */
	public MVMap<String, byte[]> openMap(String mapName) {
		return store.openMap(mapName);
	}

	/**
	 * Commits pending changes to the WAL.
	 * <p>
	 * Called by sub-stores (e.g. {@link H2InflightStore}) that share this engine instance
	 * and need to flush their writes without closing the store.
	 * </p>
	 */
	public void commit() {
		lock.writeLock().lock();
		try {
			if (store != null && !store.isClosed()) {
				store.commit();
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	/** Exposed for testing. */
	MVStore getStore() {
		return store;
	}
}
