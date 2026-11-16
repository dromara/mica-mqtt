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
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.googlecode.aviator.runtime.type.AviatorString;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcher;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcherFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AviatorScript 条件匹配器。
 * <p>
 * 表达式在构造期编译一次（{@code compile(expr, true)} 走 Aviator 的 LRU 表达式缓存），
 * 匹配期只求值。求值环境由 {@link #envOf(RuleContext)} 组装，包含 payload / topic /
 * clientId / headers / ts 等内置变量，并注册 {@code header()}、{@code contains()}、
 * {@code now()}、{@code env()} 等便捷函数。
 * </p>
 *
 * @author L.cm
 */
public class AviatorExprMatcher {

	/**
	 * 表达式缓存容量。
	 */
	private static final int EXPRESSION_CACHE_SIZE = 1024;

	/**
	 * 单次求值的最大循环次数，防止表达式死循环。
	 */
	private static final int MAX_LOOP_COUNT = 1000;

	private static final AviatorEvaluatorInstance EVAL = AviatorEvaluator.newInstance();

	static {
		// 沙箱模式：禁用 Module / NewInstance / StaticMethods / InternalVars 与反射，
		// 同时把求值超时限制在 1 秒
		EVAL.enableSandboxMode();
		EVAL.setOption(Options.MAX_LOOP_COUNT, MAX_LOOP_COUNT);
		// 编译结果常驻内存，避免每条消息重复编译
		EVAL.useLRUExpressionCache(EXPRESSION_CACHE_SIZE);
		registerFunctions(EVAL);
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

	/**
	 * 组装表达式求值环境。
	 *
	 * @param ctx 规则上下文
	 * @return 环境变量
	 */
	public static Map<String, Object> envOf(RuleContext ctx) {
		Map<String, Object> env = new HashMap<>();
		Object payload = ctx.attr(RuleContext.PAYLOAD_ATTR);
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
		env.put("ts", System.currentTimeMillis());
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
	 * 注册 broker 自定义函数。
	 */
	private static void registerFunctions(AviatorEvaluatorInstance evaluator) {
		evaluator.addFunction(new HeaderFunction());
		evaluator.addFunction(new ContainsFunction());
		evaluator.addFunction(new NowFunction());
		evaluator.addFunction(new EnvFunction());
	}

	/**
	 * 把 Java 值转成 Aviator 可用对象。
	 */
	private static AviatorObject wrap(Object value) {
		if (value == null) {
			return AviatorNil.NIL;
		}
		if (value instanceof String) {
			return new AviatorString((String) value);
		}
		if (value instanceof Boolean) {
			return (Boolean) value ? AviatorBoolean.TRUE : AviatorBoolean.FALSE;
		}
		return AviatorRuntimeJavaType.valueOf(value);
	}

	/**
	 * {@code header(name)} / {@code header(name, defaultValue)}：读取消息头。
	 */
	private static final class HeaderFunction extends AbstractFunction {
		@Override
		public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
			String name = FunctionUtils.getStringValue(arg1, env);
			return wrap(headerValue(env, name, null));
		}

		@Override
		public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
			String name = FunctionUtils.getStringValue(arg1, env);
			String defaultValue = FunctionUtils.getStringValue(arg2, env);
			return wrap(headerValue(env, name, defaultValue));
		}

		@Override
		public String getName() {
			return "header";
		}
	}

	/**
	 * {@code contains(haystack, needle)}：字符串包含。
	 */
	private static final class ContainsFunction extends AbstractFunction {
		@Override
		public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
			String haystack = FunctionUtils.getStringValue(arg1, env);
			String needle = FunctionUtils.getStringValue(arg2, env);
			boolean result = haystack != null && needle != null && haystack.contains(needle);
			return result ? AviatorBoolean.TRUE : AviatorBoolean.FALSE;
		}

		@Override
		public String getName() {
			return "contains";
		}
	}

	/**
	 * {@code now()}：当前时间戳（毫秒）。
	 */
	private static final class NowFunction extends AbstractFunction {
		@Override
		public AviatorObject call(Map<String, Object> env) {
			return AviatorRuntimeJavaType.valueOf(System.currentTimeMillis());
		}

		@Override
		public String getName() {
			return "now";
		}
	}

	/**
	 * {@code env(key)} / {@code env(key, defaultValue)}：读取环境变量。
	 * <p>
	 * 沙箱模式下系统属性访问受限，读不到时返回默认值。
	 * </p>
	 */
	private static final class EnvFunction extends AbstractFunction {
		@Override
		public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
			String key = FunctionUtils.getStringValue(arg1, env);
			return wrap(System.getenv(key));
		}

		@Override
		public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
			String key = FunctionUtils.getStringValue(arg1, env);
			String defaultValue = FunctionUtils.getStringValue(arg2, env);
			String value = System.getenv(key);
			return wrap(value == null ? defaultValue : value);
		}

		@Override
		public String getName() {
			return "env";
		}
	}

	private static String headerValue(Map<String, Object> env, String name, String defaultValue) {
		if (name == null) {
			return defaultValue;
		}
		Object headers = env.get("headers");
		if (headers instanceof Map) {
			Object value = ((Map<?, ?>) headers).get(name);
			return value == null ? defaultValue : String.valueOf(value);
		}
		return defaultValue;
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
