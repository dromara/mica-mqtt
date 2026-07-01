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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background thread that periodically removes expired QoS 1/2 inflight records.
 * <p>
 * H2 MVStore does not support native TTL.  This class compensates by scheduling a
 * periodic scan of the {@link InflightStore} and removing records whose
 * {@code expireAt} timestamp has passed.  The scan runs every
 * {@link #DEFAULT_PERIOD_MS} milliseconds (default 30 s).
 * </p>
 *
 * <h2>Alert threshold</h2>
 * <p>
 * If the total inflight count exceeds {@link #ALERT_THRESHOLD} after a scan, a
 * {@code WARN} log is emitted.  This surfaces situations where clients are not
 * acknowledging messages (e.g. slow consumers, silent crashes).  Connect this
 * to an external metrics pipeline via {@link org.dromara.mica.mqtt.broker.cluster.metrics.ClusterMetrics} if needed.
 * </p>
 *
 * <h2>Accuracy</h2>
 * <p>
 * Records may linger for at most {@code TTL + period} milliseconds before removal
 * (e.g. with a 30 s TTL and 30 s period, a record is removed at most 60 s after
 * it was written).  This is acceptable for MQTT inflight semantics where the exact
 * eviction time is not critical.
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 * @see InflightStore
 * @see H2InflightStore
 */
public class InflightTtlCleaner {
	private static final Logger logger = LoggerFactory.getLogger(InflightTtlCleaner.class);
	private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

	/** Default TTL for inflight messages: 30 seconds. */
	public static final long DEFAULT_TTL_MS = 30_000L;

	/** Default cleanup period: 30 seconds. */
	public static final long DEFAULT_PERIOD_MS = 30_000L;

	/**
	 * Number of inflight records above which a WARN alert is logged.
	 * Corresponds to the "inflight 滞后堆积告警（>10w 条告警）" requirement.
	 */
	static final long ALERT_THRESHOLD = 100_000L;

	private final InflightStore store;
	private final long periodMs;
	private ScheduledExecutorService scheduler;

	/**
	 * Constructs a cleaner with the default period ({@value #DEFAULT_PERIOD_MS} ms).
	 *
	 * @param store the inflight store to clean; must not be {@code null}
	 */
	public InflightTtlCleaner(InflightStore store) {
		this(store, DEFAULT_PERIOD_MS);
	}

	/**
	 * Constructs a cleaner with a custom cleanup period.
	 *
	 * @param store    the inflight store to clean; must not be {@code null}
	 * @param periodMs cleanup interval in milliseconds; must be positive
	 */
	public InflightTtlCleaner(InflightStore store, long periodMs) {
		this.store = store;
		this.periodMs = periodMs;
	}

	/**
	 * Starts the background cleanup thread.
	 * <p>
	 * This method is idempotent: calling it more than once has no effect.
	 * </p>
	 */
	public synchronized void start() {
		if (scheduler != null) {
			return;
		}
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "mica-mqtt-inflight-ttl-cleaner-" + INSTANCE_COUNTER.incrementAndGet());
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::runCleanup, periodMs, periodMs, TimeUnit.MILLISECONDS);
		logger.info("[InflightTtlCleaner] Started with period={}ms ttl={}ms", periodMs, DEFAULT_TTL_MS);
	}

	/**
	 * Stops the background cleanup thread gracefully.
	 * <p>
	 * Waits up to 5 seconds for the thread to finish its current scan, then forces
	 * shutdown.  Safe to call even if {@link #start()} was never called.
	 * </p>
	 */
	public synchronized void stop() {
		if (scheduler == null) {
			return;
		}
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
		scheduler = null;
		logger.info("[InflightTtlCleaner] Stopped");
	}

	/**
	 * Executes a single TTL scan.
	 * <p>
	 * Exposed package-private for testing.
	 * </p>
	 */
	void runCleanup() {
		try {
			long nowMs = System.currentTimeMillis();
			int removed = store.removeExpired(nowMs);
			if (removed > 0) {
				logger.debug("[InflightTtlCleaner] Removed {} expired inflight records", removed);
			}
			long total = store.count();
			if (total > ALERT_THRESHOLD) {
				logger.warn("[InflightTtlCleaner] Inflight record count {} exceeds alert threshold {}; " +
					"check for slow or unresponsive clients", total, ALERT_THRESHOLD);
			}
		} catch (Exception e) {
			logger.error("[InflightTtlCleaner] Cleanup scan failed", e);
		}
	}
}
