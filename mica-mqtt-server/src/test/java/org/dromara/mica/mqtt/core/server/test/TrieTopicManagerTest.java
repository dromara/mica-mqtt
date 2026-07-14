package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.session.TrieTopicManager;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TrieTopicManager 测试
 *
 * @author L.cm
 */
class TrieTopicManagerTest {

	/**
	 * 方便测试
	 *
	 * @param topicFilter topicFilter
	 * @param topicName   topicName
	 * @return 是否匹配
	 */
	private static boolean match(String topicFilter, String topicName) {
		TrieTopicManager topicManager = new TrieTopicManager();
		return match(topicManager, topicFilter, topicName);
	}

	/**
	 * 方便测试
	 *
	 * @param topicFilter topicFilter
	 * @param topicName   topicName
	 * @return 是否匹配
	 */
	private static boolean match(TrieTopicManager topicManager, String topicFilter, String topicName) {
		String clientId = "client1";
		int qos = 0;
		topicManager.addSubscribe(new TopicFilter(topicFilter), clientId, qos, false, false, 0);
		List<Subscribe> subscribeList = topicManager.searchSubscribe(topicName);
		return subscribeList.stream().anyMatch(subscribe -> {
			return subscribe.getClientId().equals(clientId) && subscribe.getMqttQoS() == qos;
		});
	}

	/**
	 * 简化订阅写入：HTTP API 等无 MQTT 5.0 订阅选项的路径默认 false/0。
	 */
	private static boolean addSubscribe(TrieTopicManager topicManager, String topicFilter, String clientId, int qos) {
		return topicManager.addSubscribe(new TopicFilter(topicFilter), clientId, qos, false, false, 0);
	}

	@Test
	void testGetSubscriptions() {
		TrieTopicManager topicManager = new TrieTopicManager();
		Assertions.assertTrue(match(topicManager, "123", "123"));
		Assertions.assertTrue(match(topicManager, "/1234/", "/1234/"));
		Assertions.assertTrue(match(topicManager, "$queue/12345", "12345"));
		Assertions.assertTrue(match(topicManager, "$queue//123456", "/123456"));
		Assertions.assertTrue(match(topicManager, "$share/test/1234567", "1234567"));
		Assertions.assertTrue(match(topicManager, "$share/test//12345678/11/", "/12345678/11/"));
		List<String> subscriptions = topicManager.getSubscriptions("client1").stream()
			.map(Subscribe::getTopicFilter)
			.collect(Collectors.toList());
		Assertions.assertTrue(subscriptions.contains("123"));
		Assertions.assertTrue(subscriptions.contains("/1234/"));
		Assertions.assertTrue(subscriptions.contains("$queue/12345"));
		Assertions.assertTrue(subscriptions.contains("$queue//123456"));
		Assertions.assertTrue(subscriptions.contains("$share/test/1234567"));
		Assertions.assertTrue(subscriptions.contains("$share/test//12345678/11/"));
	}

	@Test
	void testMatch() {
		// gitee issues #I56BTC /iot/test/# 无法匹配到 /iot/test 和 /iot/test/
		Assertions.assertFalse(match("+", "/iot/test"));
		Assertions.assertFalse(match("+", "iot/test"));
		Assertions.assertFalse(match("+", "/iot/test"));
		Assertions.assertFalse(match("+", "/iot"));
		Assertions.assertFalse(match("+/test", "/iot/test"));
		Assertions.assertFalse(match("/iot/test/+/", "/iot/test/123"));

		Assertions.assertTrue(match("/iot/test/+", "/iot/test/123"));
		Assertions.assertFalse(match("/iot/test/+", "/iot/test/123/"));
		Assertions.assertTrue(match("/iot/+/test", "/iot/abc/test"));
		Assertions.assertFalse(match("/iot/+/test", "/iot/abc/test/"));
		Assertions.assertFalse(match("/iot/+/test", "/iot/abc/test1"));
		Assertions.assertTrue(match("/iot/+/+/test", "/iot/abc/123/test"));
		Assertions.assertFalse(match("/iot/+/+/test", "/iot/abc/123/test1"));
		Assertions.assertFalse(match("/iot/+/+/test", "/iot/abc/123/test/"));
		Assertions.assertTrue(match("/iot/+/+/+", "/iot/abc/123/test"));
		Assertions.assertFalse(match("/iot/+/+/+", "/iot/abc/123/test/"));
		Assertions.assertTrue(match("/iot/+/test", "/iot/a/test"));
		Assertions.assertTrue(match("/iot/+/test", "/iot/a/test"));
		Assertions.assertFalse(match("/iot/+/+/+", "/iot/a//test/"));
		Assertions.assertFalse(match("/iot/+/+/+", "/iot/a/b/c/"));
		Assertions.assertFalse(match("/iot/+/+/+", "/iot/a"));
		Assertions.assertFalse(TopicUtil.match("/iot/test/+", "/iot/test"));

		Assertions.assertTrue(match("#", "/iot/test"));
		Assertions.assertTrue(match("/iot/test/#", "/iot/test"));
		Assertions.assertTrue(match("/iot/test/#", "/iot/test/"));
		Assertions.assertTrue(match("/iot/test/#", "/iot/test/1"));
		Assertions.assertTrue(match("/iot/test/#", "/iot/test/123123/12312"));

		Assertions.assertTrue(match("/iot/test/123", "/iot/test/123"));
	}

	@Test
	void testMatchGroup() {
		Assertions.assertTrue(match("$queue/123", "123"));
		Assertions.assertFalse(match("$queue/123", "/123"));
		Assertions.assertFalse(match("$queue//123", "123"));
		Assertions.assertTrue(match("$queue//123", "/123"));

		Assertions.assertTrue(match("$share/test/123", "123"));
		Assertions.assertFalse(match("$share/test/123", "/123"));
		Assertions.assertFalse(match("$share/test//123", "123"));
		Assertions.assertTrue(match("$share/test//123", "/123"));
	}

	@Test
	void testAdd() {
		TrieTopicManager topicManager = new TrieTopicManager();
		addSubscribe(topicManager, "test/+", "client1", 0);
		addSubscribe(topicManager, "test/123", "client1", 1);
		addSubscribe(topicManager, "$queue/test/123", "client1", 2);
		addSubscribe(topicManager, "$share/group1/test/123", "client1", 1);
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client1");
		Assertions.assertEquals(4, subscribeList.size());
	}

	@Test
	void testMqtt5SubscriptionOptions() {
		TrieTopicManager topicManager = new TrieTopicManager();
		TopicFilter topicFilter = new TopicFilter("test/+");
		Assertions.assertTrue(topicManager.addSubscribe(topicFilter, "client1", 1, true, true, 1));
		Assertions.assertFalse(topicManager.addSubscribe(topicFilter, "client1", 2, false, false, 2));

		// 合并语义：QoS 取 max、noLocal/retainAsPublished 取或、retainHandling 取 max
		Subscribe subscription = topicManager.getSubscriptions("client1").get(0);
		Assertions.assertEquals(2, subscription.getMqttQoS());
		Assertions.assertTrue(subscription.isNoLocal());
		Assertions.assertTrue(subscription.isRetainAsPublished());
		Assertions.assertEquals(2, subscription.getRetainHandling());
	}

	@Test
	void testMergeOverlappingMqtt5SubscriptionOptions() {
		TrieTopicManager topicManager = new TrieTopicManager();
		topicManager.addSubscribe(new TopicFilter("test/value"), "client1", 0, true, false, 0);
		topicManager.addSubscribe(new TopicFilter("test/+"), "client1", 1, false, true, 0);

		Subscribe subscription = topicManager.searchSubscribe("test/value").get(0);
		Assertions.assertEquals(1, subscription.getMqttQoS());
		Assertions.assertFalse(subscription.isNoLocal());
		Assertions.assertTrue(subscription.isRetainAsPublished());
	}

	@Test
	void testRemove() {
		TrieTopicManager topicManager = new TrieTopicManager();
		addSubscribe(topicManager, "test/123", "client1", 0);
		addSubscribe(topicManager, "$queue/test/123", "client1", 0);
		addSubscribe(topicManager, "$share/group1/test/123", "client1", 0);
		topicManager.removeSubscribe("$queue/test/123", "client1");
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client1");
		Assertions.assertEquals(2, subscribeList.size());
	}

	@Test
	void testSearch() {
		TrieTopicManager topicManager = new TrieTopicManager();
		addSubscribe(topicManager, "test/+", "client1", 0);
		addSubscribe(topicManager, "test/+/", "client2", 1);
		addSubscribe(topicManager, "test/+/1", "client3", 1);
		addSubscribe(topicManager, "$queue/test/#", "client4", 0);
		addSubscribe(topicManager, "$share/group1/test/123", "client5", 0);
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client1");
		Assertions.assertEquals(1, subscribeList.size());
		List<Subscribe> subscribes = topicManager.searchSubscribe("test/123");
		System.out.println(subscribes);
	}

	/**
	 * MQTT 5.0 Subscription Identifier：精确订阅路径应回带 subscriptionId。
	 */
	@Test
	void testSubscriptionIdentifierExactPath() {
		TrieTopicManager topicManager = new TrieTopicManager();
		String clientId = "clientA";
		String topicFilter = "device/123/telemetry";
		int subscriptionId = 42;
		topicManager.addSubscribe(new TopicFilter(topicFilter), clientId, 1, false, false, 0, subscriptionId);
		// getSubscriptions 应携带 subscriptionId
		List<Subscribe> subs = topicManager.getSubscriptions(clientId);
		Assertions.assertEquals(1, subs.size());
		Assertions.assertEquals(subscriptionId, subs.get(0).getSubscriptionId());
		// getSubscriptionId 直接查
		Assertions.assertEquals(subscriptionId, topicManager.getSubscriptionId(topicFilter, clientId));
	}

	/**
	 * 重复订阅时，subscriptionId 应被新值覆盖。
	 */
	@Test
	void testSubscriptionIdentifierOverwrite() {
		TrieTopicManager topicManager = new TrieTopicManager();
		String clientId = "clientA";
		String topicFilter = "device/123/telemetry";
		topicManager.addSubscribe(new TopicFilter(topicFilter), clientId, 1, false, false, 0, 10);
		Assertions.assertEquals(10, topicManager.getSubscriptionId(topicFilter, clientId));
		// 客户端用新 subscriptionId 重新订阅
		topicManager.addSubscribe(new TopicFilter(topicFilter), clientId, 1, false, false, 0, 99);
		Assertions.assertEquals(99, topicManager.getSubscriptionId(topicFilter, clientId));
	}

	/**
	 * 取消订阅时，subscriptionId 存储应被清理。
	 */
	@Test
	void testSubscriptionIdentifierRemovedWithSubscription() {
		TrieTopicManager topicManager = new TrieTopicManager();
		String clientId = "clientA";
		String topicFilter = "device/123/telemetry";
		topicManager.addSubscribe(new TopicFilter(topicFilter), clientId, 1, false, false, 0, 7);
		Assertions.assertEquals(7, topicManager.getSubscriptionId(topicFilter, clientId));
		topicManager.removeSubscribe(topicFilter, clientId);
		Assertions.assertEquals(0, topicManager.getSubscriptionId(topicFilter, clientId));
	}

	/**
	 * 不带 subscriptionId 的旧版 6 参 addSubscribe 不应写入 subscriptionId 存储。
	 */
	@Test
	void testSubscriptionIdentifierBackwardCompatible() {
		TrieTopicManager topicManager = new TrieTopicManager();
		String clientId = "clientA";
		String topicFilter = "device/123/telemetry";
		// 旧版 6 参 addSubscribe（subscriptionId 默认 0）
		topicManager.addSubscribe(new TopicFilter(topicFilter), clientId, 1, false, false, 0);
		Assertions.assertEquals(0, topicManager.getSubscriptionId(topicFilter, clientId));
	}

	/**
	 * 按 clientId 批量清理订阅时，subscriptionId 存储应一并清理。
	 */
	@Test
	void testSubscriptionIdentifierRemovedOnClientDisconnect() {
		TrieTopicManager topicManager = new TrieTopicManager();
		String clientId = "clientA";
		topicManager.addSubscribe(new TopicFilter("a/b"), clientId, 0, false, false, 0, 1);
		topicManager.addSubscribe(new TopicFilter("a/c"), clientId, 0, false, false, 0, 2);
		Assertions.assertEquals(1, topicManager.getSubscriptionId("a/b", clientId));
		Assertions.assertEquals(2, topicManager.getSubscriptionId("a/c", clientId));
		// 断开连接：批量清理
		topicManager.removeSubscribe(clientId);
		Assertions.assertEquals(0, topicManager.getSubscriptionId("a/b", clientId));
		Assertions.assertEquals(0, topicManager.getSubscriptionId("a/c", clientId));
	}

	@Test
	void test() {
		TrieTopicManager topicManager = new TrieTopicManager();
		addSubscribe(topicManager, "test/123", "client1", 1);
		addSubscribe(topicManager, "test/1234", "client1", 1);
		addSubscribe(topicManager, "test/1235", "client1", 1);
		addSubscribe(topicManager, "test1/123", "client1", 1);
		addSubscribe(topicManager, "+/123", "client1", 0);
		addSubscribe(topicManager, "test/#", "client1", 1);
		addSubscribe(topicManager, "/test/123", "client1", 0);
		addSubscribe(topicManager, "$share/group1/test/123", "client2", 0);
		addSubscribe(topicManager, "$queue/test/123", "client3", 0);

		List<Subscribe> subscribeList = topicManager.searchSubscribe("test/123");
		Assertions.assertFalse(subscribeList.isEmpty());

		List<Subscribe> subscriptions = topicManager.getSubscriptions("client1");
		Assertions.assertFalse(subscriptions.isEmpty());

		topicManager.removeSubscribe("/test/123", "client1");
		topicManager.removeSubscribe("client1");
		subscriptions = topicManager.getSubscriptions("client1");
		Assertions.assertTrue(subscriptions.isEmpty());
	}

}
