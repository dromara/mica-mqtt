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

/**
 * 可选匹配器：在 topic 命中后，再做一次内容级匹配。
 *
 * @author L.cm
 */
@FunctionalInterface
public interface RuleMatcher {

	/**
	 * @param ctx 规则上下文
	 * @return true 放行执行 actions
	 */
	boolean matches(RuleContext ctx);

	/**
	 * 比较操作符语义，供声明式 matcher（header / jsonPath）复用。
	 * <p>
	 * 支持 {@code eq}（默认）、{@code neq}、{@code contains}、{@code startsWith}、
	 * {@code endsWith}、{@code exists}、{@code gt}、{@code gte}、{@code lt}、{@code lte}。
	 * </p>
	 *
	 * @param actual   实际值，可为 {@code null}
	 * @param op       操作符，{@code null} 或空串按 {@code eq} 处理
	 * @param expected 期望值
	 * @return 是否满足
	 */
	static boolean compare(Object actual, String op, String expected) {
		String operator = op == null || op.isEmpty() ? "eq" : op.trim().toLowerCase();
		switch (operator) {
			case "eq":
				return actual != null && String.valueOf(actual).equals(expected);
			case "neq":
				return actual == null || !String.valueOf(actual).equals(expected);
			case "contains":
				return actual != null && String.valueOf(actual).contains(expected);
			case "startswith":
				return actual != null && String.valueOf(actual).startsWith(expected);
			case "endswith":
				return actual != null && String.valueOf(actual).endsWith(expected);
			case "exists":
				return actual != null;
			case "gt":
			case "gte":
			case "lt":
			case "lte": {
				if (actual == null || expected == null) {
					return false;
				}
				Double left = toNumber(actual);
				Double right = toNumber(expected);
				if (left == null || right == null) {
					return false;
				}
				switch (operator) {
					case "gt":
						return left > right;
					case "gte":
						return left >= right;
					case "lt":
						return left < right;
					default:
						return left <= right;
				}
			}
			default:
				return false;
		}
	}

	/**
	 * 把值转成数字，无法转换时返回 {@code null}。
	 *
	 * @param value 值
	 * @return 数值或 {@code null}
	 */
	static Double toNumber(Object value) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		try {
			return Double.valueOf(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
