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

package org.dromara.mica.mqtt.core.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.tio.utils.mica.Pair;

/**
 * TopicUtil 测试
 *
 * @author L.cm
 */
class TopicUtilTest {

	@Test
	void test() {
		// gitee issues #I56BTC /iot/test/# 无法匹配到 /iot/test 和 /iot/test/
		Assertions.assertFalse(TopicUtil.match("+", "/iot/test"));
		Assertions.assertFalse(TopicUtil.match("+", "iot/test"));
		Assertions.assertFalse(TopicUtil.match("+", "/iot/test"));
		Assertions.assertFalse(TopicUtil.match("+", "/iot"));
		Assertions.assertFalse(TopicUtil.match("+/test", "/iot/test"));
		Assertions.assertFalse(TopicUtil.match("/iot/test/+/", "/iot/test/123"));

		Assertions.assertTrue(TopicUtil.match("/iot/test/+", "/iot/test/123"));
		Assertions.assertFalse(TopicUtil.match("/iot/test/+", "/iot/test/123/"));
		Assertions.assertTrue(TopicUtil.match("/iot/+/test", "/iot/abc/test"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/test", "/iot/abc/test/"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/test", "/iot/abc/test1"));
		Assertions.assertTrue(TopicUtil.match("/iot/+/+/test", "/iot/abc/123/test"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/+/test", "/iot/abc/123/test1"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/+/test", "/iot/abc/123/test/"));
		Assertions.assertTrue(TopicUtil.match("/iot/+/+/+", "/iot/abc/123/test"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/+/+", "/iot/abc/123/test/"));
		Assertions.assertTrue(TopicUtil.match("/iot/+/test", "/iot/a/test"));
		Assertions.assertTrue(TopicUtil.match("/iot/+/test", "/iot/a/test"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/+/+", "/iot/a//test/"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/+/+", "/iot/a/b/c/"));
		Assertions.assertFalse(TopicUtil.match("/iot/+/+/+", "/iot/a"));

		Assertions.assertTrue(TopicUtil.match("#", "/iot/test"));
		Assertions.assertTrue(TopicUtil.match("/iot/test/#", "/iot/test"));
		Assertions.assertTrue(TopicUtil.match("/iot/test/#", "/iot/test/"));
		Assertions.assertTrue(TopicUtil.match("/iot/test/#", "/iot/test/1"));
		Assertions.assertTrue(TopicUtil.match("/iot/test/#", "/iot/test/123123/12312"));

		Assertions.assertTrue(TopicUtil.match("/iot/test/123", "/iot/test/123"));
	}

	@Test
	void test2() {
		String s1 = "$SYS/brokers/${node}/clients/${clientId}/disconnected";
		String s2 = "$SYS/brokers/+/clients/+/disconnected";
		String s3 = TopicUtil.getTopicFilter(s1);
		Assertions.assertEquals(s2, s3);
		s1 = "$SYS/brokers/${node}/clients/${clientId}abc/disconnected";
		s3 = TopicUtil.getTopicFilter(s1);
		Assertions.assertEquals(s2, s3);
		s1 = "$SYS/brokers/${node}/clients/${clientId}abc${x}/disconnected";
		s3 = TopicUtil.getTopicFilter(s1);
		Assertions.assertEquals(s2, s3);
		s1 = "$SYS/brokers/${node}/clients/abc${clientId}abc${x}123/disconnected";
		s3 = TopicUtil.getTopicFilter(s1);
		Assertions.assertEquals(s2, s3);
	}

	@Test
	void test3() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("/iot/test/+a");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("/iot/test/a+");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("/iot/test/+a/");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("/iot/test/a+/");
		});
		Assertions.assertDoesNotThrow(() -> TopicUtil.validateTopicFilter("+"));
		Assertions.assertDoesNotThrow(() -> TopicUtil.validateTopicFilter("/iot/test/+"));
		Assertions.assertDoesNotThrow(() -> TopicUtil.validateTopicFilter("/iot/test/+/"));
	}

	@Test
	void test4() {
		String test1 = "Hello, ${name}!";
		String test2 = "No variable here";
		String test3 = "Invalid ${variable";
		String test4 = "${name}!";
		Assertions.assertTrue(TopicUtil.hasVariable(test1));
		Assertions.assertFalse(TopicUtil.hasVariable(test2));
		Assertions.assertFalse(TopicUtil.hasVariable(test3));
		Assertions.assertTrue(TopicUtil.hasVariable(test4));
	}

	@Test
	void testResolveTopic() {
		String message = "Hello, ${name}!";
		TestBean testBean = new TestBean();
		testBean.setName("张三");
		String m1 = TopicUtil.resolveTopic(message, testBean);
		Assertions.assertEquals("Hello, 张三!", m1);
		String s1 = "$SYS/brokers/${node}/clients/${clientId}/disconnected";
		testBean.setNode("node1");
		testBean.setClientId("abc123");
		String m2 = TopicUtil.resolveTopic(s1, testBean);
		Assertions.assertEquals("$SYS/brokers/node1/clients/abc123/disconnected", m2);
		String m3 = TopicUtil.resolveTopic("/iot/test/123", testBean);
		Assertions.assertEquals("/iot/test/123", m3);
	}

	@Test
	void testRetainTopicName() {
		Pair<String, Integer> pair1 = TopicUtil.retainTopicName("$retain/15/x/y");
		Assertions.assertEquals("x/y", pair1.getLeft());
		Pair<String, Integer> pair2 = TopicUtil.retainTopicName("$retain/15//x/y");
		Assertions.assertEquals("/x/y", pair2.getLeft());
		Pair<String, Integer> pair3 = TopicUtil.retainTopicName("$retain/15/");
		Assertions.assertEquals(-1, pair3.getRight());
		Pair<String, Integer> pair4 = TopicUtil.retainTopicName("$retain/");
		Assertions.assertEquals(-1, pair4.getRight());
	}

}
