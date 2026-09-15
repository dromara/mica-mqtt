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
 * 简单模板渲染：替换 {@code {topic}} / {@code {clientId}} / {@code {rule.name}} / {@code {topicSegments[i]}}。
 *
 * @author L.cm
 */
public final class TemplateRenderer {

	private TemplateRenderer() {
	}

	public static String render(String template, RuleContext ctx) {
		if (template == null) {
			return "";
		}
		String out = template;
		out = out.replace("{topic}", ctx.getTopic() == null ? "" : ctx.getTopic());
		out = out.replace("{clientId}", ctx.getClientId() == null ? "" : ctx.getClientId());
		out = out.replace("{rule.name}", ctx.getRule().getName() == null ? "" : ctx.getRule().getName());
		String topic = ctx.getTopic() == null ? "" : ctx.getTopic();
		String[] parts = topic.split("/");
		for (int i = 0; i < parts.length; i++) {
			out = out.replace("{topicSegments[" + i + "]}", parts[i]);
		}
		return out;
	}
}