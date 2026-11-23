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

package org.dromara.mica.mqtt.broker.rule.matcher;

import org.dromara.mica.mqtt.broker.rule.RuleContext;

import java.util.List;
import java.util.Map;

/**
 * 内置 matcher：JSONPath-lite 字段条件。
 * <p>
 * 配置示例：{@code props: { path: device.temperature, op: gt, value: 80 }}。
 * </p>
 * <p>
 * 支持的路径语法：{@code a.b.c}（逐层取字段）与 {@code a[0].b}（数组下标）。
 * 取值基于规则解码后的 payload（{@code RuleContext} 中 {@code payload} 属性），
 * 因此规则需要配置 {@code codecType} 才能拿到结构化对象。
 * </p>
 *
 * @author L.cm
 */
public class JsonPathRuleMatcherFactory implements RuleMatcherFactory {

	/**
	 * matcher 类型标识。
	 */
	public static final String TYPE = "jsonPath";

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public RuleMatcher create(Map<String, String> props) {
		String path = props == null ? null : props.get("path");
		if (path == null || path.isEmpty()) {
			throw new IllegalArgumentException("jsonPath matcher requires 'path' property");
		}
		String op = props.get("op");
		String value = props.get("value");
		return ctx -> {
			Object actual = readPath(ctx, path);
			return RuleMatcher.compare(actual, op, value);
		};
	}

	/**
	 * 按 JSONPath-lite 语法取值。
	 *
	 * @param ctx  规则上下文
	 * @param path 路径
	 * @return 取到的值；路径不存在时返回 {@code null}
	 */
	static Object readPath(RuleContext ctx, String path) {
		Object current = ctx.attr(RuleContext.PAYLOAD_ATTR);
		if (current == null) {
			return null;
		}
		int pos = 0;
		int len = path.length();
		while (pos < len && current != null) {
			int dot = path.indexOf('.', pos);
			String segment = dot < 0 ? path.substring(pos) : path.substring(pos, dot);
			pos = dot < 0 ? len : dot + 1;
			current = readSegment(current, segment);
		}
		return current;
	}

	private static Object readSegment(Object source, String segment) {
		if (segment.isEmpty()) {
			return source;
		}
		int bracket = segment.indexOf('[');
		if (bracket < 0) {
			return readProperty(source, segment);
		}
		String name = segment.substring(0, bracket);
		Object current = name.isEmpty() ? source : readProperty(source, name);
		int pos = bracket;
		while (pos < segment.length() && current != null) {
			int close = segment.indexOf(']', pos);
			if (close < 0) {
				return null;
			}
			String indexText = segment.substring(pos + 1, close);
			try {
				int index = Integer.parseInt(indexText.trim());
				if (current instanceof List) {
					List<?> list = (List<?>) current;
					current = index >= 0 && index < list.size() ? list.get(index) : null;
				} else if (current instanceof Map) {
					current = ((Map<?, ?>) current).get(index);
				} else {
					return null;
				}
			} catch (NumberFormatException e) {
				return null;
			}
			pos = close + 1;
		}
		return current;
	}

	private static Object readProperty(Object source, String name) {
		if (source instanceof Map) {
			return ((Map<?, ?>) source).get(name);
		}
		return null;
	}
}
