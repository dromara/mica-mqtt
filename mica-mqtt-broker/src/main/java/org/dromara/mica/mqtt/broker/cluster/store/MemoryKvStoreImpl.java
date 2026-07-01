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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Pure in-memory implementation of {@link LocalKvStore} intended for unit tests.
 * <p>
 * Uses a {@link ConcurrentSkipListMap} so that keys are sorted and the
 * {@link #scan(String)} operation returns results in ascending key order —
 * identical ordering to the H2 MVStore-backed implementation.
 * </p>
 * <p>
 * This implementation does not persist any data; all state is lost when the
 * JVM exits.  It is suitable only for tests that do not require crash-recovery
 * verification.
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class MemoryKvStoreImpl implements LocalKvStore {

	private final ConcurrentSkipListMap<String, byte[]> data = new ConcurrentSkipListMap<>();
	private volatile boolean open = false;

	@Override
	public void open(Path dataDir) {
		data.clear();
		open = true;
	}

	@Override
	public void close() {
		open = false;
	}

	@Override
	public byte[] get(String key) {
		return data.get(key);
	}

	@Override
	public void put(String key, byte[] value) {
		data.put(key, value);
	}

	@Override
	public void delete(String key) {
		data.remove(key);
	}

	@Override
	public List<KeyValue> scan(String prefix) {
		List<KeyValue> result = new ArrayList<>();
		if (prefix == null || prefix.isEmpty()) {
			for (java.util.Map.Entry<String, byte[]> entry : data.entrySet()) {
				result.add(new KeyValue(entry.getKey(), entry.getValue()));
			}
		} else {
			// Use the prefix as an inclusive lower bound; iterate and stop once
			// a key no longer starts with the prefix (robust for all Unicode characters).
			for (java.util.Map.Entry<String, byte[]> entry : data.tailMap(prefix).entrySet()) {
				if (!entry.getKey().startsWith(prefix)) {
					break;
				}
				result.add(new KeyValue(entry.getKey(), entry.getValue()));
			}
		}
		return result;
	}

	@Override
	public void executeInTransaction(Runnable body) {
		// In-memory store: no real transaction support; just run the body.
		body.run();
	}

	@Override
	public StoreStats stats() {
		return new StoreStats(-1L, data.size(), open);
	}

	/**
	 * Returns the number of entries in the store (for test assertions).
	 *
	 * @return the number of entries currently held in this in-memory store
	 */
	public int size() {
		return data.size();
	}
}
