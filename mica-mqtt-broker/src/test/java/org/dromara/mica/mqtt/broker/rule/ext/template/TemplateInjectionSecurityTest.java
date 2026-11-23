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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 规则模板表达式注入的回归测试。
 * <p>
 * 消息内容（topic / clientId / payload）来自外部发布者，属于不可信输入；
 * 规则模板属于运维写下的可信配置。二者一旦混在同一次「替换 + 求值」里，
 * 攻击者就能把自己的表达式塞进 topic，让 broker 替他求值：
 * 可读到操作系统环境变量，也可读到规则动作里的 MQTT 账号与 webhook 凭据。
 * </p>
 *
 * @author L.cm
 */
class TemplateInjectionSecurityTest {

	private static final Gson JSON = new Gson();
	private static final String SECRET = "s3cr3t-mqtt-password";

	private AlertCenter center;
	private AlertTemplateAction.Factory factory;
	private AlertEvent[] received;

	@BeforeEach
	void setUp() {
		center = new AlertCenter();
		TemplateServices services = new TemplateServices(null, null, center);
		factory = new AlertTemplateAction.Factory();
		factory.setTemplateServices(services);
		received = new AlertEvent[1];
		center.addNotifier(new AlertNotifier() {
			@Override
			public String getName() {
				return "capture";
			}

			@Override
			public void send(AlertEvent event) {
				received[0] = event;
			}
		});
	}

	@AfterEach
	void tearDown() {
		center.shutdown();
	}

	/**
	 * topic 里的 {@code ${...}} 不能被当成表达式求值。
	 * <p>
	 * 修复前：模板先做 {@code {topic}} 替换、再做 {@code ${}} 求值，
	 * topic 中的 {@code ${1+1}} 会被算出 2 —— 换成 {@code env('PATH')} 就是环境变量外泄。
	 * </p>
	 */
	@Test
	void topicContentIsNotEvaluatedAsExpression() throws Exception {
		ActionRef ref = ActionRef.builder("alert")
			.name("inject")
			.prop("title", "T-{topic}")
			.build();

		factory.create(ref).send(ctx("dev-001", "sensor/${1+1}", "{}", ruleWithSecret()));
		awaitEvent();

		Assertions.assertEquals("T-sensor/${1+1}", received[0].getTitle(),
			"topic 中的表达式应原样保留，不得被求值");
		Assertions.assertFalse(received[0].getTitle().contains("T-sensor/2"),
			"topic 中的表达式不得被求值");
	}

	/**
	 * 表达式环境里的 {@code rule} 不得暴露动作属性（凭据所在）。
	 */
	@Test
	void expressionEnvDoesNotExposeActionProps() throws Exception {
		ActionRef ref = ActionRef.builder("alert")
			.name("secret")
			.prop("title", "P-${rule.actions[0].props.password}")
			.build();

		factory.create(ref).send(ctx("dev-001", "sensor/x", "{}", ruleWithSecret()));
		awaitEvent();

		Assertions.assertFalse(received[0].getTitle().contains(SECRET),
			"表达式环境不得暴露规则动作属性中的凭据，实际标题: " + received[0].getTitle());
	}

	/**
	 * 收紧 {@code rule} 之后，{@code rule.name} 这类只读信息仍要可用。
	 */
	@Test
	void ruleNameRemainsAccessible() throws Exception {
		ActionRef ref = ActionRef.builder("alert")
			.name("name")
			.prop("title", "R-${rule.name}")
			.build();

		factory.create(ref).send(ctx("dev-001", "sensor/x", "{}", ruleWithSecret()));
		awaitEvent();

		Assertions.assertEquals("R-rule-a", received[0].getTitle(),
			"rule.name 应仍可从表达式环境读取");
	}

	/**
	 * 模板渲染只做一次替换：替换进来的 topic 若本身长得像占位符，不应被二次展开。
	 */
	@Test
	void substitutedValueIsNotRescanned() {
		RuleContext ctx = ctx("dev-001", "{clientId}", "{}", ruleWithSecret());
		String rendered = TemplateRenderer.render("X-{topic}", ctx);
		Assertions.assertEquals("X-{clientId}", rendered,
			"替换值不应被二次扫描，否则 topic 里的 {clientId} 会被展开");
	}

	/**
	 * 占位符与表达式的组合：{@code ${}} 先求值，{@code {}} 后替换。
	 */
	@Test
	void expressionIsEvaluatedBeforePlaceholderSubstitution() throws Exception {
		ActionRef ref = ActionRef.builder("alert")
			.name("combo")
			.prop("title", "V-${payload.value}-{topic}")
			.build();

		factory.create(ref).send(ctx("dev-001", "sensor/x", "{\"value\":42}", ruleWithSecret()));
		awaitEvent();

		Assertions.assertTrue(received[0].getTitle().startsWith("V-42"),
			"表达式应正常求值，实际标题: " + received[0].getTitle());
		Assertions.assertTrue(received[0].getTitle().endsWith("-sensor/x"),
			"占位符应正常替换，实际标题: " + received[0].getTitle());
	}

	private static Rule ruleWithSecret() {
		ActionRef action = ActionRef.builder("mqtt")
			.name("forward")
			.prop("password", SECRET)
			.build();
		return Rule.builder()
			.name("rule-a")
			.topicFilter("sensor/#")
			.addAction(action)
			.build();
	}

	private static RuleContext ctx(String clientId, String topic, String payload, Rule rule) {
		byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
		RuleContext ctx = new RuleContext(
			(ChannelContext) null,
			new RuleChannelInfo("127.0.0.1", 0, "test-node"),
			clientId, topic, MqttQoS.QOS1, bytes, false, Collections.emptyMap(), rule);
		ctx.attr("payload", JSON.fromJson(payload, Map.class));
		return ctx;
	}

	private void awaitEvent() throws InterruptedException {
		long deadline = System.currentTimeMillis() + 3000L;
		while (System.currentTimeMillis() < deadline) {
			if (received[0] != null) {
				return;
			}
			Thread.sleep(20L);
		}
		Assertions.fail("告警事件未在超时前送达");
	}
}
