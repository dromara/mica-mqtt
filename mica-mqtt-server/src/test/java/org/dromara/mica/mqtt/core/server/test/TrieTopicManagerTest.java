package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.core.server.session.TrieTopicManager;
import org.tio.utils.thread.ThreadUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TrieTopicManager 测试
 *
 * @author L.cm
 */
class TrieTopicManagerTest {

	public static TrieTopicManager test1() {
		TrieTopicManager topicManager = new TrieTopicManager();
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
		long startTime = System.nanoTime();
		for (int i = 0; i < 100000; i++) {
			topicManager.searchSubscribe("/sys/123/456/thing/model/down_raw");
		}
		long costTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
		System.out.println("test1\t" + costTime);
		return topicManager;
	}

	public static void testTime() {
		for (int i = 0; i < 100; i++) {
			test1();
		}
	}

	public static void testMem() {
		TrieTopicManager topicManager = new TrieTopicManager();
		long startTime = System.nanoTime();
		for (int i = 0; i < 10000; i++) {
			for (int j = 0; j < 100; j++) {
				topicManager.addSubscribe("/sys/1/" + i + "/" + j, "client" + i, 1);
			}
		}
		long costTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
		System.out.println("testMem\t" + costTime);
		System.out.println(topicManager);
	}

	public static void testConcurrent() {
		TrieTopicManager topicManager = new TrieTopicManager();
		ExecutorService executor = ThreadUtils.getBizExecutor(10);
		for (int i = 0; i < 10000; i++) {
			for (int j = 0; j < 10; j++) {
				String topic = "/sys/1/" + i + "/" + j;
				String client = "client" + i;
				executor.execute(() -> {
					topicManager.addSubscribe(topic, client, 2);
				});
			}
		}
		ThreadUtils.sleep(8000);
		System.out.println(topicManager);
	}

	public static void main(String[] args) {
		testTime();
		testMem();
		testConcurrent();
	}

}
