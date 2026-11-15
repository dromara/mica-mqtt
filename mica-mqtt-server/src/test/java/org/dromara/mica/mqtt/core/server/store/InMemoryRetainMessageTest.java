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
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class InMemoryRetainMessageTest {

	private static Message message(String topic) {
		Message message = new Message();
		message.setTopic(topic);
		return message;
	}

	/**
	 * 永久 retain 与带 TTL retain 并发更新同一 topic 时，不能出现两条 retained。
	 */
	@Test
	void concurrentPermanentAndTimedUpdateKeepsSingleEntry() throws Exception {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int i = 0; i < 1000; i++) {
				Message permanent = message("topic");
				Message timed = message("topic");
				CountDownLatch start = new CountDownLatch(1);
				Future<?> first = executor.submit(() -> {
					start.await();
					store.addRetainMessage("topic", 0, permanent);
					return null;
				});
				Future<?> second = executor.submit(() -> {
					start.await();
					store.addRetainMessage("topic", 60, timed);
					return null;
				});
				start.countDown();
				first.get();
				second.get();
				List<Message> retained = store.getRetainMessage("topic");
				assertEquals(1, retained.size(), "同一 topic 只能有一条 retained");
				Message current = retained.get(0);
				assertTrue(current == permanent || current == timed);
			}
		} finally {
			executor.shutdownNow();
			store.clean();
		}
	}

	@Test
	void permanentRetainReplacesTimedRetain() throws Exception {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		Message timed = message("topic");
		Message permanent = message("topic");
		store.addRetainMessage("topic", 60, timed);
		store.addRetainMessage("topic", 0, permanent);
		List<Message> retained = store.getRetainMessage("topic");
		assertEquals(1, retained.size());
		assertSame(permanent, retained.get(0));
		store.clean();
	}
}
