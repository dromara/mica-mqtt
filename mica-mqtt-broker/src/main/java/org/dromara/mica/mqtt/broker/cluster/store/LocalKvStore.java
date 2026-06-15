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

import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction over a local, embedded key-value store used for V3 cluster persistence.
 * <p>
 * The V3 storage layer relies on this interface to persist MQTT broker state (sessions,
 * retain messages, shared subscription membership, QoS 1/2 inflight messages) to a
 * local file so that the broker can recover its state after a restart without depending
 * on other cluster nodes.
 * </p>
 * <p>
 * The primary implementation is {@code H2MvStoreImpl} which uses the H2 MVStore engine
 * (~2 MB, pure Java, ACID transactions, WAL-based crash safety).  A lightweight
 * {@code MemoryKvStoreImpl} is provided for unit tests.
 * </p>
 * <p>
 * <strong>Thread safety</strong>: implementations must be thread-safe.  Multiple
 * broker threads may call {@link #get}, {@link #put}, {@link #delete}, and
 * {@link #scan} concurrently.
 * </p>
 * <p>
 * <strong>Startup invariant</strong>: {@link #open(Path)} must complete successfully
 * before any other method is called.  If {@link #open} fails, the broker may choose
 * to start in pure-memory mode (degraded) rather than refusing to start entirely
 * (see invariant INV-6 in the storage design document).
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public interface LocalKvStore extends AutoCloseable {

	/**
	 * Opens (or creates) the underlying storage file at the given directory.
	 * <p>
	 * This method is called once during broker startup, before any MQTT connections
	 * are accepted.  It may perform expensive initialisation such as WAL replay.
	 * </p>
	 *
	 * @param dataDir the directory in which to place the storage file; the
	 *                implementation is free to create subdirectories as needed
	 * @throws RuntimeException if the store cannot be opened or created
	 */
	void open(Path dataDir);

	/**
	 * Closes the store and releases all resources.
	 * <p>
	 * After this call returns, no other method should be invoked on this instance.
	 * Implementations should flush any pending writes before closing.
	 * </p>
	 */
	void close();

	/**
	 * Returns the value associated with {@code key}, or {@code null} if absent.
	 *
	 * @param key the lookup key; never {@code null}
	 * @return the stored byte array, or {@code null} if the key does not exist
	 */
	byte[] get(String key);

	/**
	 * Inserts or replaces the value for {@code key}.
	 *
	 * @param key   the key; never {@code null}
	 * @param value the value to store; never {@code null}
	 */
	void put(String key, byte[] value);

	/**
	 * Removes the entry for {@code key} if it exists.
	 *
	 * @param key the key to delete; never {@code null}
	 */
	void delete(String key);

	/**
	 * Returns all key-value pairs whose keys start with {@code prefix}, in ascending
	 * key order.
	 * <p>
	 * Used to implement range scans such as "all inflight messages for clientId X"
	 * where the key is structured as {@code "clientId:packetId"}.
	 * </p>
	 *
	 * @param prefix the key prefix to scan; may be empty to return all entries
	 * @return a list of matching entries (never {@code null}, may be empty)
	 */
	List<KeyValue> scan(String prefix);

	/**
	 * Executes {@code body} within a single store transaction.
	 * <p>
	 * All {@link #put} and {@link #delete} calls made inside {@code body} are
	 * committed atomically on normal return, or rolled back if an exception is thrown.
	 * </p>
	 * <p>
	 * For implementations that do not support multi-statement transactions (e.g. the
	 * in-memory implementation), this method is equivalent to simply calling
	 * {@code body.run()}.
	 * </p>
	 *
	 * @param body the transactional work to execute; must not be {@code null}
	 * @throws RuntimeException if the transaction fails or {@code body} throws
	 */
	void executeInTransaction(Runnable body);

	/**
	 * Returns a lightweight snapshot of store health and size statistics.
	 *
	 * @return the current {@link StoreStats}; never {@code null}
	 */
	StoreStats stats();

	// ---- Nested value type -------------------------------------------------------

	/**
	 * An immutable key-value pair returned by {@link #scan(String)}.
	 */
	final class KeyValue {
		private final String key;
		private final byte[] value;

		public KeyValue(String key, byte[] value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public byte[] getValue() {
			return value;
		}
	}

	// ---- Nested stats type -------------------------------------------------------

	/**
	 * Snapshot of store statistics for health monitoring and alerting.
	 */
	final class StoreStats {
		/** File size in bytes ({@code -1} for in-memory stores). */
		private final long fileSizeBytes;
		/** Total number of key-value entries in the store. */
		private final long entryCount;
		/** Whether the store is currently open and healthy. */
		private final boolean healthy;

		public StoreStats(long fileSizeBytes, long entryCount, boolean healthy) {
			this.fileSizeBytes = fileSizeBytes;
			this.entryCount = entryCount;
			this.healthy = healthy;
		}

		public long getFileSizeBytes() {
			return fileSizeBytes;
		}

		public long getEntryCount() {
			return entryCount;
		}

		public boolean isHealthy() {
			return healthy;
		}

		@Override
		public String toString() {
			return "StoreStats{fileSizeBytes=" + fileSizeBytes +
				", entryCount=" + entryCount +
				", healthy=" + healthy + '}';
		}
	}
}
