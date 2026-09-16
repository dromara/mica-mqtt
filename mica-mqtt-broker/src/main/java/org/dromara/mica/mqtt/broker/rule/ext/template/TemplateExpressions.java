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

package org.dromara.mica.mqtt.broker.rule.ext.template;

import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.ext.aviator.AviatorExprMatcher;

/**
 * 模板占位符 {@code ${expr}} 插值：通过 {@link String#indexOf} 扫描 {@code ${} }，
 * 避免正则匹配开销。表达式编译由 Aviator 内部缓存（{@code compile(expr, true)}）处理。
 *
 * <p>参考 {@code TopicUtil#resolveTopic} 的实现思路。
 *
 * @author L.cm
 */
public final class TemplateExpressions {

	private TemplateExpressions() {
	}

	/**
	 * 把模板中的 {@code ${expr}} 替换为 Aviator 求值结果；不含占位符时原样返回。
	 *
	 * @param template 模板字符串，可为 {@code null}
	 * @param ctx      规则上下文
	 * @return 插值结果，{@code template} 为 {@code null} 时返回空串
	 */
	public static String interpolate(String template, RuleContext ctx) {
		if (template == null) {
			return "";
		}
		StringBuilder sb = null;
		int cursor = 0;
		int templateLength = template.length();
		while (cursor < templateLength) {
			int start = template.indexOf("${", cursor);
			if (start == -1) {
				break;
			}
			int end = template.indexOf('}', start + 2);
			if (end == -1) {
				break;
			}
			if (sb == null) {
				sb = new StringBuilder(templateLength + 32);
			}
			sb.append(template, cursor, start);
			String expr = template.substring(start + 2, end);
			Object value = evaluate(expr, ctx);
			if (value != null) {
				sb.append(value);
			}
			cursor = end + 1;
		}
		if (sb == null) {
			return template;
		}
		sb.append(template, cursor, templateLength);
		return sb.toString();
	}

	private static Object evaluate(String expr, RuleContext ctx) {
		// 编译结果由 AviatorEvaluatorInstance 内部 LRU 缓存，无需额外缓存。
		try {
			return new AviatorExprMatcher(expr).execute(AviatorExprMatcher.envOf(ctx));
		} catch (Exception e) {
			// 占位符求值失败降级为空串，不影响同模板中其它占位符
			return null;
		}
	}
}
