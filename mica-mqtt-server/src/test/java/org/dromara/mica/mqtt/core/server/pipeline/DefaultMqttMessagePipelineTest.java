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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMqttMessagePipelineTest {

	private static Message downStream() {
		Message message = new Message();
		message.setMessageType(MessageType.DOWN_STREAM);
		message.setTopic("topic");
		return message;
	}

	private static MqttMessageDeliveryHandler delivery(boolean result) {
		return new MqttMessageDeliveryHandler() {
			@Override
			public boolean handle(Message message) {
				return result;
			}

			@Override
			public int getOrder() {
				return 100;
			}
		};
	}

	/**
	 * 投递 handler 返回 false 表示实际发送失败，管线必须返回 false，
	 * 否则遗嘱重试路径会把失败当成成功并清除备份。
	 */
	@Test
	void deliveryHandlerReturningFalseFailsPipeline() {
		DefaultMqttMessagePipeline pipeline = new DefaultMqttMessagePipeline();
		pipeline.addHandler(delivery(false));
		assertFalse(pipeline.handle(downStream()));
	}

	@Test
	void deliveryHandlerReturningTrueSucceedsEvenIfLaterHandlerFails() {
		DefaultMqttMessagePipeline pipeline = new DefaultMqttMessagePipeline();
		pipeline.addHandler(delivery(true));
		pipeline.addHandler(new MqttMessageHandler() {
			@Override
			public boolean handle(Message message) {
				throw new IllegalStateException("post delivery failure");
			}

			@Override
			public int getOrder() {
				return 200;
			}
		});
		assertTrue(pipeline.handle(downStream()));
	}

	/**
	 * 投递前被非投递 handler 中断时，不能报告成功。
	 */
	@Test
	void interruptBeforeDeliveryFailsPipeline() {
		DefaultMqttMessagePipeline pipeline = new DefaultMqttMessagePipeline();
		pipeline.addHandler(message -> false);
		pipeline.addHandler(delivery(true));
		assertFalse(pipeline.handle(downStream()));
	}
}
