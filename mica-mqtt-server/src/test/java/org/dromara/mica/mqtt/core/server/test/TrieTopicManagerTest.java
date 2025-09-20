package org.dromara.mica.mqtt.core.server.test;

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
		topicManager.addSubscribe(topicFilter, clientId, qos);
		List<Subscribe> subscribeList = topicManager.searchSubscribe(topicName);
		return subscribeList.stream().anyMatch(subscribe -> {
			return subscribe.getClientId().equals(clientId) && subscribe.getMqttQoS() == qos;
		});
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
		topicManager.addSubscribe("test/+", "client1", 0);
		topicManager.addSubscribe("test/123", "client1", 1);
		topicManager.addSubscribe("$queue/test/123", "client1", 2);
		topicManager.addSubscribe("$share/group1/test/123", "client1", 1);
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client1");
		Assertions.assertEquals(4, subscribeList.size());
	}

	@Test
	void testRemove() {
		TrieTopicManager topicManager = new TrieTopicManager();
		topicManager.addSubscribe("test/123", "client1", 0);
		topicManager.addSubscribe("$queue/test/123", "client1", 0);
		topicManager.addSubscribe("$share/group1/test/123", "client1", 0);
		topicManager.removeSubscribe("$queue/test/123", "client1");
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client1");
		Assertions.assertEquals(2, subscribeList.size());
	}

	@Test
	void testSearch() {
		TrieTopicManager topicManager = new TrieTopicManager();
		topicManager.addSubscribe("test/+", "client1", 0);
		topicManager.addSubscribe("test/+/", "client2", 1);
		topicManager.addSubscribe("test/+/1", "client3", 1);
		topicManager.addSubscribe("$queue/test/#", "client4", 0);
		topicManager.addSubscribe("$share/group1/test/123", "client5", 0);
		List<Subscribe> subscribeList = topicManager.getSubscriptions("client1");
		Assertions.assertEquals(1, subscribeList.size());
		List<Subscribe> subscribes = topicManager.searchSubscribe("test/123");
		System.out.println(subscribes);
	}

	@Test
	void test() {
		TrieTopicManager topicManager = new TrieTopicManager();
		topicManager.addSubscribe("test/123", "client1", 1);
		topicManager.addSubscribe("test/1234", "client1", 1);
		topicManager.addSubscribe("test/1235", "client1", 1);
		topicManager.addSubscribe("test1/123", "client1", 1);
		topicManager.addSubscribe("+/123", "client1", 0);
		topicManager.addSubscribe("test/#", "client1", 1);
		topicManager.addSubscribe("/test/123", "client1", 0);
		topicManager.addSubscribe("$share/group1/test/123", "client2", 0);
		topicManager.addSubscribe("$queue/test/123", "client3", 0);

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
