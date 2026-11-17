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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则配置文件解析：把 YAML / JSON 解析出来的结构转成 {@link Rule}。
 * <p>
 * 配置格式：
 * <pre>
 * rules:
 *   - id: rule-001
 *     name: device-event-to-kafka
 *     topicFilter: device/+/event
 *     enabled: true
 *     codec: json
 *     matcher: payload-meta-env-is-prod
 *     when: "payload.temperature &gt; 80"
 *     stopOnError: false
 *     timeoutMs: 5000
 *     labels: { env: prod }
 *     actions:
 *       - type: kafka
 *         bootstrap: 10.0.0.1:9092
 *         topic: device-events
 * </pre>
 * action 的属性既支持平铺，也支持 {@code props:} 嵌套（两者可混用）。
 *
 * @author L.cm
 */
public final class RuleConfigParser {

	private RuleConfigParser() {
	}

	/**
	 * 解析规则集合。
	 *
	 * @param root 解析后的根节点，通常是含 {@code rules} 数组的 Map，也接受直接的数组
	 * @return 规则列表
	 */
	@SuppressWarnings("unchecked")
	public static List<Rule> parse(Object root) {
		List<Rule> rules = new ArrayList<>();
		Object list = root;
		if (root instanceof Map) {
			list = ((Map<String, Object>) root).get("rules");
		}
		if (!(list instanceof List)) {
			return rules;
		}
		for (Object item : (List<Object>) list) {
			if (item instanceof Map) {
				rules.add(parseRule((Map<String, Object>) item));
			}
		}
		return rules;
	}

	/**
	 * 解析单条规则。
	 *
	 * @param map 规则节点
	 * @return 规则
	 */
	@SuppressWarnings("unchecked")
	public static Rule parseRule(Map<String, Object> map) {
		Rule.Builder builder = Rule.builder();
		builder.id(asString(map.get("id")));
		builder.name(asString(map.get("name")));
		builder.topicFilter(asString(map.get("topicFilter")));

		Object enabled = map.get("enabled");
		if (enabled != null) {
			builder.enabled(Boolean.parseBoolean(String.valueOf(enabled)));
		}
		String codec = asString(map.get("codec"));
		builder.codecType(codec == null ? asString(map.get("codecType")) : codec);

		String matcher = asString(map.get("matcher"));
		builder.matcherType(matcher == null ? asString(map.get("matcherType")) : matcher);

		Object matcherProps = map.get("matcherProps");
		if (matcherProps instanceof Map) {
			builder.matcherProps(toStringMap((Map<Object, Object>) matcherProps));
		}

		builder.when(asString(map.get("when")));

		Object stopOnError = map.get("stopOnError");
		if (stopOnError != null) {
			builder.stopOnError(Boolean.parseBoolean(String.valueOf(stopOnError)));
		}
		Object timeoutMs = map.get("timeoutMs");
		if (timeoutMs != null) {
			builder.timeoutMs(toLong(timeoutMs));
		}
		Object labels = map.get("labels");
		if (labels instanceof Map) {
			builder.labels(toStringMap((Map<Object, Object>) labels));
		}
		builder.actions(parseActions(map.get("actions")));
		return builder.build();
	}

	/**
	 * 解析 actions 列表。
	 *
	 * @param value actions 节点
	 * @return action 引用列表
	 */
	@SuppressWarnings("unchecked")
	public static List<ActionRef> parseActions(Object value) {
		if (!(value instanceof List)) {
			return Collections.emptyList();
		}
		List<ActionRef> refs = new ArrayList<>();
		for (Object item : (List<Object>) value) {
			if (item instanceof Map) {
				refs.add(parseAction((Map<String, Object>) item));
			}
		}
		return refs;
	}

	/**
	 * 解析单个 action。
	 *
	 * @param map action 节点
	 * @return action 引用
	 */
	@SuppressWarnings("unchecked")
	public static ActionRef parseAction(Map<String, Object> map) {
		String type = asString(map.get("type"));
		if (type == null || type.isEmpty()) {
			throw new IllegalArgumentException("rule action requires a 'type' field");
		}
		ActionRef.Builder builder = ActionRef.builder(type);
		String name = asString(map.get("name"));
		if (name != null) {
			builder.name(name);
		}
		Object props = map.get("props");
		if (props instanceof Map) {
			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) props).entrySet()) {
				builder.prop(String.valueOf(entry.getKey()), entry.getValue());
			}
		}
		// 平铺属性（type / name / props 除外）
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			if ("type".equals(key) || "name".equals(key) || "props".equals(key)) {
				continue;
			}
			builder.prop(key, entry.getValue());
		}
		return builder.build();
	}

	/**
	 * 把任意 Map 转成 {@code Map<String, String>}。
	 *
	 * @param map 源 map
	 * @return 字符串 map
	 */
	private static Map<String, String> toStringMap(Map<Object, Object> map) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			result.put(String.valueOf(entry.getKey()),
				entry.getValue() == null ? null : String.valueOf(entry.getValue()));
		}
		return result;
	}

	/**
	 * 取字符串值。
	 *
	 * @param value 节点
	 * @return 字符串；节点为 null 时返回 null
	 */
	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	/**
	 * 取长整型值：JSON 里的数字可能是 {@code Double}（如 2000.0）。
	 *
	 * @param value 节点
	 * @return 长整型值
	 */
	private static long toLong(Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return Long.parseLong(String.valueOf(value).trim());
	}
}
