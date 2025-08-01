package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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
		List<org.dromara.mica.mqtt.core.server.model.Subscribe> subscribeList = topicManager.getSubscriptions("client3");
		Assertions.assertFalse(subscribeList.isEmpty());
	}

	@Test
	void testRemove() {
		IMqttSessionManager topicManager = new InMemoryMqttSessionManager();
		topicManager.removeSubscribe("/sys/1/456/thing/model/down_raw", "client1");
		topicManager.removeSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client1");
		topicManager.removeSubscribe("$share/group1/sys/123/456/thing/model/down_raw", "client1");
		List<org.dromara.mica.mqtt.core.server.model.Subscribe> subscribeList = topicManager.getSubscriptions("client3");
		Assertions.assertTrue(subscribeList.isEmpty());
	}

	@Test
	void testPacketId() {
		IMqttSessionManager topicManager = new InMemoryMqttSessionManager();
		for (int i = 0; i < Short.MAX_VALUE * 3; i++) {
			int packetId = topicManager.getPacketId("client1");
			System.out.println(packetId);
		}
	}

}
