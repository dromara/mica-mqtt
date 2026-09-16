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
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * alert 模板：触发告警事件。
 *
 * <p>YAML 配置：
 * <pre>
 * - type: alert
 *   name: overheat
 *   props:
 *     severity: critical
 *     title: "设备 {clientId} 温度过高"
 *     message: "${payload.temperature}"
 *     dedupeKey: "{clientId}:overheat"
 * </pre>
 *
 * <p>{@link AlertCenter} 由装配流程通过 {@link TemplateServices} 注入。
 *
 * @author L.cm
 */
public class AlertTemplateAction implements Action {

	private final ActionRef ref;
	private final AlertCenter center;

	public AlertTemplateAction(ActionRef ref, AlertCenter center) {
		this.ref = ref;
		this.center = center;
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	@Override
	public void send(RuleContext ctx) {
		if (center == null) {
			throw new IllegalStateException("alert action is not wired with an AlertCenter");
		}
		String severity = ref.getString("severity", "warning");
		String title = TemplateExpressions.interpolate(
			TemplateRenderer.render(ref.getString("title", ctx.getRule().getName()), ctx), ctx);
		String message = TemplateExpressions.interpolate(
			TemplateRenderer.render(ref.getString("message", ""), ctx), ctx);
		List<String> tags = parseTags(ctx);
		String dedupeKey = TemplateRenderer.render(ref.getString("dedupeKey", ""), ctx);
		Map<String, Object> extra = parseExtra(ctx);
		center.trigger(new AlertEvent(System.currentTimeMillis(), severity, title, message,
			tags, dedupeKey, extra));
	}

	@SuppressWarnings("unchecked")
	private List<String> parseTags(RuleContext ctx) {
		Object o = ref.getProps().get("tags");
		if (!(o instanceof List)) {
			return Collections.emptyList();
		}
		List<String> raw = (List<String>) o;
		List<String> rendered = new ArrayList<>(raw.size());
		for (String s : raw) {
			rendered.add(TemplateRenderer.render(s, ctx));
		}
		return rendered;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseExtra(RuleContext ctx) {
		Map<String, Object> extra = new LinkedHashMap<>();
		Object o = ref.getProps().get("extra");
		if (o instanceof Map) {
			Map<String, Object> raw = (Map<String, Object>) o;
			for (Map.Entry<String, Object> e : raw.entrySet()) {
				Object v = e.getValue();
				extra.put(e.getKey(), v instanceof String ? TemplateRenderer.render((String) v, ctx) : v);
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
	public static class Factory implements TemplateActionFactory {
		private volatile TemplateServices services;

		@Override
		public String getType() {
			return "alert";
		}

		@Override
		public void setTemplateServices(TemplateServices services) {
			this.services = services;
		}

		@Override
		public Action create(ActionRef ref) {
			TemplateServices current = services;
			return new AlertTemplateAction(ref, current == null ? null : current.getAlertCenter());
		}
	}
}
