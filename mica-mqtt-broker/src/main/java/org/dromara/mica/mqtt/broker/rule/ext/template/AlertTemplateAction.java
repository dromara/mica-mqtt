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
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * alert 模板：触发告警事件。
 *
 * @author L.cm
 */
public class AlertTemplateAction implements Action {

	private static final AtomicReference<AlertCenter> CENTER = new AtomicReference<>();

	private final ActionRef ref;

	public AlertTemplateAction(ActionRef ref) {
		this.ref = ref;
	}

	public static void setCenter(AlertCenter center) {
		CENTER.set(center);
	}

	public static AlertCenter getCenter() {
		return CENTER.get();
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	@Override
	public void send(RuleContext ctx) {
		AlertCenter center = CENTER.get();
		if (center == null) {
			throw new IllegalStateException("AlertCenter not configured");
		}
		String severity = ActionRefs.getString(ref, "severity", "warning");
		String title = interpolate(
			TemplateRenderer.render(
				ActionRefs.getString(ref, "title", ctx.getRule().getName()), ctx), ctx);
		String message = interpolate(
			TemplateRenderer.render(
				ActionRefs.getString(ref, "message", ""), ctx), ctx);
		List<String> tags = parseTags(ctx);
		String dedupeKey = TemplateRenderer.render(ActionRefs.getString(ref, "dedupeKey", ""), ctx);
		Map<String, Object> extra = parseExtra(ctx);
		AlertEvent event = new AlertEvent(System.currentTimeMillis(), severity, title, message,
			tags, dedupeKey, extra);
		center.trigger(event);
	}

	/**
	 * 把 {@code ${expr}} 替换为 Aviator 求值结果；普通字面量保持原样。
	 */
	private static String interpolate(String template, RuleContext ctx) {
		if (template == null || template.indexOf("${") < 0) {
			return template == null ? "" : template;
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("\\$\\{([^}]+)\\}").matcher(template);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String expr = matcher.group(1);
			Object value;
			try {
				AviatorExprMatcher m = new AviatorExprMatcher(expr);
				value = m.execute(AviatorExprMatcher.envOf(ctx));
			} catch (Exception e) {
				value = "";
			}
			matcher.appendReplacement(sb,
				java.util.regex.Matcher.quoteReplacement(value == null ? "" : value.toString()));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private List<String> parseTags(RuleContext ctx) {
		Object o = ref.getProps().get("tags");
		if (o instanceof List) {
			List<String> raw = (List<String>) o;
			List<String> rendered = new ArrayList<>(raw.size());
			for (String s : raw) {
				rendered.add(TemplateRenderer.render(s, ctx));
			}
			return rendered;
		}
		return Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseExtra(RuleContext ctx) {
		Map<String, Object> extra = new LinkedHashMap<>();
		Object o = ref.getProps().get("extra");
		if (o instanceof Map) {
			Map<String, Object> raw = (Map<String, Object>) o;
			for (Map.Entry<String, Object> e : raw.entrySet()) {
				Object v = e.getValue();
				if (v instanceof String) {
					extra.put(e.getKey(), TemplateRenderer.render((String) v, ctx));
				} else {
					extra.put(e.getKey(), v);
				}
			}
		}
		Object w = ref.getProps().get("dedupeWindowMs");
		if (w != null) {
			extra.put("dedupeWindowMs", w);
		}
		return extra;
	}

	/**
	 * ActionFactory：注册 type=alert。
	 */
	public static class Factory implements ActionFactory {
		@Override
		public String getType() {
			return "alert";
		}

		@Override
		public Action create(ActionRef ref) {
			return new AlertTemplateAction(ref);
		}
	}
}