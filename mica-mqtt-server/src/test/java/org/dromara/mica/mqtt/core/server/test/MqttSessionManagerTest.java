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

package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MqttSessionManager 测试
 *
 * @author L.cm
 */
class MqttSessionManagerTest {

	@Test
	void testAdd() {
		IMqttSessionManager topicManager = new InMemoryMqttSessionManager();
		topicManager.addSubscribe("/sys/1/456/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/2/456/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/3/4567/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/4/45678/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/1/4561/thing/model/down_raw", "client1", 0);
		topicManager.addSubscribe("/sys/2/45612/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/+/+/thing/model/down_raw", "client1", 0);
		topicManager.addSubscribe("/sys/3/456/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/12/456/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/11/4567/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/111/45678/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/123/4561/thing/model/down_raw", "client1", 0);
		topicManager.addSubscribe("/sys/123/45612/thing/model/down_raw", "client1", 1);
		topicManager.addSubscribe("/sys/1/+/thing/model/down_raw", "client1", 0);
		topicManager.addSubscribe("/sys/1/456/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/2/456/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/3/4567/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/4/45678/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/1/4561/thing/model/down_raw", "client2", 0);
		topicManager.addSubscribe("/sys/2/45612/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/+/+/thing/model/down_raw", "client2", 0);
		topicManager.addSubscribe("/sys/3/456/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/12/456/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/11/4567/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/111/45678/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/123/4561/thing/model/down_raw", "client2", 0);
		topicManager.addSubscribe("/sys/123/45612/thing/model/down_raw", "client2", 1);
		topicManager.addSubscribe("/sys/1/+/thing/model/down_raw", "client2", 0);
		topicManager.addSubscribe("/sys/1/456/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/2/456/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/3/4567/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/4/45678/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/1/4561/thing/model/down_raw", "client3", 0);
		topicManager.addSubscribe("/sys/2/45612/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/+/+/thing/model/down_raw", "client3", 0);
		topicManager.addSubscribe("/sys/3/456/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/12/456/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/11/4567/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/111/45678/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/123/4561/thing/model/down_raw", "client3", 0);
		topicManager.addSubscribe("/sys/123/45612/thing/model/down_raw", "client3", 1);
		topicManager.addSubscribe("/sys/1/+/thing/model/down_raw", "client3", 0);
		topicManager.addSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client1", 0);
		topicManager.addSubscribe("$queue/sys/123/456/thing/model/down_raw", "client31", 0);
		topicManager.addSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client2", 0);
		topicManager.addSubscribe("$queue/sys/123/456/thing/model/down_raw", "client2", 0);
		topicManager.addSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client3", 0);
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client3");
		Assertions.assertFalse(subscribeList.isEmpty());
	}

	@Test
	void testRemove() {
		IMqttSessionManager topicManager = new InMemoryMqttSessionManager();
		// 在没有任何订阅的情况下删除订阅，应安全无异常
		topicManager.removeSubscribe("/sys/1/456/thing/model/down_raw", "client1");
		topicManager.removeSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client1");
		// 重复删除也应安全无异常
		topicManager.removeSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client1");
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client3");
		Assertions.assertTrue(subscribeList.isEmpty());
	}

	@Test
	void testPacketId() {
		IMqttSessionManager topicManager = new InMemoryMqttSessionManager();
		int first = topicManager.getPacketId("client1");
		int second = topicManager.getPacketId("client1");
		int third = topicManager.getPacketId("client1");
		// packetId 从 1 开始，并顺序递增
		Assertions.assertEquals(1, first);
		Assertions.assertEquals(2, second);
		Assertions.assertEquals(3, third);
		// 不同的 client 拥有独立的 packetId 计数器
		int client2First = topicManager.getPacketId("client2");
		Assertions.assertEquals(1, client2First);
		// 跨过 0xffff 之后应回绕到 1，避免 0 出现：
		// 当前已返回 1, 2, 3（current=4），再调用 0xffff - 3 次刚好让 current 回到 1
		int lastValue = 3;
		for (int i = 0; i < 0xffff - 3; i++) {
			lastValue = topicManager.getPacketId("client1");
		}
		Assertions.assertEquals(0xffff, lastValue);
		int overflow = topicManager.getPacketId("client1");
		Assertions.assertEquals(1, overflow);
		// 回绕后下一次为 2
		Assertions.assertEquals(2, topicManager.getPacketId("client1"));
	}

	// ----------------------------------------------------------------------------
	// 订阅存储
	// ----------------------------------------------------------------------------

	@Test
	void testGetSubscriptions() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/a/+/c", "client1", 0);
		sessionManager.addSubscribe("/a/#", "client1", 2);
		sessionManager.addSubscribe("$queue/a/b/c", "client1", 1);
		sessionManager.addSubscribe("$share/group1/a/b/c", "client1", 0);

		List<Subscribe> subscribes = sessionManager.getSubscriptions("client1");
		// 共 5 条订阅
		Assertions.assertEquals(5, subscribes.size());

		Set<String> topicFilters = subscribes.stream()
			.map(Subscribe::getTopicFilter)
			.collect(Collectors.toSet());
		Assertions.assertEquals(
			new HashSet<>(Arrays.asList("/a/b/c", "/a/+/c", "/a/#", "$queue/a/b/c", "$share/group1/a/b/c")),
			topicFilters
		);

		// 不存在的 client 返回空列表
		Assertions.assertTrue(sessionManager.getSubscriptions("not_exists").isEmpty());
	}

	@Test
	void testGetSubscriptionsMultiClient() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/a/+/c", "client2", 0);
		sessionManager.addSubscribe("/a/#", "client3", 2);

		Assertions.assertEquals(1, sessionManager.getSubscriptions("client1").size());
		Assertions.assertEquals(1, sessionManager.getSubscriptions("client2").size());
		Assertions.assertEquals(1, sessionManager.getSubscriptions("client3").size());
		Assertions.assertTrue(sessionManager.getSubscriptions("client4").isEmpty());
	}

	// ----------------------------------------------------------------------------
	// searchSubscribe(topicName) - 命中多 client
	// ----------------------------------------------------------------------------

	@Test
	void testSearchSubscribeExact() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/a/b/c", "client2", 2);
		sessionManager.addSubscribe("/a/b/d", "client3", 0);

		List<Subscribe> matched = sessionManager.searchSubscribe("/a/b/c");
		Set<String> clientIds = matched.stream().map(Subscribe::getClientId).collect(Collectors.toSet());
		Assertions.assertEquals(2, matched.size());
		Assertions.assertTrue(clientIds.contains("client1"));
		Assertions.assertTrue(clientIds.contains("client2"));
		Assertions.assertFalse(clientIds.contains("client3"));

		// 精确订阅 QoS 应为注册时设置的值
		for (Subscribe subscribe : matched) {
			if ("client1".equals(subscribe.getClientId())) {
				Assertions.assertEquals(1, subscribe.getMqttQoS());
			} else if ("client2".equals(subscribe.getClientId())) {
				Assertions.assertEquals(2, subscribe.getMqttQoS());
			}
		}
	}

	@Test
	void testSearchSubscribeWildcardPlus() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/+/c", "client1", 1);
		sessionManager.addSubscribe("/a/+/d", "client2", 0);
		sessionManager.addSubscribe("/a/b/c", "client3", 2);

		List<Subscribe> matched = sessionManager.searchSubscribe("/a/b/c");
		Set<String> clientIds = matched.stream().map(Subscribe::getClientId).collect(Collectors.toSet());
		// /a/+/c 命中 client1，/a/b/c 精确命中 client3
		Assertions.assertEquals(2, matched.size());
		Assertions.assertTrue(clientIds.contains("client1"));
		Assertions.assertTrue(clientIds.contains("client3"));
	}

	@Test
	void testSearchSubscribeWildcardHash() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/#", "client1", 1);
		sessionManager.addSubscribe("/a/b/#", "client2", 0);
		sessionManager.addSubscribe("/b/#", "client3", 2);

		List<Subscribe> matched = sessionManager.searchSubscribe("/a/b/c/d");
		Set<String> clientIds = matched.stream().map(Subscribe::getClientId).collect(Collectors.toSet());
		// client1 订阅了 /a/#，client2 订阅了 /a/b/#
		Assertions.assertEquals(2, matched.size());
		Assertions.assertTrue(clientIds.contains("client1"));
		Assertions.assertTrue(clientIds.contains("client2"));
		Assertions.assertFalse(clientIds.contains("client3"));
	}

	@Test
	void testSearchSubscribeShared() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		// 共享订阅：filter 去掉 $share/{group}/ 前缀后再写入 trie，
		// 因此发布时 topicName 应与去掉前缀后的部分保持一致（不含前导 /）
		sessionManager.addSubscribe("$share/group1/a/b/c", "client1", 1);
		sessionManager.addSubscribe("$share/group1/a/b/c", "client2", 2);
		sessionManager.addSubscribe("$share/group2/a/b/c", "client3", 0);
		// 普通订阅也会同时命中
		sessionManager.addSubscribe("a/b/c", "client4", 1);

		// 共享订阅：每组内只投递给一个 client
		List<Subscribe> matched = sessionManager.searchSubscribe("a/b/c");
		Set<String> clientIds = matched.stream().map(Subscribe::getClientId).collect(Collectors.toSet());
		// 至少包含 client4，以及 group1 中的一个（client1 或 client2）
		Assertions.assertTrue(clientIds.contains("client4"));
		boolean group1Hit = clientIds.contains("client1") || clientIds.contains("client2");
		Assertions.assertTrue(group1Hit, "group1 中应至少有一个 client 命中");
		// 共享订阅语义下，单个 group 只会选出一个 client
		boolean bothGroup1Hit = clientIds.contains("client1") && clientIds.contains("client2");
		Assertions.assertFalse(bothGroup1Hit, "共享订阅同一 group 不应同时命中多个 client");
	}

	@Test
	void testSearchSubscribeQueue() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("$queue/a/b/c", "client1", 1);
		sessionManager.addSubscribe("$queue/a/b/c", "client2", 2);
		sessionManager.addSubscribe("$queue/a/b/d", "client3", 0);

		// $queue 前缀同样会被剥离，topicName 需与去掉前缀后的部分一致
		List<Subscribe> matched = sessionManager.searchSubscribe("a/b/c");
		Assertions.assertEquals(1, matched.size());
		String hitClient = matched.get(0).getClientId();
		Assertions.assertTrue("client1".equals(hitClient) || "client2".equals(hitClient),
			"$queue 共享订阅应只投递给一个 client");
	}

	@Test
	void testSearchSubscribeNoMatch() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/x/+/y", "client2", 0);

		// 没有匹配项
		Assertions.assertTrue(sessionManager.searchSubscribe("/no/match").isEmpty());
	}

	// ----------------------------------------------------------------------------
	// searchSubscribe(topicName, clientId) - 单 client 单 topic
	// ----------------------------------------------------------------------------

	@Test
	void testSearchSubscribeByClient() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/a/+/c", "client1", 0);
		sessionManager.addSubscribe("/a/#", "client1", 2);
		sessionManager.addSubscribe("/a/b/c", "client2", 0);

		// 同一 client 命中多个 filter 时，应取最大 QoS
		Byte qos = sessionManager.searchSubscribe("/a/b/c", "client1");
		Assertions.assertNotNull(qos);
		Assertions.assertEquals(2, qos.byteValue());

		// 仅 client2 命中精确订阅
		qos = sessionManager.searchSubscribe("/a/b/c", "client2");
		Assertions.assertNotNull(qos);
		Assertions.assertEquals(0, qos.byteValue());
	}

	@Test
	void testSearchSubscribeByClientNoMatch() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		// 没有匹配项
		Assertions.assertNull(sessionManager.searchSubscribe("/no/match", "client1"));
		// client 没有匹配项
		Assertions.assertNull(sessionManager.searchSubscribe("/a/b/c", "no_such_client"));
	}

	// ----------------------------------------------------------------------------
	// QoS 合并与 noLocal
	// ----------------------------------------------------------------------------

	@Test
	void testAddSubscribeQosMaxMerge() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		// 同一 client 同一 topic 多次订阅，QoS 取较大值
		sessionManager.addSubscribe("/a/b/c", "client1", 0);
		sessionManager.addSubscribe("/a/b/c", "client1", 2);
		sessionManager.addSubscribe("/a/b/c", "client1", 1);

		List<Subscribe> subscribes = sessionManager.getSubscriptions("client1");
		Assertions.assertEquals(1, subscribes.size());
		Assertions.assertEquals(2, subscribes.get(0).getMqttQoS());

		// searchSubscribe 也应返回最大 QoS
		Assertions.assertEquals(2, sessionManager.searchSubscribe("/a/b/c", "client1").byteValue());
	}

	@Test
	void testAddSubscribeNoLocal() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe(new TopicFilter("/a/b/c"), "client1", 1, true);
		sessionManager.addSubscribe(new TopicFilter("/a/+/c"), "client2", 0, false);

		// noLocal 应在 getSubscriptions 中正确保留
		List<Subscribe> subscribes = sessionManager.getSubscriptions("client1");
		Assertions.assertEquals(1, subscribes.size());
		Assertions.assertTrue(subscribes.get(0).isNoLocal());

		subscribes = sessionManager.getSubscriptions("client2");
		Assertions.assertEquals(1, subscribes.size());
		Assertions.assertFalse(subscribes.get(0).isNoLocal());
	}

	@Test
	void testAddSubscribeNoLocalMerge() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		// 同一 client 同一 topic 重复订阅，noLocal 取 OR
		sessionManager.addSubscribe(new TopicFilter("/a/b/c"), "client1", 0, false);
		sessionManager.addSubscribe(new TopicFilter("/a/b/c"), "client1", 1, true);

		List<Subscribe> subscribes = sessionManager.getSubscriptions("client1");
		Assertions.assertEquals(1, subscribes.size());
		// QoS 取较大值（1）
		Assertions.assertEquals(1, subscribes.get(0).getMqttQoS());
		// noLocal 取或
		Assertions.assertTrue(subscribes.get(0).isNoLocal());
	}

	@Test
	void testSearchSubscribeQosMerge() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		// 精确订阅 + 通配订阅都应参与 QoS 合并
		sessionManager.addSubscribe("/a/b/c", "client1", 0);
		sessionManager.addSubscribe("/a/+/c", "client1", 2);
		sessionManager.addSubscribe("/a/#", "client1", 1);

		// 应取最大 QoS = 2
		Byte qos = sessionManager.searchSubscribe("/a/b/c", "client1");
		Assertions.assertNotNull(qos);
		Assertions.assertEquals(2, qos.byteValue());
	}

	// ----------------------------------------------------------------------------
	// 订阅删除
	// ----------------------------------------------------------------------------

	@Test
	void testRemoveSubscribeByTopic() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/a/+/c", "client1", 0);
		sessionManager.addSubscribe("/a/#", "client1", 2);

		Assertions.assertEquals(3, sessionManager.getSubscriptions("client1").size());

		// 删除精确订阅
		sessionManager.removeSubscribe("/a/b/c", "client1");
		List<Subscribe> subscribes = sessionManager.getSubscriptions("client1");
		Assertions.assertEquals(2, subscribes.size());
		Assertions.assertTrue(subscribes.stream().noneMatch(s -> "/a/b/c".equals(s.getTopicFilter())));

		// 删除通配订阅
		sessionManager.removeSubscribe("/a/+/c", "client1");
		Assertions.assertEquals(1, sessionManager.getSubscriptions("client1").size());

		// 删除最后一个
		sessionManager.removeSubscribe("/a/#", "client1");
		Assertions.assertTrue(sessionManager.getSubscriptions("client1").isEmpty());
	}

	@Test
	void testRemoveSubscribeByClient() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/a/+/c", "client1", 0);
		sessionManager.addSubscribe("$queue/a/b/c", "client1", 1);
		sessionManager.addSubscribe("$share/group1/a/b/c", "client1", 0);
		// 别的 client 的订阅不应被影响
		sessionManager.addSubscribe("/a/b/c", "client2", 1);

		// 模拟 client1 断开连接，清除其全部订阅
		sessionManager.remove("client1");

		Assertions.assertTrue(sessionManager.getSubscriptions("client1").isEmpty());
		// client2 的订阅未被影响
		Assertions.assertEquals(1, sessionManager.getSubscriptions("client2").size());
	}

	@Test
	void testRemoveSubscribeIdempotent() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.removeSubscribe("/a/b/c", "client1");
		// 重复删除不应抛异常
		sessionManager.removeSubscribe("/a/b/c", "client1");
		Assertions.assertTrue(sessionManager.getSubscriptions("client1").isEmpty());
	}

	// ----------------------------------------------------------------------------
	// Pending Publish (QoS 1 / QoS 2)
	// ----------------------------------------------------------------------------

	@Test
	void testPendingPublish() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		MqttPublishMessage message = createPublishMessage("/test/topic", 1, MqttQoS.QOS1);
		MqttPendingPublish pending = new MqttPendingPublish(message, MqttQoS.QOS1);

		// 不存在的 client
		Assertions.assertNull(sessionManager.getPendingPublish("client1", 1));

		sessionManager.addPendingPublish("client1", 1, pending);
		Assertions.assertSame(pending, sessionManager.getPendingPublish("client1", 1));
		Assertions.assertSame(pending, sessionManager.getPendingPublish("client1", 1));

		// 不同 messageId 互不影响
		sessionManager.addPendingPublish("client1", 2, pending);
		Assertions.assertSame(pending, sessionManager.getPendingPublish("client1", 2));

		// 删除
		sessionManager.removePendingPublish("client1", 1);
		Assertions.assertNull(sessionManager.getPendingPublish("client1", 1));
		// 删除不存在的 messageId 不应抛异常
		sessionManager.removePendingPublish("client1", 999);
		// 删除不存在的 client 不应抛异常
		sessionManager.removePendingPublish("not_exists", 1);
	}

	@Test
	void testPendingPublishCount() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		Assertions.assertEquals(0, sessionManager.getPendingPublishCount("client1"));
		sessionManager.addPendingPublish("client1", 1, new MqttPendingPublish(
			createPublishMessage("/test/topic", 1, MqttQoS.QOS1), MqttQoS.QOS1));
		sessionManager.addPendingPublish("client1", 2, new MqttPendingPublish(
			createPublishMessage("/test/topic", 2, MqttQoS.QOS2), MqttQoS.QOS2));
		Assertions.assertEquals(2, sessionManager.getPendingPublishCount("client1"));
		sessionManager.removePendingPublish("client1", 1);
		Assertions.assertEquals(1, sessionManager.getPendingPublishCount("client1"));
	}

	@Test
	void testClientReceiveMaximum() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		Assertions.assertEquals(IMqttSessionManager.MQTT5_DEFAULT_RECEIVE_MAXIMUM, sessionManager.getClientReceiveMaximum("client1"));
		sessionManager.setClientReceiveMaximum("client1", 10);
		Assertions.assertEquals(10, sessionManager.getClientReceiveMaximum("client1"));
		sessionManager.remove("client1");
		Assertions.assertEquals(IMqttSessionManager.MQTT5_DEFAULT_RECEIVE_MAXIMUM, sessionManager.getClientReceiveMaximum("client1"));
	}

	// ----------------- PR7（P1.7）PublishBacklog 行为 -----------------

	@Test
	void testPendingPublishBacklogEnqueueAndPoll() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		String clientId = "client1";
		org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry e1 =
			new org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry(
				"a/b", "p1".getBytes(), MqttQoS.QOS1, 0, false, null);
		org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry e2 =
			new org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry(
				"a/c", "p2".getBytes(), MqttQoS.QOS1, 0, false, null);
		sessionManager.addPendingPublishBacklog(clientId, e1);
		sessionManager.addPendingPublishBacklog(clientId, e2);
		// FIFO 出队
		Assertions.assertEquals(2, sessionManager.getPendingPublishBacklogSize(clientId));
		Assertions.assertEquals("a/b", sessionManager.pollPendingPublishBacklog(clientId).getTopic());
		Assertions.assertEquals("a/c", sessionManager.pollPendingPublishBacklog(clientId).getTopic());
		Assertions.assertNull(sessionManager.pollPendingPublishBacklog(clientId));
		// 队列空时安全清除 map entry
		Assertions.assertEquals(0, sessionManager.getPendingPublishBacklogSize(clientId));
	}

	@Test
	void testPendingPublishBacklogRemovedOnClientRemove() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		String clientId = "client1";
		sessionManager.addPendingPublishBacklog(clientId,
			new org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry(
				"a/b", new byte[0], MqttQoS.QOS1, 0, false, null));
		Assertions.assertEquals(1, sessionManager.getPendingPublishBacklogSize(clientId));
		sessionManager.remove(clientId);
		Assertions.assertEquals(0, sessionManager.getPendingPublishBacklogSize(clientId));
	}

	@Test
	void testPendingPublishBacklogEmptyDefaultBehavior() {
		// 未注册时直接 poll 应返回 null 而不抛异常
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		Assertions.assertNull(sessionManager.pollPendingPublishBacklog("not-exist"));
		Assertions.assertEquals(0, sessionManager.getPendingPublishBacklogSize("not-exist"));
	}

	// ----------------- PR9（P2.8）Session Expiry Interval 行为 -----------------

	@Test
	void testSessionExpiryIntervalDefaultValues() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		// 未设置时缺省为 0 / true
		Assertions.assertEquals(0, sessionManager.getSessionExpiryInterval("client1"));
		Assertions.assertTrue(sessionManager.isCleanStart("client1"));
	}

	@Test
	void testSessionExpiryIntervalSetAndGet() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.setSessionExpiryInterval("client1", 3600, false);
		Assertions.assertEquals(3600, sessionManager.getSessionExpiryInterval("client1"));
		Assertions.assertFalse(sessionManager.isCleanStart("client1"));
	}

	@Test
	void testSessionExpiryIntervalOverwrite() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.setSessionExpiryInterval("client1", 60, false);
		// 客户端重新连接时覆盖
		sessionManager.setSessionExpiryInterval("client1", 7200, true);
		Assertions.assertEquals(7200, sessionManager.getSessionExpiryInterval("client1"));
		Assertions.assertTrue(sessionManager.isCleanStart("client1"));
	}

	@Test
	void testSessionExpiryIntervalClearedOnRemove() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.setSessionExpiryInterval("client1", 3600, false);
		Assertions.assertEquals(3600, sessionManager.getSessionExpiryInterval("client1"));
		sessionManager.remove("client1");
		// remove 后回到缺省值
		Assertions.assertEquals(0, sessionManager.getSessionExpiryInterval("client1"));
		Assertions.assertTrue(sessionManager.isCleanStart("client1"));
	}

	@Test
	void testSessionExpiryIntervalZeroMeansImmediate() {
		// spec 3.1.2.11.4: sessionExpiryInterval == 0 表示立即过期
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.setSessionExpiryInterval("client1", 0, true);
		Assertions.assertEquals(0, sessionManager.getSessionExpiryInterval("client1"));
	}

	@Test
	void testSessionExpiryIntervalNullClientIdIsNoop() {
		// 防御性：null clientId 不应抛 NPE
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		Assertions.assertDoesNotThrow(() ->
			sessionManager.setSessionExpiryInterval(null, 60, false));
		Assertions.assertDoesNotThrow(() ->
			sessionManager.setSessionExpiryInterval("", 60, false));
	}

	@Test
	void testPendingQos2Publish() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		MqttPublishMessage message = createPublishMessage("/test/topic", 1, MqttQoS.QOS2);
		MqttMessage pubRecMessage = createMqttMessage(MqttMessageType.PUBREC, MqttQoS.QOS1);
		MqttPendingQos2Publish pending = new MqttPendingQos2Publish(message, pubRecMessage);

		// 不存在的 client
		Assertions.assertNull(sessionManager.getPendingQos2Publish("client1", 1));

		sessionManager.addPendingQos2Publish("client1", 1, pending);
		Assertions.assertSame(pending, sessionManager.getPendingQos2Publish("client1", 1));

		sessionManager.addPendingQos2Publish("client1", 2, pending);
		Assertions.assertSame(pending, sessionManager.getPendingQos2Publish("client1", 2));

		sessionManager.removePendingQos2Publish("client1", 1);
		Assertions.assertNull(sessionManager.getPendingQos2Publish("client1", 1));
		// 删除不存在的 messageId 不应抛异常
		sessionManager.removePendingQos2Publish("client1", 999);
		// 删除不存在的 client 不应抛异常
		sessionManager.removePendingQos2Publish("not_exists", 1);
	}

	@Test
	void testRemoveClearsPending() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		MqttPublishMessage qos1Message = createPublishMessage("/test/topic", 1, MqttQoS.QOS1);
		MqttPublishMessage qos2Message = createPublishMessage("/test/topic", 1, MqttQoS.QOS2);
		MqttMessage pubRecMessage = createMqttMessage(MqttMessageType.PUBREC, MqttQoS.QOS1);

		sessionManager.addPendingPublish("client1", 1, new MqttPendingPublish(qos1Message, MqttQoS.QOS1));
		sessionManager.addPendingQos2Publish("client1", 1, new MqttPendingQos2Publish(qos2Message, pubRecMessage));
		// 触发 packetId 计数
		sessionManager.getPacketId("client1");

		sessionManager.remove("client1");
		// 移除后所有数据应被清空
		Assertions.assertNull(sessionManager.getPendingPublish("client1", 1));
		Assertions.assertNull(sessionManager.getPendingQos2Publish("client1", 1));
		// remove 也会清空 packetId 计数器
		Assertions.assertEquals(1, sessionManager.getPacketId("client1"));
	}

	// ----------------------------------------------------------------------------
	// session 存在与清理
	// ----------------------------------------------------------------------------

	@Test
	void testHasSession() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		String clientId = "client1";

		// 全新 session 不存在
		Assertions.assertFalse(sessionManager.hasSession(clientId));

		// 有订阅则存在
		sessionManager.addSubscribe("/a/b/c", clientId, 1);
		Assertions.assertTrue(sessionManager.hasSession(clientId));

		// 清除订阅后不存在
		sessionManager.remove(clientId);
		Assertions.assertFalse(sessionManager.hasSession(clientId));

		// 有 QoS 1 pending publish 则存在
		sessionManager.addPendingPublish(clientId, 1, new MqttPendingPublish(
			createPublishMessage("/a/b/c", 1, MqttQoS.QOS1), MqttQoS.QOS1));
		Assertions.assertTrue(sessionManager.hasSession(clientId));
		sessionManager.remove(clientId);

		// 有 QoS 2 pending publish 则存在
		sessionManager.addPendingQos2Publish(clientId, 1, new MqttPendingQos2Publish(
			createPublishMessage("/a/b/c", 1, MqttQoS.QOS2),
			createMqttMessage(MqttMessageType.PUBREC, MqttQoS.QOS1)));
		Assertions.assertTrue(sessionManager.hasSession(clientId));
		sessionManager.remove(clientId);

		// 分配过 packetId 则存在
		sessionManager.getPacketId(clientId);
		Assertions.assertTrue(sessionManager.hasSession(clientId));
	}

	@Test
	void testClean() {
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		sessionManager.addSubscribe("/a/+/c", "client2", 0);
		sessionManager.addPendingPublish("client1", 1, new MqttPendingPublish(
			createPublishMessage("/a/b/c", 1, MqttQoS.QOS1), MqttQoS.QOS1));
		sessionManager.getPacketId("client1");

		Assertions.assertFalse(sessionManager.getSubscriptions("client1").isEmpty());
		Assertions.assertNotNull(sessionManager.getPendingPublish("client1", 1));

		// clean 后所有数据被清空
		sessionManager.clean();
		Assertions.assertTrue(sessionManager.getSubscriptions("client1").isEmpty());
		Assertions.assertTrue(sessionManager.getSubscriptions("client2").isEmpty());
		Assertions.assertNull(sessionManager.getPendingPublish("client1", 1));
		Assertions.assertFalse(sessionManager.hasSession("client1"));
	}

	@Test
	void testGetSubscriptionsReturnsArrayList() {
		// 记录 getSubscriptions 当前实现返回 ArrayList，方便后续重构感知行为变化
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe("/a/b/c", "client1", 1);
		List<Subscribe> subscribes = sessionManager.getSubscriptions("client1");
		Assertions.assertEquals(1, subscribes.size());
		Assertions.assertTrue(subscribes instanceof ArrayList);
	}

	// ----------------- PR10 回归：Subscription Identifier 透传 -----------------

	@Test
	void testSubscriptionIdentifierPropagatesThroughSessionManager() {
		// 回归测试：InMemoryMqttSessionManager 7 参 addSubscribe 必须把 subscriptionId 透传到 topicManager
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		String clientId = "clientA";
		String topicFilter = "device/123/telemetry";
		int subscriptionId = 42;
		// 7 参重载：MQTT 5 SUBSCRIBE 调用
		sessionManager.addSubscribe(new TopicFilter(topicFilter), clientId, 1,
			false, false, 0, subscriptionId);
		// 通过 sessionManager.getSubscriptions 获取的 Subscribe 实体应包含 subscriptionId
		List<Subscribe> subs = sessionManager.getSubscriptions(clientId);
		Assertions.assertEquals(1, subs.size());
		Assertions.assertEquals(subscriptionId, subs.get(0).getSubscriptionId(),
			"subscriptionId must propagate from InMemoryMqttSessionManager to TrieTopicManager");
	}

	@Test
	void testSubscriptionIdentifierZeroDoesNotRegister() {
		// subscriptionId = 0 表示未设置，不应在 Subscribe 实体中出现
		IMqttSessionManager sessionManager = new InMemoryMqttSessionManager();
		sessionManager.addSubscribe(new TopicFilter("a/b/c"), "client1", 1,
			false, false, 0, 0);
		List<Subscribe> subs = sessionManager.getSubscriptions("client1");
		Assertions.assertEquals(1, subs.size());
		Assertions.assertEquals(0, subs.get(0).getSubscriptionId());
	}

	// ----------------------------------------------------------------------------
	// helpers
	// ----------------------------------------------------------------------------

	/**
	 * 构造一个简单的 MqttPublishMessage
	 */
	private static MqttPublishMessage createPublishMessage(String topic, int packetId, MqttQoS qos) {
		MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, false, 0);
		MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topic, packetId);
		return new MqttPublishMessage(fixedHeader, variableHeader, "payload".getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 构造一个普通的 MqttMessage（用于 PUBREC 等）
	 */
	private static MqttMessage createMqttMessage(MqttMessageType messageType, MqttQoS qos) {
		MqttFixedHeader fixedHeader = new MqttFixedHeader(messageType, false, qos, false, 0);
		return new MqttMessage(fixedHeader);
	}
}
