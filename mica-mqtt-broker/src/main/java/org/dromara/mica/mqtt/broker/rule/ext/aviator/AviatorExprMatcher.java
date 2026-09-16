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

package org.dromara.mica.mqtt.broker.rule.ext.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcher;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcherFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aviator 表达式 matcher。
 *
 * @author L.cm
 */
public class AviatorExprMatcher {

	private static final AviatorEvaluatorInstance EVAL = AviatorEvaluator.newInstance();

	static {
		EVAL.enableSandboxMode();
		EVAL.setOption(Options.MAX_LOOP_COUNT, 1000);
	}

	private final String expression;
	private final Expression compiled;

	public AviatorExprMatcher(String expression) {
		this.expression = expression;
		this.compiled = EVAL.compile(expression, true);
	}

	public String getExpression() {
		return expression;
	}

	public Object execute(Map<String, Object> env) {
		return compiled.execute(env);
	}

	public static Map<String, Object> envOf(RuleContext ctx) {
		Map<String, Object> env = new HashMap<>();
		Object payload = ctx.getAttributes().get("payload");
		if (payload == null) {
			payload = ctx.getPayload();
		}
		env.put("payload", payload);
		env.put("payloadBytes", ctx.getPayload());
		env.put("clientId", ctx.getClientId());
		env.put("topic", ctx.getTopic());
		env.put("topicSegments", splitTopic(ctx.getTopic()));
		env.put("qos", ctx.getQos() == null ? 0 : ctx.getQos().value());
		env.put("retain", ctx.isRetain());
		env.put("headers", ctx.getHeaders());
		env.put("rule", ctx.getRule());
		return env;
	}

	public static boolean asBool(Object result) {
		if (result instanceof Boolean) {
			return (Boolean) result;
		}
		return false;
	}

	private static List<String> splitTopic(String topic) {
		if (topic == null || topic.isEmpty()) {
			return Collections.emptyList();
		}
		return Arrays.asList(topic.split("/"));
	}

	/**
	 * 工厂：把 aviator 表达式 matcher 注册到现有 RuleMatcherRegistry（type=aviator）。
	 */
	public static class Factory implements RuleMatcherFactory {
		@Override
		public String getType() {
			return "aviator";
		}

		@Override
		public RuleMatcher create(Map<String, String> props) {
			String when = props == null ? null : props.get("when");
			if (when == null || when.isEmpty()) {
				throw new IllegalArgumentException("aviator matcher requires 'when' property");
			}
			AviatorExprMatcher delegate = new AviatorExprMatcher(when);
			return ctx -> asBool(delegate.execute(envOf(ctx)));
		}
	}
}
