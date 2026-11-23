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
import org.dromara.mica.mqtt.broker.rule.RuleChannelInfo;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置 matcher 工厂单测：header / jsonPath / compound。
 *
 * @author L.cm
 */
class RuleMatcherFactoriesTest {

	private static RuleContext context(String payload, Map<String, String> headers) {
		return new RuleContext(null, new RuleChannelInfo(null, 0, null),
			"client1", "device/001/sensor", MqttQoS.QOS0,
			payload == null ? new byte[0] : payload.getBytes(), false,
			headers == null ? Collections.emptyMap() : headers, null);
	}

	private static Map<String, String> props(String... kv) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	@Test
	void headerMatcherComparesValue() {
		RuleMatcher matcher = new HeaderRuleMatcherFactory()
			.create(props("key", "region", "op", "eq", "value", "cn-east"));
		assertTrue(matcher.matches(context("x", props("region", "cn-east"))));
		assertFalse(matcher.matches(context("x", props("region", "cn-west"))));
	}

	@Test
	void headerMatcherSupportsContainsAndExists() {
		RuleMatcher contains = new HeaderRuleMatcherFactory()
			.create(props("key", "region", "op", "contains", "value", "east"));
		assertTrue(contains.matches(context("x", props("region", "cn-east"))));

		RuleMatcher exists = new HeaderRuleMatcherFactory()
			.create(props("key", "region", "op", "exists"));
		assertFalse(exists.matches(context("x", Collections.emptyMap())));
	}

	@Test
	void headerMatcherRequiresKey() {
		try {
			new HeaderRuleMatcherFactory().create(Collections.emptyMap());
			org.junit.jupiter.api.Assertions.fail("should throw for missing key");
		} catch (IllegalArgumentException expected) {
			// 预期：缺少 key 时启动期报错
		}
	}

	@Test
	void jsonPathMatcherReadsDecodedPayload() {
		RuleContext ctx = context("{}", Collections.emptyMap());
		ctx.attr(RuleContext.PAYLOAD_ATTR, nested());
		RuleMatcher matcher = new JsonPathRuleMatcherFactory()
			.create(props("path", "device.temperature", "op", "gt", "value", "80"));
		assertTrue(matcher.matches(ctx));

		RuleMatcher arrayMatcher = new JsonPathRuleMatcherFactory()
			.create(props("path", "tags[1]", "op", "eq", "value", "urgent"));
		assertTrue(arrayMatcher.matches(ctx));
	}

	@Test
	void jsonPathMatcherReturnsFalseWhenPathMissing() {
		RuleContext ctx = context("{}", Collections.emptyMap());
		ctx.attr(RuleContext.PAYLOAD_ATTR, nested());
		RuleMatcher matcher = new JsonPathRuleMatcherFactory()
			.create(props("path", "device.missing", "op", "exists"));
		assertFalse(matcher.matches(ctx));
	}

	@Test
	void compoundMatcherCombinesNamedMatchers() {
		MatcherRegistry registry = new MatcherRegistry();
		registry.registerMatcher("prod", ctx -> "prod".equals(ctx.getHeaders().get("env")));
		registry.registerMatcher("hot", ctx -> {
			Object payload = ctx.attr(RuleContext.PAYLOAD_ATTR);
			return payload instanceof Map
				&& String.valueOf(((Map<?, ?>) payload).get("level")).equals("high");
		});
		registry.registerFactory(new CompoundRuleMatcherFactory());

		RuleMatcher compound = registry.get("compound", props("and", "prod,hot"));
		RuleContext ctx = context("{}", props("env", "prod"));
		ctx.attr(RuleContext.PAYLOAD_ATTR, java.util.Collections.singletonMap("level", "high"));
		assertTrue(compound.matches(ctx));

		RuleContext cold = context("{}", props("env", "prod"));
		cold.attr(RuleContext.PAYLOAD_ATTR, java.util.Collections.singletonMap("level", "low"));
		assertFalse(compound.matches(cold));
	}

	@Test
	void compoundMatcherRejectsUnknownName() {
		MatcherRegistry registry = new MatcherRegistry();
		registry.registerFactory(new CompoundRuleMatcherFactory());
		try {
			registry.get("compound", props("and", "missing"));
			org.junit.jupiter.api.Assertions.fail("should throw for unknown matcher");
		} catch (IllegalArgumentException expected) {
			// 预期：引用未注册的 matcher 时启动期报错
		}
	}

	private static Map<String, Object> nested() {
		Map<String, Object> device = new HashMap<>();
		device.put("temperature", 95);
		Map<String, Object> root = new HashMap<>();
		root.put("device", device);
		root.put("tags", java.util.Arrays.asList("normal", "urgent"));
		return root;
	}
}
