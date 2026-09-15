/*
 * Copyright (c) 2019-2029, Dreamlu 卢春�?(596392912@qq.com & dreamlu.net).
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

import com.google.gson.Gson;
import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.RuleChannelInfo;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aviator 表达�?matcher 单测�? *
 * @author L.cm
 */
class AviatorExprMatcherTest {

	private static final Gson JSON = new Gson();

	private static RuleContext ctx(String clientId, String topic, byte[] payload, MqttQoS qos) {
		Rule rule = Rule.builder().topicFilter("test").name("rule-a").build();
		RuleContext ctx = new RuleContext(
			(ChannelContext) null,
			new RuleChannelInfo("127.0.0.1", 0, "test-node"),
			clientId, topic, qos, payload, false, Collections.emptyMap(), rule);
		ctx.attr("payload", JSON.fromJson(new String(payload, StandardCharsets.UTF_8), Map.class));
		return ctx;
	}

	@Test
	void numericCompare() {
		AviatorExprMatcher matcher = new AviatorExprMatcher("payload.temperature > 80");
		RuleContext ctx = ctx("dev-001", "sensor/01/temp",
			"{\"temperature\":85}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS1);
		assertTrue(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(ctx))));

		RuleContext ctx2 = ctx("dev-002", "sensor/02/temp",
			"{\"temperature\":50}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS1);
		assertFalse(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(ctx2))));
	}

	@Test
	void stringCompare() {
		AviatorExprMatcher matcher = new AviatorExprMatcher("payload.status == 'offline'");
		RuleContext on = ctx("dev", "dev/status", "{\"status\":\"online\"}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0);
		RuleContext off = ctx("dev", "dev/status", "{\"status\":\"offline\"}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0);
		assertFalse(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(on))));
		assertTrue(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(off))));
	}

	@Test
	void topicAndQos() {
		AviatorExprMatcher matcher = new AviatorExprMatcher("topicSegments[0] == 'sensor' && qos >= 1");
		RuleContext hi = ctx("dev", "sensor/01/temp", "{}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS1);
		RuleContext low = ctx("dev", "sensor/01/temp", "{}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0);
		RuleContext other = ctx("dev", "device/01/temp", "{}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS1);
		assertTrue(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(hi))));
		assertFalse(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(low))));
		assertFalse(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(other))));
	}

	@Test
	void nullFieldSafe() {
		AviatorExprMatcher matcher = new AviatorExprMatcher("payload.missing == nil");
		RuleContext ctx = ctx("dev", "x", "{}".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0);
		assertTrue(AviatorExprMatcher.asBool(matcher.execute(AviatorExprMatcher.envOf(ctx))));
	}
}