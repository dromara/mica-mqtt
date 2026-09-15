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

import com.google.gson.Gson;
import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.RuleChannelInfo;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Action 模板 SPI 工厂与基础行为单测。
 *
 * @author L.cm
 */
class TemplateActionsTest {

	private static final Gson JSON = new Gson();

	private static RuleContext ctx(String clientId, String topic, String payload) {
		Rule rule = Rule.builder().topicFilter("test").name("rule-a").build();
		byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
		RuleContext ctx = new RuleContext(
			(ChannelContext) null,
			new RuleChannelInfo("127.0.0.1", 0, "test-node"),
			clientId, topic, MqttQoS.QOS1, bytes, false, Collections.emptyMap(), rule);
		ctx.attr("payload", JSON.fromJson(payload, Map.class));
		return ctx;
	}

	@Test
	void templateRenderer() {
		RuleContext ctx = ctx("dev-001", "sensor/01/temp",
			"{\"temperature\":85}");
		String r = TemplateRenderer.render(
			"cloud/{clientId}/{topicSegments[2]}/{rule.name}", ctx);
		assertEquals("cloud/dev-001/temp/rule-a", r);
	}

	@Test
	void publishTemplateRequiresPublisher() {
		// 强制清空 publisher 以验证缺省时抛错
		PublishTemplateAction.setPublisher(null);
		ActionRef ref = ActionRef.builder(PublishTemplateAction.class.getSimpleName())
			.prop("topic", "x/{topic}").build();
		try {
			new PublishTemplateAction.Factory().create(ref).send(ctx("c", "a/b", "{}"));
			assertTrue(false, "expected IllegalStateException");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("setPublisher"));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void publishTemplateRender() throws Exception {
		// 注入 publisher
		final String[] captured = new String[4];
		PublishTemplateAction.setPublisher((topic, payload, qos, retain) -> {
			captured[0] = topic;
			captured[1] = new String(payload, StandardCharsets.UTF_8);
			captured[2] = String.valueOf(qos);
			captured[3] = String.valueOf(retain);
		});
		ActionRef ref = ActionRef.builder("publish")
			.name("reformat")
			.prop("topic", "v2/{topicSegments[2]}")
			.prop("qos", 1)
			.prop("retain", false)
			.build();
		new PublishTemplateAction.Factory().create(ref).send(
			ctx("dev-007", "sensor/01/temp", "{\"x\":1}"));
		assertEquals("v2/temp", captured[0]);
		assertEquals("{\"x\":1}", captured[1]);
		assertEquals("1", captured[2]);
		assertEquals("false", captured[3]);
	}

	@Test
	void storeTemplateFallback() throws Exception {
		StoreTemplateAction.MemoryStoreFunction mem = new StoreTemplateAction.MemoryStoreFunction();
		StoreTemplateAction.setFallback(mem);
		ActionRef ref = ActionRef.builder("store")
			.name("history")
			.prop("maxRows", 5)
			.build();
		StoreTemplateAction action = (StoreTemplateAction) new StoreTemplateAction.Factory().create(ref);
		for (int i = 0; i < 8; i++) {
			action.send(ctx("c", "sensor/x", "{\"i\":" + i + "}"));
		}
		List<StoreTemplateAction.Record> recent = mem.recent("history", 100);
		assertEquals(5, recent.size());
		// ring buffer 保留最新的 5 条
		assertTrue(new String(recent.get(0).getPayload()).contains("i\":3"));
		assertTrue(new String(recent.get(4).getPayload()).contains("i\":7"));
	}

	@Test
	void alertTemplateTrigger() throws Exception {
		AlertCenter center = new AlertCenter();
		AlertTemplateAction.setCenter(center);
		final AlertEvent[] received = new AlertEvent[1];
		center.addNotifier(new AlertNotifier() {
			@Override
			public String getName() { return "n"; }
			@Override
			public void send(AlertEvent event) { received[0] = event; }
		});
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("severity", "critical");
		props.put("title", "alert-{clientId}");
		props.put("message", "${payload.value}");
		props.put("dedupeKey", "{clientId}:v");
		ActionRef ref = ActionRef.builder("alert").name("a").props(props).build();
		new AlertTemplateAction.Factory().create(ref).send(
			ctx("dev-007", "alert/x", "{\"value\":42}"));
		Thread.sleep(200);
		assertNotNull(received[0]);
		assertEquals("alert-dev-007", received[0].getTitle());
		// aviator 默认数值字面量为 double，序列化结果为 "42.0"
		assertTrue(received[0].getMessage().startsWith("42"));
		center.shutdown();
	}
}