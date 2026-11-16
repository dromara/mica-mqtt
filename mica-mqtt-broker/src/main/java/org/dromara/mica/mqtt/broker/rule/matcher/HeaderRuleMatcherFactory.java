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

import java.util.Map;

/**
 * 内置 matcher：单个 header 条件。
 * <p>
 * 配置示例：{@code props: { key: region, op: eq, value: cn-east }}。
 * </p>
 *
 * @author L.cm
 */
public class HeaderRuleMatcherFactory implements RuleMatcherFactory {

	/**
	 * matcher 类型标识。
	 */
	public static final String TYPE = "header";

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public RuleMatcher create(Map<String, String> props) {
		String key = props == null ? null : props.get("key");
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("header matcher requires 'key' property");
		}
		String op = props.get("op");
		String value = props.get("value");
		return ctx -> {
			Map<String, String> headers = ctx.getHeaders();
			String actual = headers == null ? null : headers.get(key);
			return RuleMatcher.compare(actual, op, value);
		};
	}
}
