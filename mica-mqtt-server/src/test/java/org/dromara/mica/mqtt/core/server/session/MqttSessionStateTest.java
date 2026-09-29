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

package org.dromara.mica.mqtt.core.server.session;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link MqttSessionState} 单元测试：会话归属、持久标志、过期标记与离线队列。
 *
 * @author wcmzllx
 */
class MqttSessionStateTest {

	private static Message message(String topic) {
		Message message = new Message();
		message.setTopic(topic);
		message.setQos(1);
		message.setTimestamp(System.currentTimeMillis());
		return message;
	}

	@Test
	void testBindRecordsOwnerAndPolicy() {
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		state.bind("ctx-1", false, true, 3600);

		Assertions.assertTrue(state.isOwner("ctx-1"));
		Assertions.assertFalse(state.isOwner("ctx-2"));
		Assertions.assertTrue(state.isPersistent());
		Assertions.assertFalse(state.isCleanStart());
		Assertions.assertEquals(3600, state.getSessionExpirySeconds());
		Assertions.assertEquals("c1", state.getClientId());
	}

	@Test
	void testRebindClearsExpireMark() {
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		state.bind("ctx-1", false, true, 60);
		state.markExpiry(System.currentTimeMillis());
		Assertions.assertTrue(state.isExpiryMarked());

		// 重连重新绑定：过期标记被取消，且归属切换到新连接
		state.bind("ctx-2", false, true, 60);
		Assertions.assertFalse(state.isExpiryMarked());
		Assertions.assertTrue(state.isOwner("ctx-2"));
	}

	@Test
	void testExpiryMarkHonoursZeroExpiry() {
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		// 过期时长 0 = 不自动过期
		state.bind("ctx-1", false, true, 0);
		state.markExpiry(System.currentTimeMillis());
		Assertions.assertFalse(state.isExpiryMarked());
	}

	@Test
	void testExpiryMarkWithPositiveExpiry() {
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		state.bind("ctx-1", false, true, 2);
		// 到点时间由 SessionExpireScheduler 的 TimerTask 负责，本对象只记录「待过期」标记
		state.markExpiry(1_000_000L);
		Assertions.assertTrue(state.isExpiryMarked());
	}

	@Test
	void testOfferDropsOldestWhenFull() {
		MqttSessionState state = new MqttSessionState("c1", 2, 0L);
		state.bind("ctx-1", false, true, 60);
		Assertions.assertTrue(state.offer(message("a")));
		Assertions.assertTrue(state.offer(message("b")));
		Assertions.assertTrue(state.offer(message("c")));

		Assertions.assertEquals(2, state.queueSize());
		// 最旧的 a 被丢弃，保留 b、c
		Assertions.assertEquals("b", state.poll().getTopic());
		Assertions.assertEquals("c", state.poll().getTopic());
		Assertions.assertNull(state.poll());
	}

	@Test
	void testOfferRejectedWhenQueueDisabled() {
		MqttSessionState state = new MqttSessionState("c1", 0, 0L);
		state.bind("ctx-1", false, true, 60);
		Assertions.assertFalse(state.offer(message("a")));
		Assertions.assertEquals(0, state.queueSize());
		// 禁用状态下 pushFirst 也不应无限堆积
		state.pushFirst(message("a"));
		Assertions.assertEquals(0, state.queueSize());
	}

	@Test
	void testPollDropsTtlExpiredMessages() throws Exception {
		// TTL 50ms
		MqttSessionState state = new MqttSessionState("c1", 10, 50L);
		state.bind("ctx-1", false, true, 60);

		Message expired = message("old");
		expired.setTimestamp(System.currentTimeMillis() - 5000L);
		state.offer(expired);
		state.offer(message("fresh"));

		// 过期的被丢弃，剩余的有效消息被正常取出
		Message polled = state.poll();
		Assertions.assertNotNull(polled);
		Assertions.assertEquals("fresh", polled.getTopic());
		Assertions.assertNull(state.poll());
	}

	@Test
	void testPollWithoutTtlKeepsOldMessages() {
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		state.bind("ctx-1", false, true, 60);
		Message old = message("old");
		old.setTimestamp(System.currentTimeMillis() - 5000L);
		state.offer(old);

		Message polled = state.poll();
		Assertions.assertNotNull(polled);
		Assertions.assertEquals("old", polled.getTopic());
	}

	@Test
	void testPushFirstRestoresOrder() {
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		state.bind("ctx-1", false, true, 60);
		state.offer(message("a"));
		state.offer(message("b"));

		Message head = state.poll();
		Assertions.assertEquals("a", head.getTopic());
		// 模拟发送失败放回队首
		state.pushFirst(head);

		Assertions.assertEquals("a", state.poll().getTopic());
		Assertions.assertEquals("b", state.poll().getTopic());
	}

	@Test
	void testClearQueue() {
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		state.bind("ctx-1", false, true, 60);
		state.offer(message("a"));
		state.clearQueue();
		Assertions.assertEquals(0, state.queueSize());
	}

	@Test
	void testQueueSurvivesRebind() {
		// 持久会话的核心语义：重连不应清空离线队列
		MqttSessionState state = new MqttSessionState("c1", 10, 0L);
		state.bind("ctx-1", false, true, 60);
		state.offer(message("offline-1"));

		state.bind("ctx-2", false, true, 60);
		Assertions.assertEquals(1, state.queueSize());
		Assertions.assertEquals("offline-1", state.poll().getTopic());
	}
}
