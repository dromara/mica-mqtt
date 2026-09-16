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

package org.dromara.mica.mqtt.broker.rule.ext.template;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警中心：ring buffer + dedupe cache + 异步派发。
 * <p>
 * 去重使用单层 Caffeine 缓存（key = dedupeKey），TTL 由该条事件的
 * {@code dedupeWindowMs} 决定；早期实现是「Map&lt;dedupeKey, Cache&lt;dedupeKey, Long&gt;&gt;」双层嵌套，
 * 外层 Map 永不清理，且 get/put 非原子。去重命中时事件不再进入 ring，也不再派发。
 * </p>
 *
 * @author L.cm
 */
public class AlertCenter {

	private static final Logger logger = LoggerFactory.getLogger(AlertCenter.class);
	/**
	 * ring buffer 保留的最大告警条数。
	 */
	private static final int RING_CAPACITY = 1000;
	/**
	 * 默认去重窗口：5 分钟。
	 */
	private static final long DEFAULT_DEDUPE_WINDOW_MS = 5 * 60 * 1000L;

	private final Deque<AlertEvent> ring = new ArrayDeque<>();
	private final List<AlertNotifier> notifiers = new CopyOnWriteArrayList<>();
	private final ExecutorService dispatchExecutor;
	private final Cache<String, DedupeEntry> dedupeCache = Caffeine.newBuilder()
		.maximumSize(10_000)
		.expireAfter(new Expiry<String, DedupeEntry>() {
			@Override
			public long expireAfterCreate(String key, DedupeEntry value, long currentTime) {
				return value.expireAfterNanos();
			}

			@Override
			public long expireAfterUpdate(String key, DedupeEntry value, long currentTime,
										  long currentDuration) {
				return value.expireAfterNanos();
			}

			@Override
			public long expireAfterRead(String key, DedupeEntry value, long currentTime,
										long currentDuration) {
				return currentDuration;
			}
		})
		.build();

	public AlertCenter() {
		this(defaultExecutor());
	}

	public AlertCenter(ExecutorService dispatchExecutor) {
		this.dispatchExecutor = dispatchExecutor;
	}

	private static ExecutorService defaultExecutor() {
		AtomicInteger idx = new AtomicInteger();
		return new ThreadPoolExecutor(2, 8,
			60L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>(1024),
			r -> {
				Thread t = new Thread(r, "alert-dispatch-" + idx.incrementAndGet());
				t.setDaemon(true);
				return t;
			});
	}

	public AlertCenter addNotifier(AlertNotifier notifier) {
		notifiers.add(notifier);
		return this;
	}

	public void trigger(AlertEvent event) {
		if (isDuplicate(event)) {
			return;
		}
		synchronized (ring) {
			ring.addLast(event);
			while (ring.size() > RING_CAPACITY) {
				ring.pollFirst();
			}
		}
		dispatchExecutor.execute(() -> dispatch(event));
	}

	/**
	 * 同 dedupeKey 在窗口内重复触发时返回 {@code true}，并刷新窗口起点。
	 */
	private boolean isDuplicate(AlertEvent event) {
		String dedupeKey = event.getDedupeKey();
		if (dedupeKey == null || dedupeKey.isEmpty()) {
			return false;
		}
		long windowMs = parseWindow(event);
		long now = event.getTs();
		final boolean[] duplicate = {false};
		dedupeCache.asMap().compute(dedupeKey, (key, prev) -> {
			if (prev != null && now - prev.ts < windowMs) {
				duplicate[0] = true;
				return prev;
			}
			return new DedupeEntry(now, windowMs);
		});
		return duplicate[0];
	}

	private static long parseWindow(AlertEvent event) {
		Object window = event.getExtra() == null ? null : event.getExtra().get("dedupeWindowMs");
		if (window instanceof Number) {
			return Math.max(1L, ((Number) window).longValue());
		}
		return DEFAULT_DEDUPE_WINDOW_MS;
	}

	private void dispatch(AlertEvent event) {
		for (AlertNotifier notifier : notifiers) {
			try {
				notifier.send(event);
			} catch (Throwable t) {
				logger.error("alert notifier {} failed: {}", notifier.getName(), t.getMessage(), t);
			}
		}
	}

	public List<AlertEvent> snapshot() {
		synchronized (ring) {
			return new ArrayList<>(ring);
		}
	}

	public int notifierCount() {
		return notifiers.size();
	}

	public void shutdown() {
		dispatchExecutor.shutdownNow();
	}

	/**
	 * 去重缓存条目：最后一次触发时间 + 该条事件的去重窗口。
	 */
	private static final class DedupeEntry {
		private final long ts;
		private final long windowMs;

		private DedupeEntry(long ts, long windowMs) {
			this.ts = ts;
			this.windowMs = windowMs;
		}

		private long expireAfterNanos() {
			return TimeUnit.MILLISECONDS.toNanos(windowMs);
		}
	}
}
