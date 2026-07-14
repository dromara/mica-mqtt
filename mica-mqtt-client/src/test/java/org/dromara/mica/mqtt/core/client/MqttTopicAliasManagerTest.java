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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR10：MQTT 5.0 客户端 Topic Alias 自动维护单元测试（spec 3.3.2.3.4）。
 *
 * <p>覆盖：
 * <ul>
 *     <li>首次发送：保留 topic、注册新 alias</li>
 *     <li>二次发送：替换 topic 为空串、复用 alias</li>
 *     <li>业务方显式设置 alias：同步映射</li>
 *     <li>达到 maxAlias 上限：不再分配新 alias，保留 topic</li>
 *     <li>registerAlias / unregister / clear / size / getAlias / getTopic API</li>
 *     <li>无效参数：null/空 topic 或 alias < 1 不抛 NPE</li>
 *     <li>并发场景：多线程 register + apply 不抛异常</li>
 * </ul>
 *
 * @author L.cm
 */
class MqttTopicAliasManagerTest {

	@Test
	void firstPublishRegistersNewAlias() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		MqttPublishBuilder builder = newBuilder("sensors/temperature");
		MqttProperties properties = new MqttProperties();

		boolean usedAlias = manager.apply(builder, properties);

		assertEquals(false, usedAlias, "First publish should keep topic and not use alias.");
		assertEquals(1, manager.size());
		Integer alias = manager.getAlias("sensors/temperature");
		assertNotNull(alias);
		assertTrue(alias >= 1 && alias <= 16);
	}

	@Test
	void secondPublishReplacesTopicWithAlias() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		// 第一次
		manager.apply(newBuilder("sensors/temperature"), new MqttProperties());
		// 第二次
		MqttPublishBuilder builder2 = newBuilder("sensors/temperature");
		MqttProperties properties2 = new MqttProperties();
		boolean usedAlias = manager.apply(builder2, properties2);

		assertEquals(true, usedAlias, "Second publish should use alias.");
		assertEquals("", builder2.getTopicName());
		Integer alias = properties2.getPropertyValue(MqttPropertyType.TOPIC_ALIAS);
		assertNotNull(alias);
		assertEquals(manager.getAlias("sensors/temperature"), alias);
	}

	@Test
	void explicitAliasRespected() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		MqttPublishBuilder builder = newBuilder("custom/topic");
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS, 7));

		manager.apply(builder, properties);

		// alias 同步到映射表
		assertEquals(Integer.valueOf(7), manager.getAlias("custom/topic"));
		assertEquals("custom/topic", manager.getTopic(7));
	}

	@Test
	void aliasZeroIgnored() {
		// Topic Alias = 0 表示"未使用 alias"，不应触发别名逻辑
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		MqttPublishBuilder builder = newBuilder("zero/alias/topic");
		MqttProperties properties = new MqttProperties();
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS, 0));

		manager.apply(builder, properties);

		assertEquals("zero/alias/topic", builder.getTopicName());
		assertEquals(0, manager.size());
	}

	@Test
	void registerAliasByBusinessLogic() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		manager.registerAlias("a/b/c", 5);
		manager.registerAlias("d/e/f", 8);

		assertEquals(2, manager.size());
		assertEquals(Integer.valueOf(5), manager.getAlias("a/b/c"));
		assertEquals(Integer.valueOf(8), manager.getAlias("d/e/f"));
		assertEquals("a/b/c", manager.getTopic(5));
		assertEquals("d/e/f", manager.getTopic(8));
	}

	@Test
	void registerAliasReplacesOldMapping() {
		// 把 alias 5 已分配给 a/b/c，现在分配给 d/e/f → 旧映射应被清理
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		manager.registerAlias("a/b/c", 5);
		manager.registerAlias("d/e/f", 5);

		assertEquals(1, manager.size());
		assertNull(manager.getAlias("a/b/c"));
		assertEquals(Integer.valueOf(5), manager.getAlias("d/e/f"));
	}

	@Test
	void unregisterRemovesMapping() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		manager.registerAlias("a/b/c", 5);
		assertEquals(1, manager.size());
		manager.unregister("a/b/c");
		assertEquals(0, manager.size());
		assertNull(manager.getAlias("a/b/c"));
		assertNull(manager.getTopic(5));
	}

	@Test
	void clearRemovesAll() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		manager.registerAlias("a", 1);
		manager.registerAlias("b", 2);
		manager.registerAlias("c", 3);
		manager.clear();
		assertEquals(0, manager.size());
	}

	@Test
	void maxAliasLimitRespected() {
		// 限制 alias 上限 2：第 3 个 topic 不分配新 alias
		MqttTopicAliasManager manager = new MqttTopicAliasManager(2);
		manager.apply(newBuilder("topic/a"), new MqttProperties());
		manager.apply(newBuilder("topic/b"), new MqttProperties());
		// 第 3 个
		MqttPublishBuilder builder3 = newBuilder("topic/c");
		MqttProperties properties3 = new MqttProperties();
		boolean usedAlias = manager.apply(builder3, properties3);

		assertEquals(false, usedAlias, "Beyond max alias: keep topic.");
		assertEquals("topic/c", builder3.getTopicName());
		assertEquals(2, manager.size());
	}

	@Test
	void nullTopicIsNoop() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		MqttPublishBuilder builder = MqttPublishMessage.builder().qos(MqttQoS.QOS0);
		MqttProperties properties = new MqttProperties();
		assertEquals(false, manager.apply(builder, properties));
		assertEquals(0, manager.size());
	}

	@Test
	void emptyTopicIsNoop() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(16);
		MqttPublishBuilder builder = newBuilder("");
		assertEquals(false, manager.apply(builder, new MqttProperties()));
		assertEquals(0, manager.size());
	}

	@Test
	void negativeMaxAliasThrows() {
		assertThrows(IllegalArgumentException.class, () -> new MqttTopicAliasManager(-1));
	}

	@Test
	void maxAliasZeroDisables() {
		// maxAlias = 0 表示禁用
		MqttTopicAliasManager manager = new MqttTopicAliasManager(0);
		MqttPublishBuilder builder = newBuilder("test/topic");
		MqttProperties properties = new MqttProperties();
		boolean usedAlias = manager.apply(builder, properties);
		assertEquals(false, usedAlias);
		assertEquals("test/topic", builder.getTopicName());
		assertEquals(0, manager.size());
	}

	@Test
	void concurrentRegisterAndApply() throws InterruptedException {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(64);
		int threadCount = 8;
		int iterations = 100;
		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threadCount; i++) {
			final int tid = i;
			threads[i] = new Thread(() -> {
				for (int j = 0; j < iterations; j++) {
					String topic = "thread-" + tid + "/topic-" + j;
					manager.apply(newBuilder(topic), new MqttProperties());
				}
			});
		}
		for (Thread t : threads) t.start();
		for (Thread t : threads) t.join();
		// size 在 [1, 64] 之间（实际可能更少，因为 alias 复用）
		int size = manager.size();
		assertTrue(size >= 1 && size <= 64,
			"Expected size in [1, 64], got " + size);
	}

	@Test
	void getMaxAlias() {
		MqttTopicAliasManager manager = new MqttTopicAliasManager(8);
		assertEquals(8, manager.getMaxAlias());
	}

	// helper
	private static MqttPublishBuilder newBuilder(String topic) {
		return MqttPublishMessage.builder()
			.topicName(topic)
			.payload("data".getBytes())
			.qos(MqttQoS.QOS1);
	}
}
