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

package org.dromara.mica.mqtt.broker.cluster.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the V3 persistence layer (H2 MVStore).
 * <p>
 * When {@link #isEnabled()} is {@code true} the cluster broker will open an embedded
 * H2 MVStore file under {@link #getDataDir()} and persist:
 * <ul>
 *   <li>QoS 1/2 inflight messages (so they can be replayed on reconnect)</li>
 *   <li>Session state (used during cross-node takeover)</li>
 *   <li>Shared subscription membership (so owner/backup failover is loss-less)</li>
 *   <li>Retain messages (with an in-memory skiplist index for wildcard lookup)</li>
 * </ul>
 * <p>
 * When {@link #isEnabled()} is {@code false} the broker behaves exactly as V1/V2:
 * all state lives in memory only and is lost on restart.  This is the default and
 * preserves backward compatibility.
 * </p>
 * <p>
 * <strong>Degraded startup</strong> (invariant INV-6 in the storage design doc):
 * if the H2 file cannot be opened (corruption, permission denied, disk full),
 * the broker will log an error and continue in pure-memory mode rather than
 * refusing to start.  This ensures partial-storage failures do not cause a
 * complete outage.
 * </p>
 *
 * @author L.cm
 * @see org.dromara.mica.mqtt.broker.cluster.store.LocalKvStore
 * @since 2.6.0
 */
public class MqttStorageConfig {
	/**
	 * Whether the V3 persistence layer is enabled.
	 * <p>
	 * Defaults to {@code false} so that simply adding a {@code MqttClusterConfig}
	 * with {@code enabled(true)} does not silently start writing data to disk.
	 * Operators must opt-in explicitly.
	 * </p>
	 */
	private boolean enabled = false;

	/**
	 * Directory in which the H2 MVStore file lives.
	 * <p>
	 * Each broker node <strong>must</strong> use a unique directory because
	 * MVStore uses {@code FILE_LOCK=FILE} by default and refuses two JVMs from
	 * opening the same file concurrently.  The convention is
	 * {@code data/<nodeId>/}, for example {@code data/node-9001/}.
	 * </p>
	 */
	private String dataDir = "data/mica-mqtt-cluster";

	/**
	 * Inflight message TTL in milliseconds.
	 * <p>
	 * A QoS 1/2 message that has been in flight longer than this value is removed
	 * by the periodic cleaner (see {@link org.dromara.mica.mqtt.broker.cluster.store.InflightTtlCleaner}).
	 * A value of {@code 0} disables TTL eviction and is the default, preserving
	 * MQTT delivery guarantees. Positive values are an explicit lossy capacity policy.
	 * </p>
	 */
	private long inflightTtlMs = 0L;

	/**
	 * Inflight cleaner period in milliseconds.
	 * <p>
	 * How often the background thread scans for expired inflight records.
	 * Actual eviction may lag by up to {@code ttl + period} milliseconds.
	 * </p>
	 */
	private long inflightCleanPeriodMs = 30_000L;

	/**
	 * Whether to persist session state (P2.1).
	 * <p>
	 * When {@code true} and {@link #isEnabled()} is also {@code true}, sessions are
	 * persisted to H2 and cross-node takeover can recover sessions after a node
	 * restart.
	 * </p>
	 */
	private boolean persistSession = true;

	/**
	 * Whether to persist shared subscription membership (P2.2).
	 */
	private boolean persistSharedSub = true;

	/**
	 * Whether to persist retain messages (P2.4).
	 * <p>
	 * When {@code false} retain messages live only in memory and are lost on
	 * restart, just like V1.
	 * </p>
	 */
	private boolean persistRetain = true;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public MqttStorageConfig enabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	public String getDataDir() {
		return dataDir;
	}

	public void setDataDir(String dataDir) {
		this.dataDir = dataDir;
	}

	public MqttStorageConfig dataDir(String dataDir) {
		this.dataDir = dataDir;
		return this;
	}

	/**
	 * Returns the storage directory as a {@link Path}, for passing to
	 * {@link org.dromara.mica.mqtt.broker.cluster.store.LocalKvStore#open(Path)}.
	 *
	 * @return the storage directory as an absolute or relative path
	 */
	public Path dataDirPath() {
		return Paths.get(dataDir);
	}

	public long getInflightTtlMs() {
		return inflightTtlMs;
	}

	public void setInflightTtlMs(long inflightTtlMs) {
		this.inflightTtlMs = inflightTtlMs;
	}

	public MqttStorageConfig inflightTtlMs(long inflightTtlMs) {
		this.inflightTtlMs = inflightTtlMs;
		return this;
	}

	public long getInflightCleanPeriodMs() {
		return inflightCleanPeriodMs;
	}

	public void setInflightCleanPeriodMs(long inflightCleanPeriodMs) {
		this.inflightCleanPeriodMs = inflightCleanPeriodMs;
	}

	public MqttStorageConfig inflightCleanPeriodMs(long inflightCleanPeriodMs) {
		this.inflightCleanPeriodMs = inflightCleanPeriodMs;
		return this;
	}

	public boolean isPersistSession() {
		return persistSession;
	}

	public void setPersistSession(boolean persistSession) {
		this.persistSession = persistSession;
	}

	public MqttStorageConfig persistSession(boolean persistSession) {
		this.persistSession = persistSession;
		return this;
	}

	public boolean isPersistSharedSub() {
		return persistSharedSub;
	}

	public void setPersistSharedSub(boolean persistSharedSub) {
		this.persistSharedSub = persistSharedSub;
	}

	public MqttStorageConfig persistSharedSub(boolean persistSharedSub) {
		this.persistSharedSub = persistSharedSub;
		return this;
	}

	public boolean isPersistRetain() {
		return persistRetain;
	}

	public void setPersistRetain(boolean persistRetain) {
		this.persistRetain = persistRetain;
	}

	public MqttStorageConfig persistRetain(boolean persistRetain) {
		this.persistRetain = persistRetain;
		return this;
	}
}
