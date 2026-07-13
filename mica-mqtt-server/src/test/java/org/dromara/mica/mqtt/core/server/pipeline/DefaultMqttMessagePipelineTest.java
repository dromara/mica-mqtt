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

package org.dromara.mica.mqtt.core.server.pipeline;

import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class DefaultMqttMessagePipelineTest {

	@Test
	void testRouteByMessageType() {
		DefaultMqttMessagePipeline pipeline = new DefaultMqttMessagePipeline();
		AtomicInteger subscribeCount = new AtomicInteger();
		AtomicInteger unsubscribeCount = new AtomicInteger();
		pipeline.addHandler(new TestHandler(MessageType.SUBSCRIBE, 0, true, subscribeCount, null));
		pipeline.addHandler(new TestHandler(MessageType.UNSUBSCRIBE, 0, true, unsubscribeCount, null));

		Message message = new Message();
		message.setMessageType(MessageType.SUBSCRIBE);
		Assertions.assertTrue(pipeline.handle(message));
		Assertions.assertEquals(1, subscribeCount.get());
		Assertions.assertEquals(0, unsubscribeCount.get());
	}

	@Test
	void testOrderAndInterruptWithinSameType() {
		DefaultMqttMessagePipeline pipeline = new DefaultMqttMessagePipeline();
		List<Integer> invocationOrder = new ArrayList<>();
		pipeline.addHandler(new TestHandler(MessageType.UP_STREAM, 30, true, null, invocationOrder));
		pipeline.addHandler(new TestHandler(MessageType.UP_STREAM, 10, false, null, invocationOrder));
		pipeline.addHandler(new TestHandler(MessageType.UP_STREAM, 20, true, null, invocationOrder));

		Message message = new Message();
		message.setMessageType(MessageType.UP_STREAM);
		Assertions.assertTrue(pipeline.handle(message));
		Assertions.assertEquals(Arrays.asList(10), invocationOrder);
	}

	private static class TestHandler implements MqttMessagePipelineHandler {
		private final MessageType messageType;
		private final int order;
		private final boolean continueProcess;
		private final AtomicInteger invocationCount;
		private final List<Integer> invocationOrder;

		private TestHandler(MessageType messageType, int order, boolean continueProcess,
							AtomicInteger invocationCount, List<Integer> invocationOrder) {
			this.messageType = messageType;
			this.order = order;
			this.continueProcess = continueProcess;
			this.invocationCount = invocationCount;
			this.invocationOrder = invocationOrder;
		}

		@Override
		public MessageType[] messageTypes() {
			return new MessageType[]{messageType};
		}

		@Override
		public boolean handle(Message message) {
			if (invocationCount != null) {
				invocationCount.incrementAndGet();
			}
			if (invocationOrder != null) {
				invocationOrder.add(order);
			}
			return continueProcess;
		}

		@Override
		public int getOrder() {
			return order;
		}
	}
}
