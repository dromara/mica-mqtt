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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AlertCenter 单测。
 *
 * @author L.cm
 */
class AlertCenterTest {

	@Test
	void dispatchToNotifier() throws Exception {
		AlertCenter center = new AlertCenter();
		CountDownLatch latch = new CountDownLatch(1);
		AtomicInteger received = new AtomicInteger();
		center.addNotifier(new AlertNotifier() {
			@Override
			public String getName() {
				return "test";
			}

			@Override
			public void send(AlertEvent event) {
				received.incrementAndGet();
				latch.countDown();
			}
		});
		Map<String, Object> extra = new LinkedHashMap<>();
		center.trigger(new AlertEvent(System.currentTimeMillis(), "warning",
			"t", "m", null, null, extra));
		assertTrue(latch.await(2, TimeUnit.SECONDS));
		assertEquals(1, received.get());
		assertEquals(1, center.snapshot().size());
		assertEquals(1, center.notifierCount());
		center.shutdown();
	}

	@Test
	void dedupeSameKey() throws Exception {
		AlertCenter center = new AlertCenter();
		AtomicInteger received = new AtomicInteger();
		center.addNotifier(new AlertNotifier() {
			@Override
			public String getName() { return "t"; }
			@Override
			public void send(AlertEvent event) { received.incrementAndGet(); }
		});
		long now = System.currentTimeMillis();
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("dedupeWindowMs", 60_000L);
		center.trigger(new AlertEvent(now, "warning", "t", "m",
			null, "k", extra));
		Thread.sleep(100);
		center.trigger(new AlertEvent(now + 100, "warning", "t", "m",
			null, "k", extra));
		Thread.sleep(500);
		// 同 key 在 60s 窗口内只触发一次
		assertEquals(1, received.get());
		center.shutdown();
	}
}