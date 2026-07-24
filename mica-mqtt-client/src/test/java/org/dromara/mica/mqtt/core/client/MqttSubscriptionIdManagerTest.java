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

package org.dromara.mica.mqtt.core.client;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR10：MQTT 5.0 客户端 Subscription Identifier 自动分配单元测试（spec 3.3.2.3.6）。
 *
 * <p>覆盖：
 * <ul>
 *     <li>nextId 单调递增</li>
 *     <li>reset 后重新从 1 开始</li>
 *     <li>peek 不影响 nextId 计数器</li>
 *     <li>达到上限抛 IllegalStateException</li>
 *     <li>多线程并发分配 ID 唯一性</li>
 * </ul>
 *
 * @author L.cm
 */
class MqttSubscriptionIdManagerTest {

	@Test
	void nextIdIncrementsFromOne() {
		MqttSubscriptionIdManager manager = new MqttSubscriptionIdManager();
		assertEquals(1, manager.nextId());
		assertEquals(2, manager.nextId());
		assertEquals(3, manager.nextId());
	}

	@Test
	void resetRestartsFromOne() {
		MqttSubscriptionIdManager manager = new MqttSubscriptionIdManager();
		manager.nextId();
		manager.nextId();
		manager.nextId();
		manager.reset();
		assertEquals(1, manager.nextId());
	}

	@Test
	void peekDoesNotChangeState() {
		MqttSubscriptionIdManager manager = new MqttSubscriptionIdManager();
		assertEquals(1, manager.peek());
		assertEquals(1, manager.peek());
		assertEquals(1, manager.nextId());
		assertEquals(2, manager.peek());
	}

	@Test
	void exhaustedThrowsIllegalState() {
		// 模拟达到上限（spec 上限 0xFFFFFFF = 268_435_455）
		// 这里通过子类覆盖来快速测试
		ExhaustedManager exhausted = new ExhaustedManager();
		// 首次 nextId() 应立即抛 IllegalStateException（因为内部计数器已经是 MAX+1）
		assertThrows(IllegalStateException.class, exhausted::nextId);
	}

	@Test
	void concurrentAllocationUniqueness() throws InterruptedException {
		MqttSubscriptionIdManager manager = new MqttSubscriptionIdManager();
		int threadCount = 8;
		int iterations = 1000;
		Set<Integer> seenIds = java.util.Collections.synchronizedSet(new HashSet<>());
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicReference<Throwable> error = new AtomicReference<>();
		for (int i = 0; i < threadCount; i++) {
			new Thread(() -> {
				try {
					for (int j = 0; j < iterations; j++) {
						int id = manager.nextId();
						boolean added = seenIds.add(id);
						assertTrue(added, "Duplicate id: " + id);
					}
				} catch (Throwable e) {
					error.set(e);
				} finally {
					latch.countDown();
				}
			}).start();
		}
		assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
		assertNull(error.get(), "Concurrent test produced error: " + error.get());
		assertEquals(threadCount * iterations, seenIds.size());
	}

	@Test
	void maxSubscriptionIdConstant() {
		// spec 3.3.2.3.6: Subscription Identifier 上限 0xFFFFFFF
		assertEquals(0xFFFFFFF, MqttSubscriptionIdManager.MAX_SUBSCRIPTION_ID);
	}

	@Test
	void normalSequenceDoesNotThrow() {
		// 普通业务流：分配 100 个 ID 不抛异常
		MqttSubscriptionIdManager manager = new MqttSubscriptionIdManager();
		for (int i = 0; i < 100; i++) {
			int id = manager.nextId();
			assertTrue(id >= 1);
		}
	}

	@Test
	void subscriptionIdentifierCapabilityDefaultsToAvailableAndCanBeUpdated() {
		MqttClientCreator creator = new MqttClientCreator();
		assertTrue(creator.isSubscriptionIdentifiersAvailable());
		creator.setSubscriptionIdentifiersAvailable(false);
		assertFalse(creator.isSubscriptionIdentifiersAvailable());
		creator.setSubscriptionIdentifiersAvailable(true);
		assertTrue(creator.isSubscriptionIdentifiersAvailable());
	}

	@Test
	void resetAfterExhaustionRecovers() {
		// 重置能恢复：即便到达上限后 reset 也有效
		ExhaustedManager manager = new ExhaustedManager();
		try {
			manager.nextId();
		} catch (IllegalStateException ignore) {
			// expected
		}
		manager.reset();
		// reset 后可以重新分配
		int id = manager.nextId();
		assertTrue(id >= 1);
	}

	/**
	 * 子类：把 nextId 触发到 0xFFFFFFF + 1 上限，模拟"马上用完"场景。
	 */
	private static class ExhaustedManager extends MqttSubscriptionIdManager {
		ExhaustedManager() {
			// 通过反射把内部 AtomicInteger 设为 MAX + 1 → 第一次 nextId() 会抛 IllegalStateException
			try {
				java.lang.reflect.Field field = MqttSubscriptionIdManager.class.getDeclaredField("nextId");
				field.setAccessible(true);
				((java.util.concurrent.atomic.AtomicInteger) field.get(this)).set(MAX_SUBSCRIPTION_ID + 1);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
