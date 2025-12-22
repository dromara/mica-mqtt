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

import java.util.Map;

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

	@Test
	void testGetTopicVars1() {
		// 测试匹配
		String s1 = "$SYS/brokers/${node}/clients/${clientId}/disconnected";
		String s2 = "$SYS/brokers/node1/clients/test1/disconnected";
		Map<String, String> vars = TopicUtil.getTopicVars(s1, s2);
		Assertions.assertEquals("node1", vars.get("node"));
		Assertions.assertEquals("test1", vars.get("clientId"));
		// 测试不匹配
		String s3 = "$SYS/brokers/${node}/clients/${clientId}/disconnected";
		String s4 = "abc/brokers/node1/clients/test1/disconnected";
		Map<String, String> vars1 = TopicUtil.getTopicVars(s3, s4);
		// 不匹配会返回空
		Assertions.assertTrue(vars1.isEmpty());
	}

	@Test
	void testGetTopicVars2() {
		// 测试匹配
		String s1 = "lnsendout/youweian_mqtt/${deviceType}/${imei}/rtdata/#";
		String s2 = "lnsendout/youweian_mqtt/deviceType/imei/rtdata/123123";
		Map<String, String> vars = TopicUtil.getTopicVars(s1, s2);
		Assertions.assertEquals("deviceType", vars.get("deviceType"));
		Assertions.assertEquals("imei", vars.get("imei"));
	}

}
