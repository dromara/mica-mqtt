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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 内置 matcher：组合多个具名 matcher。
 * <p>
 * 配置示例：{@code props: { and: "prod-only,temp-alert", or: "hot-region" }}。
 * </p>
 * <p>
 * 引用的是通过 {@link MatcherRegistry#registerMatcher(String, RuleMatcher)} 注册的
 * 具名 matcher。语义为：{@code and} 列表全部满足、且 {@code or} 列表至少满足一个
 * （任一列表为空则该侧不参与判断）。
 * </p>
 *
 * @author L.cm
 */
public class CompoundRuleMatcherFactory implements RuleMatcherFactory {

	private static final Logger logger = LoggerFactory.getLogger(CompoundRuleMatcherFactory.class);

	/**
	 * matcher 类型标识。
	 */
	public static final String TYPE = "compound";

	private MatcherRegistry registry;

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public void setRegistry(MatcherRegistry registry) {
		this.registry = registry;
	}

	@Override
	public RuleMatcher create(Map<String, String> props) {
		List<String> andNames = split(props == null ? null : props.get("and"));
		List<String> orNames = split(props == null ? null : props.get("or"));
		if (andNames.isEmpty() && orNames.isEmpty()) {
			throw new IllegalArgumentException("compound matcher requires 'and' or 'or' property");
		}
		List<RuleMatcher> andMatchers = resolve(andNames);
		List<RuleMatcher> orMatchers = resolve(orNames);
		return ctx -> {
			for (RuleMatcher m : andMatchers) {
				if (!m.matches(ctx)) {
					return false;
				}
			}
			if (!orMatchers.isEmpty()) {
				boolean any = false;
				for (RuleMatcher m : orMatchers) {
					if (m.matches(ctx)) {
						any = true;
						break;
					}
				}
				return any;
			}
			return true;
		};
	}

	/**
	 * 按名称解析具名 matcher，未注册时抛错（启动期暴露配置问题）。
	 */
	private List<RuleMatcher> resolve(List<String> names) {
		List<RuleMatcher> list = new ArrayList<>(names.size());
		for (String name : names) {
			RuleMatcher matcher = registry == null ? null : registry.getNamed(name);
			if (matcher == null) {
				throw new IllegalArgumentException("compound matcher references unknown matcher: " + name);
			}
			list.add(matcher);
		}
		return list;
	}

	private static List<String> split(String value) {
		if (value == null || value.trim().isEmpty()) {
			return Collections.emptyList();
		}
		List<String> list = new ArrayList<>();
		for (String item : value.split(",")) {
			String trimmed = item.trim();
			if (!trimmed.isEmpty()) {
				list.add(trimmed);
			}
		}
		return list;
	}
}
