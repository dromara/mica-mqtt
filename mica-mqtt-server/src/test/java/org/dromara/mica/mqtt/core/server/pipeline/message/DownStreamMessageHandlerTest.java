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

package org.dromara.mica.mqtt.core.server.pipeline.message;

import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class DownStreamMessageHandlerTest {

	private static Message message(String topic) {
		Message message = new Message();
		message.setMessageType(MessageType.DOWN_STREAM);
		message.setTopic(topic);
		message.setQos(0);
		return message;
	}

	/**
	 * 没有订阅者属于正常空操作，不得触发遗嘱重试。
	 */
	@Test
	void noSubscriberIsNormalNoop() {
		MqttServer server = MqttServer.create().build();
		DownStreamMessageHandler handler = new DownStreamMessageHandler(server);
		assertTrue(handler.handle(message("no/subscriber")));
	}

	/**
	 * 有订阅者但没有任何连接可投递时必须返回失败，
	 * 否则遗嘱会被误判为已投递并清除备份。
	 */
	@Test
	void subscriberWithoutChannelIsDeliveryFailure() {
		MqttServer server = MqttServer.create().build();
		server.getServerCreator().getSessionManager()
			.addSubscribe(new TopicFilter("ghost/topic"), "ghost", 0, false);
		DownStreamMessageHandler handler = new DownStreamMessageHandler(server);
		assertFalse(handler.handle(message("ghost/topic")));
	}

	/**
	 * 指定 clientId 但该客户端未订阅时也是正常空操作。
	 */
	@Test
	void targetedClientWithoutSubscriptionIsNormalNoop() {
		MqttServer server = MqttServer.create().build();
		DownStreamMessageHandler handler = new DownStreamMessageHandler(server);
		Message message = message("target/topic");
		message.setClientId("target");
		assertTrue(handler.handle(message));
	}

	/**
	 * 遗嘱的 retain 标志必须在 fan-out 投递前写入保留存储，
	 * 即使当前没有订阅者也不能丢失。
	 */
	@Test
	void retainedWillIsStoredEvenWithoutSubscribers() {
		MqttServer server = MqttServer.create().build();
		DownStreamMessageHandler handler = new DownStreamMessageHandler(server);
		Message will = message("retained/will");
		will.setRetain(true);
		will.setPayload(new byte[]{1, 2, 3});
		assertTrue(handler.handle(will));
		assertEquals(1, server.getServerCreator().getMessageStore().getRetainMessage("retained/#").size());
	}
}
