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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.tio.utils.mica.IntPair;

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
		Map<String, String> mapData = new HashMap<>();
		mapData.put("name", "张三");
		String m4 = TopicUtil.resolveTopic("Hello, ${name}!", mapData);
		Assertions.assertEquals("Hello, 张三!", m4);
	}

	@Test
	void testRetainTopicName() {
		IntPair<String> pair1 = TopicUtil.retainTopicName("$retain/15/x/y");
		Assertions.assertEquals("x/y", pair1.getValue());
		IntPair<String> pair2 = TopicUtil.retainTopicName("$retain/15//x/y");
		Assertions.assertEquals("/x/y", pair2.getValue());
		IntPair<String> pair3 = TopicUtil.retainTopicName("$retain/15/");
		Assertions.assertEquals(-1, pair3.getKey());
		IntPair<String> pair4 = TopicUtil.retainTopicName("$retain/");
		Assertions.assertEquals(-1, pair4.getKey());
	}

	@Test
	void testGetTopicVars1() {
		// 测试匹配
		String s1 = "$SYS/brokers/${node}/clients/${clientId}/disconnected/+";
		String s2 = "$SYS/brokers/node1/clients/test1/disconnected/123123";
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

	@Test
	void testValidateTopicFilterList() {
		// 测试 List 版本的 validateTopicFilter
		List<String> validFilters = Arrays.asList("/test/+", "/test/#", "+", "#");
		Assertions.assertDoesNotThrow(() -> {
			TopicUtil.validateTopicFilter((List<String>) validFilters);
		});

		List<String> invalidFilters = Arrays.asList("/test/+", "/test/+a");
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter((List<String>) invalidFilters);
		});

		List<String> emptyList = new ArrayList<>();
		Assertions.assertDoesNotThrow(() -> {
			TopicUtil.validateTopicFilter((List<String>) emptyList);
		});
	}

	@Test
	void testValidateTopicName() {
		// 测试 topicName 校验
		Assertions.assertDoesNotThrow(() -> TopicUtil.validateTopicName("/test/device001"));
		Assertions.assertDoesNotThrow(() -> TopicUtil.validateTopicName("test/device001"));
		Assertions.assertDoesNotThrow(() -> TopicUtil.validateTopicName("/"));
		Assertions.assertDoesNotThrow(() -> TopicUtil.validateTopicName("device001"));

		// 包含 + 通配符应该抛出异常
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicName("/test/+");
		});

		// 包含 # 通配符应该抛出异常
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicName("/test/#");
		});

		// 包含 + 在中间应该抛出异常
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicName("/test/+/device");
		});
	}

	@Test
	void testGetTopicFilterType() {
		// 测试获取 topicFilter 类型
		Assertions.assertEquals(TopicFilterType.NONE, TopicUtil.getTopicFilterType("/test/device"));
		Assertions.assertEquals(TopicFilterType.NONE, TopicUtil.getTopicFilterType("test/device"));
		Assertions.assertEquals(TopicFilterType.QUEUE, TopicUtil.getTopicFilterType("$queue/test/device"));
		Assertions.assertEquals(TopicFilterType.SHARE, TopicUtil.getTopicFilterType("$share/group1/test/device"));
		Assertions.assertEquals(TopicFilterType.SHARE, TopicUtil.getTopicFilterType("$share/mygroup/test/device"));
	}

	@Test
	void testGetTopicParts() {
		// 测试 getTopicParts 方法
		String[] parts1 = TopicUtil.getTopicParts("/test/device001");
		Assertions.assertEquals(3, parts1.length);
		Assertions.assertEquals("/", parts1[0]);
		Assertions.assertEquals("test", parts1[1]);
		Assertions.assertEquals("device001", parts1[2]);

		String[] parts2 = TopicUtil.getTopicParts("test/device001");
		Assertions.assertEquals(2, parts2.length);
		Assertions.assertEquals("test", parts2[0]);
		Assertions.assertEquals("device001", parts2[1]);

		String[] parts3 = TopicUtil.getTopicParts("/test/device001/");
		Assertions.assertEquals(4, parts3.length);
		Assertions.assertEquals("/", parts3[0]);
		Assertions.assertEquals("test", parts3[1]);
		Assertions.assertEquals("device001", parts3[2]);
		Assertions.assertEquals("/", parts3[3]);

		String[] parts4 = TopicUtil.getTopicParts("/");
		Assertions.assertEquals(1, parts4.length);
		Assertions.assertEquals("/", parts4[0]);

		String[] parts5 = TopicUtil.getTopicParts("");
		Assertions.assertEquals(0, parts5.length);

		String[] parts6 = TopicUtil.getTopicParts("a/b/c/d/e");
		Assertions.assertEquals(5, parts6.length);
		Assertions.assertEquals("a", parts6[0]);
		Assertions.assertEquals("b", parts6[1]);
		Assertions.assertEquals("c", parts6[2]);
		Assertions.assertEquals("d", parts6[3]);
		Assertions.assertEquals("e", parts6[4]);
	}

	@Test
	void testHasVariable() {
		// 测试 hasVariable 方法的各种场景
		Assertions.assertTrue(TopicUtil.hasVariable("${name}"));
		Assertions.assertTrue(TopicUtil.hasVariable("Hello, ${name}!"));
		Assertions.assertTrue(TopicUtil.hasVariable("${name}${age}"));
		Assertions.assertTrue(TopicUtil.hasVariable("/test/${deviceId}/data"));
		Assertions.assertTrue(TopicUtil.hasVariable("${a}${b}${c}"));

		Assertions.assertFalse(TopicUtil.hasVariable(""));
		Assertions.assertFalse(TopicUtil.hasVariable("no variable"));
		Assertions.assertFalse(TopicUtil.hasVariable("${incomplete"));
		Assertions.assertFalse(TopicUtil.hasVariable("incomplete}"));
		Assertions.assertFalse(TopicUtil.hasVariable("${}"));
		Assertions.assertFalse(TopicUtil.hasVariable("${"));
		Assertions.assertFalse(TopicUtil.hasVariable("}"));
		Assertions.assertFalse(TopicUtil.hasVariable(null));
	}

	@Test
	void testResolveTopicWithMap() {
		// 测试 resolveTopic 使用 Map
		Map<String, Object> mapData = new HashMap<>();
		mapData.put("name", "张三");
		mapData.put("age", 25);
		mapData.put("city", "北京");

		String result1 = TopicUtil.resolveTopic("Hello, ${name}!", mapData);
		Assertions.assertEquals("Hello, 张三!", result1);

		String result2 = TopicUtil.resolveTopic("/user/${name}/${age}", mapData);
		Assertions.assertEquals("/user/张三/25", result2);

		String result3 = TopicUtil.resolveTopic("/test/${city}/data", mapData);
		Assertions.assertEquals("/test/北京/data", result3);

		// 不存在的字段
		String result4 = TopicUtil.resolveTopic("/test/${notexist}", mapData);
		Assertions.assertEquals("/test/", result4);

		// null payload
		String result5 = TopicUtil.resolveTopic("/test/${name}", null);
		Assertions.assertEquals("/test/${name}", result5);
	}

	@Test
	void testResolveTopicWithNullValue() {
		// 测试 resolveTopic 处理 null 值
		TestBean testBean = new TestBean();
		testBean.setName(null);
		String result = TopicUtil.resolveTopic("Hello, ${name}!", testBean);
		Assertions.assertEquals("Hello, !", result);
	}

	@Test
	void testResolveTopicMultipleVariables() {
		// 测试 resolveTopic 多个变量
		TestBean testBean = new TestBean();
		testBean.setName("张三");
		testBean.setNode("node1");
		testBean.setClientId("client123");

		String result = TopicUtil.resolveTopic("${name}/${node}/${clientId}", testBean);
		Assertions.assertEquals("张三/node1/client123", result);
	}

	@Test
	void testRetainTopicNameMoreCases() {
		// 测试 retainTopicName 更多场景
		IntPair<String> pair1 = TopicUtil.retainTopicName("$retain/0/x/y");
		Assertions.assertEquals(0, pair1.getKey());
		Assertions.assertEquals("x/y", pair1.getValue());

		IntPair<String> pair2 = TopicUtil.retainTopicName("$retain/3600/test/data");
		Assertions.assertEquals(3600, pair2.getKey());
		Assertions.assertEquals("test/data", pair2.getValue());

		IntPair<String> pair3 = TopicUtil.retainTopicName("$retain/abc/test");
		Assertions.assertEquals(-1, pair3.getKey());

		IntPair<String> pair4 = TopicUtil.retainTopicName("normal/topic");
		Assertions.assertEquals(0, pair4.getKey());
		Assertions.assertEquals("normal/topic", pair4.getValue());

		IntPair<String> pair5 = TopicUtil.retainTopicName("$retain/60/");
		Assertions.assertEquals(-1, pair5.getKey());
	}

	@Test
	void testMatchEdgeCases() {
		// 测试 match 方法的边界情况
		// 空字符串
		Assertions.assertTrue(TopicUtil.match("#", ""));
		Assertions.assertFalse(TopicUtil.match("+", ""));

		// 单个字符
		Assertions.assertTrue(TopicUtil.match("+", "a"));
		Assertions.assertTrue(TopicUtil.match("+", "ab"));

		// 根路径
		Assertions.assertTrue(TopicUtil.match("/", "/"));
		Assertions.assertFalse(TopicUtil.match("/", ""));
		Assertions.assertFalse(TopicUtil.match("/", "/test"));

		// # 在中间应该抛出异常
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.match("/test/#/more", "/test/data/more");
		});

		// + 前后必须有 /
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.match("test+data", "test123data");
		});
	}

	@Test
	void testGetTopicFilterEdgeCases() {
		// 测试 getTopicFilter 边界情况
		String result1 = TopicUtil.getTopicFilter("${var}");
		Assertions.assertEquals("+", result1);

		String result2 = TopicUtil.getTopicFilter("/${var}");
		Assertions.assertEquals("/+", result2);

		String result3 = TopicUtil.getTopicFilter("${var}/");
		Assertions.assertEquals("+/", result3);

		String result4 = TopicUtil.getTopicFilter("/${a}/${b}/${c}");
		Assertions.assertEquals("/+/+/+", result4);

		String result5 = TopicUtil.getTopicFilter("no/variables");
		Assertions.assertEquals("no/variables", result5);

		String result6 = TopicUtil.getTopicFilter("/");
		Assertions.assertEquals("/", result6);
	}

	@Test
	void testGetTopicVarsEdgeCases() {
		// 测试 getTopicVars 边界情况
		// 没有变量的模板
		Map<String, String> vars1 = TopicUtil.getTopicVars("/test/device", "/test/device");
		Assertions.assertTrue(vars1.isEmpty());

		// 不匹配的情况
		Map<String, String> vars2 = TopicUtil.getTopicVars("/test/${id}", "/other/device");
		Assertions.assertTrue(vars2.isEmpty());

		// 长度不匹配
		Map<String, String> vars3 = TopicUtil.getTopicVars("/test/${id}", "/test/device/extra");
		Assertions.assertTrue(vars3.isEmpty());

		// 单个变量
		Map<String, String> vars4 = TopicUtil.getTopicVars("/${id}", "/device001");
		Assertions.assertEquals(1, vars4.size());
		Assertions.assertEquals("device001", vars4.get("id"));

		// 变量在开头
		Map<String, String> vars5 = TopicUtil.getTopicVars("${id}/test", "device001/test");
		Assertions.assertEquals(1, vars5.size());
		Assertions.assertEquals("device001", vars5.get("id"));
	}

	@Test
	void testValidateTopicFilterEdgeCases() {
		// 测试 validateTopicFilter 边界情况
		// 空字符串
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("");
		});

		// null
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter((String) null);
		});

		// 包含空白字符（应该警告但不抛异常）
		Assertions.assertDoesNotThrow(() -> {
			TopicUtil.validateTopicFilter("/test /device");
		});

		// # 不在最后
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("/test/#/more");
		});

		// + 前后没有 /
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("test+data");
		});

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("test+");
		});

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			TopicUtil.validateTopicFilter("+test");
		});
	}

	@Test
	void testMatchComplexScenarios() {
		// 测试 match 复杂场景
		// 多个 + 通配符
		Assertions.assertTrue(TopicUtil.match("/+/+/+", "/a/b/c"));
		Assertions.assertFalse(TopicUtil.match("/+/+/+", "/a/b"));

		// + 和 # 组合
		Assertions.assertTrue(TopicUtil.match("/+/#", "/test/data/more"));
		Assertions.assertFalse(TopicUtil.match("/+/#", "/test"));

		// 长路径匹配
		Assertions.assertTrue(TopicUtil.match("/a/b/c/d/e", "/a/b/c/d/e"));
		Assertions.assertFalse(TopicUtil.match("/a/b/c/d/e", "/a/b/c/d"));

		// # 匹配多层级
		Assertions.assertTrue(TopicUtil.match("/test/#", "/test/a/b/c/d/e/f"));
		Assertions.assertTrue(TopicUtil.match("/test/#", "/test/"));
	}

	@Test
	void testGetTopicVarsWithWildcards() {
		// 测试 getTopicVars 带通配符的情况
		Map<String, String> vars1 = TopicUtil.getTopicVars("/test/${id}/+", "/test/device001/temp");
		Assertions.assertEquals(1, vars1.size());
		Assertions.assertEquals("device001", vars1.get("id"));

		Map<String, String> vars2 = TopicUtil.getTopicVars("/test/${id}/#", "/test/device001/data/status");
		Assertions.assertEquals(1, vars2.size());
		Assertions.assertEquals("device001", vars2.get("id"));
	}

	@Test
	void testResolveTopicWithSpecialCharacters() {
		// 测试 resolveTopic 特殊字符
		TestBean testBean = new TestBean();
		testBean.setName("test-value_123");

		String result = TopicUtil.resolveTopic("/test/${name}", testBean);
		Assertions.assertEquals("/test/test-value_123", result);
	}

	@Test
	void testGetTopicPartsWithSpecialCases() {
		// 测试 getTopicParts 特殊情况
		String[] parts1 = TopicUtil.getTopicParts("a");
		Assertions.assertEquals(1, parts1.length);
		Assertions.assertEquals("a", parts1[0]);

		String[] parts2 = TopicUtil.getTopicParts("//");
		Assertions.assertEquals(3, parts2.length);
		Assertions.assertEquals("/", parts2[0]);
		Assertions.assertEquals("", parts2[1]);
		Assertions.assertEquals("/", parts2[2]);

		String[] parts3 = TopicUtil.getTopicParts("a/b/c/d/e/f/g/h/i/j");
		Assertions.assertEquals(10, parts3.length);
	}

}
