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

/**
 * 规则模板渲染：替换 {@code {topic}} / {@code {clientId}} / {@code {rule.name}} / {@code {topicSegments[i]}}。
 * <p>
 * 所有 action（内置 {@code rule.action} 与扩展 {@code rule.ext.template}）共用这一套占位符语义，
 * 避免各自实现导致能力不一致。放在 {@code rule} 根包而非 {@code ext.template} 下，是因为
 * {@code rule.action} 是导出包而 {@code ext.template} 不是，渲染器属于两者的公共依赖。
 * </p>
 * <p>
 * <b>替换值不再被二次扫描</b>：占位符只做一次替换，替换进来的内容（topic / clientId 等
 * 都来自消息，可能是外部可控的）不会被后续占位符再匹配一次。否则 topic 里的
 * {@code {clientId}} 会被展开成真正的 clientId，语义就不再可预期。
 * </p>
 *
 * @author L.cm
 */
public final class TemplateRenderer {

	private static final String TOPIC = "topic";
	private static final String CLIENT_ID = "clientId";
	private static final String RULE_NAME = "rule.name";
	private static final String TOPIC_SEGMENTS = "topicSegments[";

	private TemplateRenderer() {
	}

	/**
	 * 渲染模板。
	 *
	 * @param template 模板串；{@code null} 视为空串
	 * @param ctx      当前规则上下文
	 * @return 渲染结果；占位符缺失时替换为空串
	 */
	public static String render(String template, RuleContext ctx) {
		if (template == null) {
			return "";
		}
		String topic = ctx.getTopic() == null ? "" : ctx.getTopic();
		String clientId = ctx.getClientId() == null ? "" : ctx.getClientId();
		String ruleName = ctx.getRule() == null || ctx.getRule().getName() == null
			? "" : ctx.getRule().getName();
		String[] parts = topic.split("/");
		StringBuilder sb = new StringBuilder(template.length() + 32);
		int cursor = 0;
		int length = template.length();
		while (cursor < length) {
			int open = template.indexOf('{', cursor);
			if (open == -1) {
				break;
			}
			int close = template.indexOf('}', open + 1);
			if (close == -1) {
				break;
			}
			String replacement = resolve(template.substring(open + 1, close), topic, clientId, ruleName, parts);
			sb.append(template, cursor, open);
			// 未识别的占位符原样保留
			sb.append(replacement == null ? template.substring(open, close + 1) : replacement);
			cursor = close + 1;
		}
		if (sb.length() == 0) {
			return template;
		}
		sb.append(template, cursor, length);
		return sb.toString();
	}

	/**
	 * 渲染模板：先求值 {@code ${expr}}，再替换 {@code {topic}} 等占位符。
	 * <p>
	 * <b>顺序不能颠倒。</b>{@code {topic}} / {@code {clientId}} 的替换值来自消息，
	 * 是外部可控的；若先替换再求值，攻击者只要把 {@code ${...}} 写进 topic，
	 * broker 就会替他求值表达式（可读到环境变量与规则里的凭据）。
	 * 先求值表达式时，被扫描的始终是规则作者写下的模板本身，
	 * 消息内容只会作为求值结果出现，不会再被当作表达式扫描。
	 * </p>
	 *
	 * @param template 模板串；{@code null} 视为空串
	 * @param ctx      当前规则上下文
	 * @return 渲染结果
	 */
	public static String renderWithExpressions(String template, RuleContext ctx) {
		return render(TemplateExpressions.interpolate(template, ctx), ctx);
	}

	/**
	 * 解析单个占位符。
	 *
	 * @param token     占位符名（不含大括号）
	 * @param topic     当前 topic
	 * @param clientId  当前 clientId
	 * @param ruleName  当前规则名
	 * @param parts     topic 按 {@code /} 切分后的段
	 * @return 替换值；占位符未识别时返回 {@code null}
	 */
	private static String resolve(String token, String topic, String clientId, String ruleName, String[] parts) {
		if (TOPIC.equals(token)) {
			return topic;
		}
		if (CLIENT_ID.equals(token)) {
			return clientId;
		}
		if (RULE_NAME.equals(token)) {
			return ruleName;
		}
		if (token.startsWith(TOPIC_SEGMENTS) && token.endsWith("]")) {
			String index = token.substring(TOPIC_SEGMENTS.length(), token.length() - 1);
			try {
				int i = Integer.parseInt(index);
				if (i >= 0 && i < parts.length) {
					return parts[i];
				}
			} catch (NumberFormatException ignored) {
				// 非数字下标视为未识别的占位符
			}
		}
		return null;
	}
}
