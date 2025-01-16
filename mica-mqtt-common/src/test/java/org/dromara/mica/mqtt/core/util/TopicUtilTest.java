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
		Assertions.assertEquals(s3, s2);
		s1 = "$SYS/brokers/${node}/clients/${clientId}abc/disconnected";
		s3 = TopicUtil.getTopicFilter(s1);
		Assertions.assertEquals(s3, s2);
		s1 = "$SYS/brokers/${node}/clients/${clientId}abc${x}/disconnected";
		s3 = TopicUtil.getTopicFilter(s1);
		Assertions.assertEquals(s3, s2);
		s1 = "$SYS/brokers/${node}/clients/abc${clientId}abc${x}123/disconnected";
		s3 = TopicUtil.getTopicFilter(s1);
		Assertions.assertEquals(s3, s2);
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

}
