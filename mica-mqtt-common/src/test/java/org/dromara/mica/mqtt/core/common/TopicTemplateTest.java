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

package org.dromara.mica.mqtt.core.common;

import java.util.Map;

import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TopicTemplate 测试
 *
 * @author L.cm
 */
class TopicTemplateTest {

	@Test
	void testBasicMatch() {
		// 基本匹配测试
		String topicTemplate = "/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("/test/device002/data"));
		Assertions.assertFalse(template.match("/test/device001/status"));
		Assertions.assertFalse(template.match("/test/device001/data/extra"));
	}

	@Test
	void testGetVariables() {
		// 基本变量提取测试
		String topicTemplate = "/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Map<String, String> vars = template.getVariables("/test/device001/data");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));

		vars = template.getVariables("/test/device002/data");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device002", vars.get("deviceId"));
	}

	@Test
	void testMultipleVariables() {
		// 多个变量测试
		String topicTemplate = "/sys/${productKey}/${deviceName}/thing/sub/register";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Map<String, String> vars = template.getVariables("/sys/product123/device456/thing/sub/register");
		Assertions.assertEquals(2, vars.size());
		Assertions.assertEquals("product123", vars.get("productKey"));
		Assertions.assertEquals("device456", vars.get("deviceName"));
	}

	@Test
	void testWithWildcards() {
		// 带通配符的测试
		String topicTemplate = "/test/${deviceId}/+/status";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/temp/status"));
		Assertions.assertTrue(template.match("/test/device001/humidity/status"));

		Map<String, String> vars = template.getVariables("/test/device001/temp/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testMismatch() {
		// 不匹配测试
		String topicTemplate = "/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Map<String, String> vars = template.getVariables("/test/device001/status");
		Assertions.assertTrue(vars.isEmpty());

		vars = template.getVariables("/test/device001/data/extra");
		Assertions.assertTrue(vars.isEmpty());
	}

	@Test
	void testQueueSharedSubscription() {
		// $queue/ 共享订阅测试
		String topicTemplate = "$queue/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		// 匹配时应该去掉 $queue/ 前缀
		Assertions.assertTrue(template.match("test/device001/data"));
		Assertions.assertTrue(template.match("test/device002/data"));
		Assertions.assertFalse(template.match("test/device001/status"));

		// 变量提取
		Map<String, String> vars = template.getVariables("test/device001/data");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testQueueSharedSubscriptionWithSlash() {
		// $queue/ 共享订阅，topic 以 / 开头的情况
		String topicTemplate = "$queue//test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		// 匹配时应该去掉 $queue/ 前缀，保留 /
		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("/test/device002/data"));
		Assertions.assertFalse(template.match("test/device001/data"));

		// 变量提取
		Map<String, String> vars = template.getVariables("/test/device001/data");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testShareGroupSubscription() {
		// $share/<group>/ 分组订阅测试
		String topicTemplate = "$share/group1/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		// 匹配时应该去掉 $share/group1/ 前缀
		Assertions.assertTrue(template.match("test/device001/data"));
		Assertions.assertTrue(template.match("test/device002/data"));
		Assertions.assertFalse(template.match("test/device001/status"));

		// 变量提取
		Map<String, String> vars = template.getVariables("test/device001/data");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testShareGroupSubscriptionWithSlash() {
		// $share/<group>/ 分组订阅，topic 以 / 开头的情况
		String topicTemplate = "$share/group1//test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		// 匹配时应该去掉 $share/group1/ 前缀，保留 /
		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("/test/device002/data"));
		Assertions.assertFalse(template.match("test/device001/data"));

		// 变量提取
		Map<String, String> vars = template.getVariables("/test/device001/data");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testShareGroupSubscriptionMultipleGroups() {
		// 多个分组订阅测试
		String topicTemplate1 = "$share/group1/test/${deviceId}/data";
		String topicFilter1 = TopicUtil.getTopicFilter(topicTemplate1);
		TopicTemplate template1 = new TopicTemplate(topicTemplate1, topicFilter1);

		String topicTemplate2 = "$share/group2/test/${deviceId}/data";
		String topicFilter2 = TopicUtil.getTopicFilter(topicTemplate2);
		TopicTemplate template2 = new TopicTemplate(topicTemplate2, topicFilter2);

		// 两个模板应该都能匹配相同的 topic
		Assertions.assertTrue(template1.match("test/device001/data"));
		Assertions.assertTrue(template2.match("test/device001/data"));

		// 变量提取应该相同
		Map<String, String> vars1 = template1.getVariables("test/device001/data");
		Map<String, String> vars2 = template2.getVariables("test/device001/data");
		Assertions.assertEquals(vars1, vars2);
		Assertions.assertEquals("device001", vars1.get("deviceId"));
	}

	@Test
	void testComplexSharedSubscription() {
		// 复杂的共享订阅场景
		String topicTemplate = "$share/mygroup/sys/${productKey}/${deviceName}/thing/sub/register";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Map<String, String> vars = template.getVariables("sys/product123/device456/thing/sub/register");
		Assertions.assertEquals(2, vars.size());
		Assertions.assertEquals("product123", vars.get("productKey"));
		Assertions.assertEquals("device456", vars.get("deviceName"));
	}

	@Test
	void testEmptyVariables() {
		// 没有变量的模板
		String topicTemplate = "/test/device/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Map<String, String> vars = template.getVariables("/test/device/data");
		Assertions.assertTrue(vars.isEmpty());
	}

	@Test
	void testWildcardOnly() {
		// 只有通配符的模板
		String topicTemplate = "/test/+/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("/test/device002/data"));

		Map<String, String> vars = template.getVariables("/test/device001/data");
		Assertions.assertTrue(vars.isEmpty());
	}

	@Test
	void testVariableAndWildcard() {
		// 变量和通配符混合
		String topicTemplate = "/test/${deviceId}/+/status";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Map<String, String> vars = template.getVariables("/test/device001/temp/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testLongTopicPath() {
		// 长路径测试
		String topicTemplate = "/a/b/c/${var1}/d/e/${var2}/f";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Map<String, String> vars = template.getVariables("/a/b/c/value1/d/e/value2/f");
		Assertions.assertEquals(2, vars.size());
		Assertions.assertEquals("value1", vars.get("var1"));
		Assertions.assertEquals("value2", vars.get("var2"));
	}

	@Test
	void testPerformanceOptimization() {
		// 性能优化测试：多次调用 match 和 getVariables
		String topicTemplate = "$share/group1/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		// 多次调用 match，验证缓存优化
		for (int i = 0; i < 1000; i++) {
			Assertions.assertTrue(template.match("test/device001/data"));
		}

		// 多次调用 getVariables，验证优化
		for (int i = 0; i < 1000; i++) {
			Map<String, String> vars = template.getVariables("test/device001/data");
			Assertions.assertEquals("device001", vars.get("deviceId"));
		}
	}

	@Test
	void testWildcardHash1() {
		// # 通配符测试（多层级匹配）
		String topicTemplate = "/test/${deviceId}/#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("/test/device001/data/status"));
		Assertions.assertTrue(template.match("/test/device001/data/status/temp"));
		Assertions.assertFalse(template.match("/test/device001"));

		Map<String, String> vars = template.getVariables("/test/device001/data/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testWildcardHash2() {
		// + 通配符测试（多层级匹配）
		String topicTemplate = "/test/${deviceId}/+";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertFalse(template.match("/test/device001/data/status"));
		Assertions.assertFalse(template.match("/test/device001/data/status/temp"));
		Assertions.assertFalse(template.match("/test/device001"));

		Map<String, String> vars1 = template.getVariables("/test/device001/data");
		Assertions.assertEquals(1, vars1.size());
		Assertions.assertEquals("device001", vars1.get("deviceId"));

		Map<String, String> vars2 = template.getVariables("/test/device001/data/status");
		Assertions.assertTrue(vars2.isEmpty());
	}

	@Test
	void testWildcardHashAtRoot() {
		// # 通配符在根目录
		String topicTemplate = "#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("test/device001"));
		Assertions.assertTrue(template.match("/"));
	}

	@Test
	void testWildcardPlusMultiple() {
		// 多个 + 通配符
		String topicTemplate = "/test/+/+/status";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/temp/status"));
		Assertions.assertTrue(template.match("/test/device002/humidity/status"));
		Assertions.assertFalse(template.match("/test/device001/status"));
		Assertions.assertFalse(template.match("/test/device001/temp/status/extra"));
	}

	@Test
	void testVariableAtStart() {
		// 变量在开头
		String topicTemplate = "/${deviceId}/data/status";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/device001/data/status"));
		Assertions.assertTrue(template.match("/device002/data/status"));

		Map<String, String> vars = template.getVariables("/device001/data/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testVariableAtEnd() {
		// 变量在结尾
		String topicTemplate = "/test/data/${deviceId}";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/data/device001"));
		Assertions.assertTrue(template.match("/test/data/device002"));

		Map<String, String> vars = template.getVariables("/test/data/device001");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testConsecutiveVariables() {
		// 连续多个变量
		String topicTemplate = "/${a}/${b}/${c}";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/value1/value2/value3"));

		Map<String, String> vars = template.getVariables("/value1/value2/value3");
		Assertions.assertEquals(3, vars.size());
		Assertions.assertEquals("value1", vars.get("a"));
		Assertions.assertEquals("value2", vars.get("b"));
		Assertions.assertEquals("value3", vars.get("c"));
	}

	@Test
	void testVariableWithWildcardPlus() {
		// 变量和 + 通配符组合
		String topicTemplate = "/test/${deviceId}/+/+/status";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/temp/humidity/status"));
		Assertions.assertFalse(template.match("/test/device001/temp/status"));

		Map<String, String> vars = template.getVariables("/test/device001/temp/humidity/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testVariableWithWildcardHash() {
		// 变量和 # 通配符组合
		String topicTemplate = "/test/${deviceId}/#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("/test/device001/data/status"));
		Assertions.assertTrue(template.match("/test/device001/data/status/temp"));

		Map<String, String> vars = template.getVariables("/test/device001/data/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testTopicEndsWithSlash() {
		// topic 以 / 结尾
		String topicTemplate = "/test/${deviceId}/";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/"));
		Assertions.assertFalse(template.match("/test/device001"));

		Map<String, String> vars = template.getVariables("/test/device001/");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testEmptyTopicName() {
		// 空 topic 名称
		String topicTemplate = "/test/${deviceId}";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertFalse(template.match(""));
		Assertions.assertFalse(template.match("/"));

		Map<String, String> vars = template.getVariables("");
		Assertions.assertTrue(vars.isEmpty());
	}

	@Test
	void testSingleLevelTopic() {
		// 单层级 topic
		String topicTemplate = "/${deviceId}";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/device001"));
		Assertions.assertFalse(template.match("/device001/data"));

		Map<String, String> vars = template.getVariables("/device001");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testRootTopic() {
		// 根 topic
		String topicTemplate = "/";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/"));
		Assertions.assertFalse(template.match("/test"));
		Assertions.assertFalse(template.match(""));

		Map<String, String> vars = template.getVariables("/");
		Assertions.assertTrue(vars.isEmpty());
	}

	@Test
	void testLengthMismatch() {
		// 长度不匹配的各种情况
		String topicTemplate = "/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertFalse(template.match("/test/device001"));
		Assertions.assertFalse(template.match("/test/device001/data/extra"));
		Assertions.assertFalse(template.match("/test"));
		Assertions.assertFalse(template.match("/test/device001/data/status/temp"));

		Map<String, String> vars = template.getVariables("/test/device001");
		Assertions.assertTrue(vars.isEmpty());
	}

	@Test
	void testSpecialCharactersInVariable() {
		// 变量值包含特殊字符
		String topicTemplate = "/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device-001/data"));
		Assertions.assertTrue(template.match("/test/device_001/data"));
		Assertions.assertTrue(template.match("/test/device.001/data"));

		Map<String, String> vars = template.getVariables("/test/device-001/data");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device-001", vars.get("deviceId"));
	}

	@Test
	void testQueueSharedSubscriptionComplex() {
		// $queue/ 复杂场景
		String topicTemplate = "$queue/sys/${productKey}/${deviceName}/thing/#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("sys/product123/device456/thing/sub/register"));
		Assertions.assertTrue(template.match("sys/product123/device456/thing/sub/register/status"));

		Map<String, String> vars = template.getVariables("sys/product123/device456/thing/sub/register");
		Assertions.assertEquals(2, vars.size());
		Assertions.assertEquals("product123", vars.get("productKey"));
		Assertions.assertEquals("device456", vars.get("deviceName"));
	}

	@Test
	void testShareGroupSubscriptionLongPath() {
		// $share/<group>/ 长路径
		String topicTemplate = "$share/group1/a/b/c/${var1}/d/e/${var2}/f";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("a/b/c/value1/d/e/value2/f"));
		Assertions.assertFalse(template.match("a/b/c/value1/d/e/value2"));

		Map<String, String> vars = template.getVariables("a/b/c/value1/d/e/value2/f");
		Assertions.assertEquals(2, vars.size());
		Assertions.assertEquals("value1", vars.get("var1"));
		Assertions.assertEquals("value2", vars.get("var2"));
	}

	@Test
	void testShareGroupSubscriptionWithSlashLongPath() {
		// $share/<group>/ 长路径，以 / 开头
		String topicTemplate = "$share/group1//a/b/c/${var1}/d/e/${var2}/f";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/a/b/c/value1/d/e/value2/f"));
		Assertions.assertFalse(template.match("a/b/c/value1/d/e/value2/f"));

		Map<String, String> vars = template.getVariables("/a/b/c/value1/d/e/value2/f");
		Assertions.assertEquals(2, vars.size());
		Assertions.assertEquals("value1", vars.get("var1"));
		Assertions.assertEquals("value2", vars.get("var2"));
	}

	@Test
	void testWildcardPlusEmptyValue() {
		// + 通配符不能匹配空值
		String topicTemplate = "/test/+/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		// 注意：getTopicParts 会将空字符串作为单独的部分
		// 所以 "/test//data" 会被分割为 ["/", "test", "", "data"]
		Assertions.assertFalse(template.match("/test//data"));
	}

	@Test
	void testVariableEmptyValue() {
		// 变量可以匹配空值（如果 topic 结构允许）
		String topicTemplate = "/test/${deviceId}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		// 变量不能匹配空字符串，因为 getTopicParts 不会产生空字符串
		// 但可以匹配单个字符
		Assertions.assertTrue(template.match("/test/a/data"));
	}

	@Test
	void testMultipleWildcards() {
		// 多个通配符组合
		String topicTemplate = "/+/+/+/status";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/a/b/c/status"));
		Assertions.assertTrue(template.match("/test/device/data/status"));
		Assertions.assertFalse(template.match("/a/b/status"));
		Assertions.assertFalse(template.match("/a/b/c/d/status"));
	}

	@Test
	void testVariableInMiddle() {
		// 变量在中间位置
		String topicTemplate = "/a/${var}/c/d";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/a/b/c/d"));
		Assertions.assertFalse(template.match("/a/b/c"));

		Map<String, String> vars = template.getVariables("/a/b/c/d");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("b", vars.get("var"));
	}

	@Test
	void testQueueSharedSubscriptionSingleLevel() {
		// $queue/ 单层级
		String topicTemplate = "$queue/${deviceId}";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("device001"));
		Assertions.assertFalse(template.match("/device001"));

		Map<String, String> vars = template.getVariables("device001");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testShareGroupSubscriptionSingleLevel() {
		// $share/<group>/ 单层级
		String topicTemplate = "$share/group1/${deviceId}";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("device001"));
		Assertions.assertFalse(template.match("/device001"));

		Map<String, String> vars = template.getVariables("device001");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testShareGroupSubscriptionSingleLevelWithSlash() {
		// $share/<group>/ 单层级，以 / 开头
		String topicTemplate = "$share/group1//${deviceId}";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/device001"));
		Assertions.assertFalse(template.match("device001"));

		Map<String, String> vars = template.getVariables("/device001");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testComplexNestedVariables() {
		// 复杂的嵌套变量场景
		String topicTemplate = "/${level1}/${level2}/${level3}/data";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/a/b/c/data"));
		Assertions.assertTrue(template.match("/test/device/status/data"));

		Map<String, String> vars = template.getVariables("/a/b/c/data");
		Assertions.assertEquals(3, vars.size());
		Assertions.assertEquals("a", vars.get("level1"));
		Assertions.assertEquals("b", vars.get("level2"));
		Assertions.assertEquals("c", vars.get("level3"));
	}

	@Test
	void testVariableWithWildcardMixed() {
		// 变量和通配符混合的复杂场景
		String topicTemplate = "/${deviceId}/+/${type}/#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/device001/temp/status/data"));
		Assertions.assertTrue(template.match("/device001/temp/status/data/extra"));

		Map<String, String> vars = template.getVariables("/device001/temp/status/data");
		Assertions.assertEquals(2, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
		Assertions.assertEquals("status", vars.get("type"));
	}

	@Test
	void testQueueSharedSubscriptionWithHash() {
		// $queue/ 带 # 通配符
		String topicTemplate = "$queue/test/${deviceId}/#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("test/device001/data"));
		Assertions.assertTrue(template.match("test/device001/data/status"));
		Assertions.assertFalse(template.match("/test/device001/data"));

		Map<String, String> vars = template.getVariables("test/device001/data/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testShareGroupSubscriptionWithHash() {
		// $share/<group>/ 带 # 通配符
		String topicTemplate = "$share/group1/test/${deviceId}/#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("test/device001/data"));
		Assertions.assertTrue(template.match("test/device001/data/status"));
		Assertions.assertFalse(template.match("/test/device001/data"));

		Map<String, String> vars = template.getVariables("test/device001/data/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

	@Test
	void testShareGroupSubscriptionWithHashAndSlash() {
		// $share/<group>/ 带 # 通配符，以 / 开头
		String topicTemplate = "$share/group1//test/${deviceId}/#";
		String topicFilter = TopicUtil.getTopicFilter(topicTemplate);
		TopicTemplate template = new TopicTemplate(topicTemplate, topicFilter);

		Assertions.assertTrue(template.match("/test/device001/data"));
		Assertions.assertTrue(template.match("/test/device001/data/status"));
		Assertions.assertFalse(template.match("test/device001/data"));

		Map<String, String> vars = template.getVariables("/test/device001/data/status");
		Assertions.assertEquals(1, vars.size());
		Assertions.assertEquals("device001", vars.get("deviceId"));
	}

}

