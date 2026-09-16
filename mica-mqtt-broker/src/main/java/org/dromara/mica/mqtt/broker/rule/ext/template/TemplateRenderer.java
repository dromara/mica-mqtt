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
 *
 * @author L.cm
 */
public final class TemplateRenderer {

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
		String out = template;
		out = out.replace("{topic}", topic);
		out = out.replace("{clientId}", ctx.getClientId() == null ? "" : ctx.getClientId());
		out = out.replace("{rule.name}", ctx.getRule().getName() == null ? "" : ctx.getRule().getName());
		String[] parts = topic.split("/");
		for (int i = 0; i < parts.length; i++) {
			out = out.replace("{topicSegments[" + i + "]}", parts[i]);
		}
		return out;
	}
}
