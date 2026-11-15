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

package org.dromara.mica.mqtt.core.server;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.pipeline.MqttMessageDeliveryHandler;
import org.dromara.mica.mqtt.core.server.store.InMemoryMqttMessageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class MqttWillDeliveryTest {
	@Test
	void retainsWillWhenAllDeliveryAttemptsFail() throws Exception {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		Message will = new Message();
		store.addWillMessage("client", will);
		CountDownLatch attempts = new CountDownLatch(3);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttServerCreator creator = MqttServer.create().mqttExecutor(executor).messageStore(store);
			creator.getMessagePipeline().addHandler(message -> {
				attempts.countDown();
				throw new IllegalStateException("failure");
			});
			MqttServerAioListener listener = new MqttServerAioListener(creator);
			Method send = MqttServerAioListener.class.getDeclaredMethod("sendWillMessage", String.class, Message.class);
			send.setAccessible(true);
			send.invoke(listener, "client", will);
			assertTrue(attempts.await(3, TimeUnit.SECONDS));
			executor.shutdown();
			assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
			assertSame(will, store.getWillMessage("client"));
		} finally {
			executor.shutdownNow();
			store.clean();
		}
	}

	@Test
	void retriesPipelineFailureBeforeClearingWill() throws Exception {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		Message will = new Message();
		store.addWillMessage("client", will);
		AtomicInteger attempts = new AtomicInteger();
		CountDownLatch succeeded = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttServerCreator creator = MqttServer.create().mqttExecutor(executor).messageStore(store);
			creator.getMessagePipeline().addHandler(message -> {
				if (attempts.incrementAndGet() < 3) {
					throw new IllegalStateException("temporary failure");
				}
				succeeded.countDown();
				return true;
			});
			MqttServerAioListener listener = new MqttServerAioListener(creator);
			Method send = MqttServerAioListener.class.getDeclaredMethod("sendWillMessage", String.class, Message.class);
			send.setAccessible(true);
			send.invoke(listener, "client", will);
			assertTrue(succeeded.await(3, TimeUnit.SECONDS));
			executor.shutdown();
			assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
			assertEquals(3, attempts.get());
			assertNull(store.getWillMessage("client"));
		} finally {
			executor.shutdownNow();
			store.clean();
		}
	}

	@Test
	void deliveryFailureBeforeAnyDeliveryHandlerKeepsWill() throws Exception {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		Message will = new Message();
		store.addWillMessage("client", will);
		AtomicInteger deliveries = new AtomicInteger();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttServerCreator creator = MqttServer.create().mqttExecutor(executor).messageStore(store);
			// 前置 handler 主动中断，投递 handler 永远不会执行
			creator.getMessagePipeline().addHandler(new MqttMessageDeliveryHandler() {
				@Override
				public boolean handle(Message message) {
					deliveries.incrementAndGet();
					return true;
				}

				@Override
				public int getOrder() {
					return 200;
				}
			});
			creator.getMessagePipeline().addHandler(message -> false);
			MqttServerAioListener listener = new MqttServerAioListener(creator);
			Method send = MqttServerAioListener.class.getDeclaredMethod("sendWillMessage", String.class, Message.class);
			send.setAccessible(true);
			send.invoke(listener, "client", will);
			executor.shutdown();
			assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
			assertEquals(0, deliveries.get());
			assertSame(will, store.getWillMessage("client"));
		} finally {
			executor.shutdownNow();
			store.clean();
		}
	}

	@Test
	void postDeliveryHandlerFailureMustNotRepublishWill() throws Exception {
		InMemoryMqttMessageStore store = new InMemoryMqttMessageStore();
		Message will = new Message();
		store.addWillMessage("client", will);
		AtomicInteger deliveries = new AtomicInteger();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			MqttServerCreator creator = MqttServer.create().mqttExecutor(executor).messageStore(store);
			creator.getMessagePipeline().addHandler(new MqttMessageDeliveryHandler() {
				@Override
				public boolean handle(Message message) {
					deliveries.incrementAndGet();
					return true;
				}

				@Override
				public int getOrder() {
					return 100;
				}
			});
			// 投递已成功，后置 handler 的异常不应被当成投递失败
			creator.getMessagePipeline().addHandler(new org.dromara.mica.mqtt.core.server.pipeline.MqttMessageHandler() {
				@Override
				public boolean handle(Message message) {
					throw new IllegalStateException("post delivery failure");
				}

				@Override
				public int getOrder() {
					return 200;
				}
			});
			MqttServerAioListener listener = new MqttServerAioListener(creator);
			Method send = MqttServerAioListener.class.getDeclaredMethod("sendWillMessage", String.class, Message.class);
			send.setAccessible(true);
			send.invoke(listener, "client", will);
			executor.shutdown();
			assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
			assertEquals(1, deliveries.get());
			assertNull(store.getWillMessage("client"));
		} finally {
			executor.shutdownNow();
			store.clean();
		}
	}
}
