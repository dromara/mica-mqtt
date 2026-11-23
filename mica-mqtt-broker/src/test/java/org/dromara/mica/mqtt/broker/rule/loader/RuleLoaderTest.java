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

package org.dromara.mica.mqtt.broker.rule.loader;

import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则文件加载单测：JSON / YAML 解析。
 *
 * @author L.cm
 */
class RuleLoaderTest {

	@Test
	void jsonLoaderParsesRules(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("rules.json");
		String content = "{\n"
			+ "  \"rules\": [\n"
			+ "    {\n"
			+ "      \"id\": \"rule-001\",\n"
			+ "      \"name\": \"device-event\",\n"
			+ "      \"topicFilter\": \"device/+/event\",\n"
			+ "      \"enabled\": true,\n"
			+ "      \"codec\": \"json\",\n"
			+ "      \"when\": \"payload.level == 'high'\",\n"
			+ "      \"stopOnError\": true,\n"
			+ "      \"timeoutMs\": 2000,\n"
			+ "      \"labels\": {\"env\": \"prod\"},\n"
			+ "      \"actions\": [\n"
			+ "        {\"type\": \"log\", \"level\": \"info\"},\n"
			+ "        {\"type\": \"http\", \"props\": {\"url\": \"https://example.com/x\"}}\n"
			+ "      ]\n"
			+ "    }\n"
			+ "  ]\n"
			+ "}";
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));

		JsonRuleLoader loader = new JsonRuleLoader(file);
		List<Rule> rules = loader.loadAll();
		assertEquals(1, rules.size());
		Rule rule = rules.get(0);
		assertEquals("rule-001", rule.getId());
		assertEquals("device/+/event", rule.getTopicFilter());
		assertEquals("json", rule.getCodecType());
		assertEquals("aviator", rule.getMatcherType());
		assertEquals("payload.level == 'high'", rule.getWhenExpr());
		assertTrue(rule.isStopOnError());
		assertEquals(2000L, rule.getTimeoutMs());
		assertEquals("prod", rule.getLabels().get("env"));
		assertEquals(2, rule.getActions().size());

		ActionRef first = rule.getActions().get(0);
		assertEquals("log", first.getType());
		assertEquals("info", first.getString("level"));

		ActionRef second = rule.getActions().get(1);
		assertEquals("http", second.getType());
		assertEquals("https://example.com/x", second.getString("url"));
	}

	@Test
	void yamlLoaderParsesRules(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("rules.yml");
		String content = "rules:\n"
			+ "  - id: rule-yaml\n"
			+ "    name: yaml-rule\n"
			+ "    topicFilter: sensor/+/temp\n"
			+ "    codec: json\n"
			+ "    matcher: prod-only\n"
			+ "    matcherProps:\n"
			+ "      env: prod\n"
			+ "    actions:\n"
			+ "      - type: publish\n"
			+ "        topic: v2/{topicSegments[2]}\n"
			+ "        qos: 1\n";
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));

		YamlRuleLoader loader = new YamlRuleLoader(file);
		List<Rule> rules = loader.loadAll();
		assertEquals(1, rules.size());
		Rule rule = rules.get(0);
		assertEquals("rule-yaml", rule.getId());
		assertEquals("sensor/+/temp", rule.getTopicFilter());
		assertEquals("prod-only", rule.getMatcherType());
		assertEquals("prod", rule.getMatcherProps().get("env"));
		assertEquals(1, rule.getActions().size());
		assertEquals("publish", rule.getActions().get(0).getType());
		assertEquals("v2/{topicSegments[2]}", rule.getActions().get(0).getString("topic"));
	}
}
