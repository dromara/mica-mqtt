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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警中心：ring buffer + dedupe cache + 异步派发。
 *
 * @author L.cm
 */
public class AlertCenter {

	private static final Logger logger = LoggerFactory.getLogger(AlertCenter.class);

	private final Deque<AlertEvent> ring = new ArrayDeque<AlertEvent>(1024) {
		@Override
		public boolean add(AlertEvent e) {
			synchronized (this) {
				boolean r = super.add(e);
				while (size() > 1000) {
					pollFirst();
				}
				return r;
			}
		}
	};
	private final List<AlertNotifier> notifiers = new CopyOnWriteArrayList<>();
	private final ExecutorService dispatchExecutor;
	private final java.util.concurrent.ConcurrentMap<String, Cache<String, Long>> dedupeCaches
		= new java.util.concurrent.ConcurrentHashMap<>();

	public AlertCenter() {
		this(defaultExecutor());
	}

	public AlertCenter(ExecutorService dispatchExecutor) {
		this.dispatchExecutor = dispatchExecutor;
	}

	private static ExecutorService defaultExecutor() {
		AtomicInteger idx = new AtomicInteger();
		return new java.util.concurrent.ThreadPoolExecutor(2, 8,
			60L, java.util.concurrent.TimeUnit.SECONDS,
			new java.util.concurrent.LinkedBlockingQueue<Runnable>(1024),
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
		ring.add(event);
		if (event.getDedupeKey() != null && !event.getDedupeKey().isEmpty()) {
			long windowMs = parseWindow(event);
			Cache<String, Long> cache = dedupeCaches.computeIfAbsent(event.getDedupeKey(),
				k -> Caffeine.newBuilder()
					.expireAfterWrite(Duration.ofMillis(windowMs))
					.maximumSize(10_000)
					.build());
			Long last = cache.getIfPresent(event.getDedupeKey());
			long now = event.getTs();
			if (last != null && (now - last) < windowMs) {
				return;
			}
			cache.put(event.getDedupeKey(), now);
		}
		dispatchExecutor.execute(() -> dispatch(event));
	}

	private static long parseWindow(AlertEvent event) {
		Object window = event.getExtra() == null ? null : event.getExtra().get("dedupeWindowMs");
		if (window instanceof Number) {
			return ((Number) window).longValue();
		}
		return 5 * 60 * 1000L;
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
}
