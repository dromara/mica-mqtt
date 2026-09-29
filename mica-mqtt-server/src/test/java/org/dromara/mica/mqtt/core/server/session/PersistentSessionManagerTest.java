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

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryMqttSessionManager} 持久会话相关能力测试：
 * 归属绑定、离线队列、在途快照与 PUBREC 标记。
 *
 * @author wcmzllx
 */
class PersistentSessionManagerTest {

	private static Message message(String topic, int qos) {
		Message message = new Message();
		message.setTopic(topic);
		message.setQos(qos);
		message.setTimestamp(System.currentTimeMillis());
		return message;
	}

	private static MqttPendingPublish pendingPublish(int packetId) {
		MqttPublishMessage publishMessage = MqttPublishMessage.builder()
			.topicName("t")
			.payload("p".getBytes())
			.qos(MqttQoS.QOS1)
			.isDup(false)
			.retained(false)
			.messageId(packetId)
			.build();
		return new MqttPendingPublish(publishMessage, MqttQoS.QOS1);
	}

	@Test
	void testBindSessionRecordsOwnership() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", false, true, 3600);

		Assertions.assertTrue(manager.isSessionOwner("c1", "ctx-1"));
		Assertions.assertFalse(manager.isSessionOwner("c1", "ctx-2"));
		Assertions.assertTrue(manager.isPersistentSession("c1"));
		Assertions.assertEquals(3600, manager.getSessionExpiryInterval("c1"));
		Assertions.assertFalse(manager.isCleanStart("c1"));
	}

	@Test
	void testOwnershipForUnknownSessionIsPermissive() {
		// 未跟踪归属的实现按"始终归属当前连接"处理，避免破坏自定义 SessionManager
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		Assertions.assertTrue(manager.isSessionOwner("unknown", "ctx-1"));
		Assertions.assertFalse(manager.isPersistentSession("unknown"));
	}

	@Test
	void testRebindSwitchesOwnerAndSurvivesOfflineQueue() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", false, true, 3600);
		Assertions.assertTrue(manager.addOfflineMessage("c1", message("offline", 1)));

		manager.bindSession("c1", "ctx-2", false, true, 3600);

		Assertions.assertFalse(manager.isSessionOwner("c1", "ctx-1"));
		Assertions.assertTrue(manager.isSessionOwner("c1", "ctx-2"));
		Assertions.assertEquals(1, manager.getOfflineMessageCount("c1"));
	}

	@Test
	void testOfflineMessageRejectedForNonPersistentSession() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", true, false, 0);

		Assertions.assertFalse(manager.addOfflineMessage("c1", message("t", 1)));
		Assertions.assertEquals(0, manager.getOfflineMessageCount("c1"));
	}

	@Test
	void testOfflineMessageRejectedForUnboundSession() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		Assertions.assertFalse(manager.addOfflineMessage("nobody", message("t", 1)));
	}

	@Test
	void testOfflineQueueFifo() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", false, true, 60);
		manager.addOfflineMessage("c1", message("m1", 1));
		manager.addOfflineMessage("c1", message("m2", 1));
		manager.addOfflineMessage("c1", message("m3", 1));

		Assertions.assertEquals("m1", manager.pollOfflineMessage("c1").getTopic());
		Assertions.assertEquals("m2", manager.pollOfflineMessage("c1").getTopic());
		Assertions.assertEquals("m3", manager.pollOfflineMessage("c1").getTopic());
		Assertions.assertNull(manager.pollOfflineMessage("c1"));
	}

	@Test
	void testPushOfflineMessageFirstRestoresOrder() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", false, true, 60);
		manager.addOfflineMessage("c1", message("m1", 1));
		manager.addOfflineMessage("c1", message("m2", 1));

		Message head = manager.pollOfflineMessage("c1");
		manager.pushOfflineMessageFirst("c1", head);

		Assertions.assertEquals("m1", manager.pollOfflineMessage("c1").getTopic());
		Assertions.assertEquals("m2", manager.pollOfflineMessage("c1").getTopic());
	}

	@Test
	void testOfflineQueueCapacityOverflow() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager(2, 0L, 0);
		manager.bindSession("c1", "ctx-1", false, true, 60);
		manager.addOfflineMessage("c1", message("m1", 1));
		manager.addOfflineMessage("c1", message("m2", 1));
		manager.addOfflineMessage("c1", message("m3", 1));

		Assertions.assertEquals(2, manager.getOfflineMessageCount("c1"));
		Assertions.assertEquals("m2", manager.pollOfflineMessage("c1").getTopic());
	}

	@Test
	void testOfflineQueueTtl() throws Exception {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager(10, 50L, 0);
		manager.bindSession("c1", "ctx-1", false, true, 60);
		Message expired = message("old", 1);
		expired.setTimestamp(System.currentTimeMillis() - 5000L);
		manager.addOfflineMessage("c1", expired);
		manager.addOfflineMessage("c1", message("fresh", 1));

		Assertions.assertEquals("fresh", manager.pollOfflineMessage("c1").getTopic());
		Assertions.assertNull(manager.pollOfflineMessage("c1"));
	}

	@Test
	void testGetPendingPublishesReturnsSnapshot() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.addPendingPublish("c1", 1, pendingPublish(1));
		manager.addPendingPublish("c1", 2, pendingPublish(2));

		Assertions.assertEquals(2, manager.getPendingPublishes("c1").size());
		Assertions.assertEquals(2, manager.getPendingPublishCount("c1"));
		Assertions.assertTrue(manager.getPendingPublishes("nobody").isEmpty());
	}

	@Test
	void testPubRecMarkerLifecycle() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.addPendingPublish("c1", 7, pendingPublish(7));

		Assertions.assertFalse(manager.isPubRecReceived("c1", 7));
		manager.markPubRecReceived("c1", 7);
		Assertions.assertTrue(manager.isPubRecReceived("c1", 7));

		// PUBREC 标记挂在已有的 markPendingPublishPubRel 钩子上，两者等价
		manager.markPendingPublishPubRel("c1", 8);
		Assertions.assertTrue(manager.isPubRecReceived("c1", 8));

		// 在途消息被确认后标记应同步清理，避免泄漏与误判
		manager.removePendingPublish("c1", 7);
		Assertions.assertFalse(manager.isPubRecReceived("c1", 7));
		Assertions.assertEquals(0, manager.getPendingPublishCount("c1"));
	}

	@Test
	void testMaxInflightPerSessionDropsOldest() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager(1000, 0L, 2);
		manager.addPendingPublish("c1", 1, pendingPublish(1));
		manager.addPendingPublish("c1", 2, pendingPublish(2));
		manager.markPendingPublishPubRel("c1", 1);

		// 超过上限，淘汰最旧的 packetId=1，其 PUBREC 标记应同步清理
		manager.addPendingPublish("c1", 3, pendingPublish(3));

		Assertions.assertEquals(2, manager.getPendingPublishCount("c1"));
		Assertions.assertNull(manager.getPendingPublish("c1", 1));
		Assertions.assertFalse(manager.isPubRecReceived("c1", 1));
		Assertions.assertNotNull(manager.getPendingPublish("c1", 2));
		Assertions.assertNotNull(manager.getPendingPublish("c1", 3));
	}

	@Test
	void testMaxInflightUnlimitedWhenNonPositive() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager(1000, 0L, 0);
		for (int i = 1; i <= 50; i++) {
			manager.addPendingPublish("c1", i, pendingPublish(i));
		}
		Assertions.assertEquals(50, manager.getPendingPublishCount("c1"));
	}

	@Test
	void testRemoveClearsSessionStateAndOfflineQueue() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", false, true, 60);
		manager.addOfflineMessage("c1", message("m1", 1));
		manager.addPendingPublish("c1", 1, pendingPublish(1));
		manager.markPendingPublishPubRel("c1", 1);

		manager.remove("c1");

		Assertions.assertFalse(manager.hasSession("c1"));
		Assertions.assertFalse(manager.isPersistentSession("c1"));
		Assertions.assertEquals(0, manager.getOfflineMessageCount("c1"));
		Assertions.assertEquals(0, manager.getPendingPublishCount("c1"));
		Assertions.assertFalse(manager.isPubRecReceived("c1", 1));
		Assertions.assertEquals(0, manager.getSessionExpiryInterval("c1"));
		Assertions.assertTrue(manager.isCleanStart("c1"));
	}

	@Test
	void testHasSessionIncludesSessionStateOnly() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		Assertions.assertFalse(manager.hasSession("c1"));
		manager.bindSession("c1", "ctx-1", false, true, 60);
		Assertions.assertTrue(manager.hasSession("c1"));
	}

	@Test
	void testMarkSessionExpiryDoesNotRemoveState() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", false, true, 60);
		manager.markSessionExpiry("c1");

		// 标记过期不等于立即清理，会话状态仍然存在
		Assertions.assertTrue(manager.hasSession("c1"));
		Assertions.assertTrue(manager.isPersistentSession("c1"));
	}

	@Test
	void testBindSessionIgnoresBlankClientId() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession(null, "ctx-1", false, true, 60);
		manager.bindSession("", "ctx-1", false, true, 60);
		// 空 clientId 不会建立会话，离线队列也不可写入
		Assertions.assertFalse(manager.isPersistentSession(""));
		Assertions.assertFalse(manager.addOfflineMessage("", message("t", 1)));
		Assertions.assertNull(manager.pollOfflineMessage(""));
	}

	@Test
	void testCleanResetsEverything() {
		InMemoryMqttSessionManager manager = new InMemoryMqttSessionManager();
		manager.bindSession("c1", "ctx-1", false, true, 60);
		manager.addOfflineMessage("c1", message("m1", 1));
		manager.addPendingPublish("c1", 1, pendingPublish(1));
		manager.markPendingPublishPubRel("c1", 1);

		manager.clean();

		Assertions.assertFalse(manager.hasSession("c1"));
		Assertions.assertEquals(0, manager.getOfflineMessageCount("c1"));
		Assertions.assertFalse(manager.isPubRecReceived("c1", 1));
	}
}
