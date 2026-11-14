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

package org.dromara.mica.mqtt.core.server.store;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryWillMessageTest {
	@Test
	void 并发清理旧遗嘱与保存新遗嘱() throws Exception {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int i = 0; i < 500; i++) {
				Message oldWill = new Message();
				Message newWill = new Message();
				store.addWillMessage("client", oldWill);
				CountDownLatch start = new CountDownLatch(1);
				Future<?> clear = executor.submit(() -> {
					start.await();
					store.clearWillMessage("client", oldWill);
					return null;
				});
				Future<?> add = executor.submit(() -> {
					start.await();
					store.addWillMessage("client", newWill);
					return null;
				});
				start.countDown();
				clear.get();
				add.get();
				assertSame(newWill, store.getWillMessage("client"));
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 旧连接清理时不删除新连接的遗嘱() {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		Message oldWill = new Message();
		Message newWill = new Message();
		store.addWillMessage("client", oldWill);
		store.addWillMessage("client", newWill);
		assertFalse(store.clearWillMessage("client", oldWill));
		assertSame(newWill, store.getWillMessage("client"));
		assertTrue(store.clearWillMessage("client", newWill));
		assertFalse(store.clearWillMessage("client", newWill));
	}
}
