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

import net.dreamlu.mica.net.utils.json.JsonUtil;
import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.sink.SinkRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 JSON 文件加载规则，复用 mica-net-utils 的 JsonUtil。
 *
 * @author L.cm
 */
public class JsonRuleLoader implements RuleLoader {

	private static final Logger logger = LoggerFactory.getLogger(JsonRuleLoader.class);

	private final String source;
	private final Path path;

	public JsonRuleLoader(Path path) {
		this.source = "json:" + path;
		this.path = path;
	}

	@Override
	public String getSource() {
		return source;
	}

	@Override
	public List<Rule> loadAll() {
		try (InputStream in = Files.newInputStream(path)) {
			byte[] bytes = readAll(in);
			Object root = JsonUtil.readValue(bytes, Object.class);
			List<?> list = extractList(root);
			List<Rule> rules = new ArrayList<>(list.size());
			for (Object item : list) {
				Rule rule = parseRule(item);
				if (rule != null) {
					rules.add(rule);
				}
			}
			logger.info("Loaded {} rules from {}", rules.size(), source);
			return rules;
		} catch (IOException e) {
			throw new RuntimeException("Failed to load rules from " + path, e);
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	@SuppressWarnings("unchecked")
	private static List<?> extractList(Object root) {
		if (root instanceof List) {
			return (List<?>) root;
		}
		if (root instanceof Map) {
			Object inner = ((Map<?, ?>) root).get("rules");
			if (inner instanceof List) {
				return (List<?>) inner;
			}
		}
		return Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	private static Rule parseRule(Object item) {
		if (!(item instanceof Map)) {
			return null;
		}
		Map<String, Object> map = (Map<String, Object>) item;
		String topicFilter = stringOf(map.get("topicFilter"));
		if (topicFilter == null || topicFilter.isEmpty()) {
			logger.warn("Skip rule without topicFilter: {}", map);
			return null;
		}
		Rule.Builder builder = Rule.builder()
			.id(stringOf(map.get("id")))
			.name(stringOf(map.get("name")))
			.topicFilter(topicFilter)
			.enabled(booleanOf(map.get("enabled"), true))
			.matcherType(stringOf(map.get("matcher")))
			.stopOnError(booleanOf(map.get("stopOnError"), false))
			.labels(stringMap(map.get("labels")));
		Object matcherProps = map.get("matcherProps");
		if (matcherProps instanceof Map) {
			builder.matcherProps(stringMap(matcherProps));
		}
		Object sinksObj = map.get("sinks");
		if (sinksObj instanceof List) {
			for (Object s : (List<?>) sinksObj) {
				SinkRef ref = parseSink(s);
				if (ref != null) {
					builder.addSink(ref);
				}
			}
		}
		return builder.build();
	}

	@SuppressWarnings("unchecked")
	private static SinkRef parseSink(Object item) {
		if (!(item instanceof Map)) {
			return null;
		}
		Map<String, Object> map = (Map<String, Object>) item;
		String type = stringOf(map.get("type"));
		if (type == null || type.isEmpty()) {
			return null;
		}
		String name = stringOf(map.get("name"));
		SinkRef.Builder builder = SinkRef.builder(type);
		if (name != null && !name.isEmpty()) {
			builder.name(name);
		}
		Object props = map.get("props");
		if (props == null) {
			props = map;
		}
		if (props instanceof Map) {
			for (Map.Entry<?, ?> e : ((Map<?, ?>) props).entrySet()) {
				String key = e.getKey() == null ? null : e.getKey().toString();
				if ("type".equals(key) || "name".equals(key)) {
					continue;
				}
				if (key != null) {
					builder.prop(key, e.getValue());
				}
			}
		}
		return builder.build();
	}

	private static String stringOf(Object o) {
		return o == null ? null : o.toString();
	}

	private static boolean booleanOf(Object o, boolean def) {
		if (o == null) {
			return def;
		}
		if (o instanceof Boolean) {
			return (Boolean) o;
		}
		return Boolean.parseBoolean(o.toString());
	}

	private static Map<String, String> stringMap(Object o) {
		if (!(o instanceof Map)) {
			return null;
		}
		Map<String, String> out = new HashMap<>();
		for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
			if (e.getKey() != null) {
				out.put(e.getKey().toString(),
					e.getValue() == null ? null : e.getValue().toString());
			}
		}
		return out;
	}
}
