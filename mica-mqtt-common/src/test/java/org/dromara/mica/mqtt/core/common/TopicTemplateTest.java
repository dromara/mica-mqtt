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

import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

}

