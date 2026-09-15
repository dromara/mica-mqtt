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

package org.dromara.mica.mqtt.broker.rule.action;

import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ActionRegistry 单测：工厂注册 + 缓存共享。
 *
 * @author L.cm
 */
class ActionRegistryTest {

	@Test
	void materializeCachesByRef() {
		ActionRegistry registry = new ActionRegistry();
		AtomicInteger factoryCount = new AtomicInteger();
		registry.registerFactory(new ActionFactory() {
			@Override
			public String getType() {
				return "fake";
			}

			@Override
			public Action create(ActionRef ref) {
				factoryCount.incrementAndGet();
				return new Action() {
					@Override
					public String getName() {
						return ref.getName();
					}

					@Override
					public void send(RuleContext ctx) {
					}
				};
			}
		});
		ActionRef ref = ActionRef.of("fake", "f1");
		Action first = registry.materialize(ref);
		Action second = registry.materialize(ref);
		assertSame(first, second);
		assertEquals(1, factoryCount.get());
	}

	@Test
	void differentRefsDifferentInstances() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerFactory(new ActionFactory() {
			@Override
			public String getType() {
				return "fake";
			}

			@Override
			public Action create(ActionRef ref) {
				return new Action() {
					@Override
					public String getName() {
						return ref.getName();
					}

					@Override
					public void send(RuleContext ctx) {
					}
				};
			}
		});
		ActionRef r1 = ActionRef.builder("fake").prop("x", "1").build();
		ActionRef r2 = ActionRef.builder("fake").prop("x", "2").build();
		assertNotNull(registry.materialize(r1));
		assertNotNull(registry.materialize(r2));
	}

	@Test
	void missingTypeThrows() {
		ActionRegistry registry = new ActionRegistry();
		ActionRef ref = ActionRef.of("missing");
		assertThrows(IllegalStateException.class, () -> registry.materialize(ref));
	}

	@Test
	void ruleContextBasic() {
		Rule rule = Rule.builder().id("r1").topicFilter("a").build();
		RuleContext ctx = new RuleContext(
			null, null, "client1", "sensor/temp",
			MqttQoS.QOS1, new byte[]{1, 2, 3}, false, null, rule);
		assertEquals("client1", ctx.getClientId());
		assertEquals("sensor/temp", ctx.getTopic());
		assertEquals(MqttQoS.QOS1, ctx.getQos());
		assertEquals(3, ctx.getPayload().length);
		ctx.attr("k", "v");
		assertEquals("v", ctx.attr("k"));
	}
}
