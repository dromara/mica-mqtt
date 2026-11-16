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

package org.dromara.mica.mqtt.broker.rule;

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
import org.dromara.mica.mqtt.broker.rule.action.ActionRegistry;
import org.dromara.mica.mqtt.broker.rule.store.InMemoryRuleStore;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleEngine 单测：action 顺序执行 / 失败继续 / 运行时增删 / 缓存共享 / 超时 / 解码。
 * <p>
 * 规则命中后整条 action 链在 action 线程池内串行执行，因此断言需要等待任务完成。
 * </p>
 *
 * @author L.cm
 */
class RuleEngineTest {

	/**
	 * 等待异步 action 执行完成的超时时间（毫秒）。
	 */
	private static final long AWAIT_TIMEOUT_MS = 5000L;

	private static MqttPublishMessage publishMessage(String topic, byte[] payload, boolean retain) {
		MqttFixedHeader header = new MqttFixedHeader(
			org.dromara.mica.mqtt.codec.MqttMessageType.PUBLISH, false,
			MqttQoS.QOS0, retain, 0);
		MqttPublishVariableHeader vh = new MqttPublishVariableHeader(topic, 0);
		return new MqttPublishMessage(header, vh, payload);
	}

	private static RuleManager newRuleManager(List<String> actionEvents) {
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		AtomicInteger counter = new AtomicInteger();
		ActionFactory seq = new ActionFactory() {
			@Override
			public String getType() {
				return "seq";
			}

			@Override
			public Action create(ActionRef ref) {
				String name = ref.getName();
				int seq = counter.incrementAndGet();
				return new Action() {
					@Override
					public String getName() {
						return name == null ? "seq-" + seq : name;
					}

					@Override
					public void send(RuleContext ctx) {
						actionEvents.add(getName() + ":" + new String(ctx.getPayload()));
					}
				};
			}
		};
		ActionRegistry sr = rm.getActionRegistry();
		sr.registerFactory(seq);
		return rm;
	}

	/**
	 * 等待事件列表达到期望条数。
	 *
	 * @param events  事件列表
	 * @param expect 期望条数
	 */
	private static void awaitSize(List<String> events, int expect) {
		long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
		while (events.size() < expect && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(10L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	@Test
	void sinkOrderAndRuntimeAddRemove() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();

		rm.setRuleStore(new InMemoryRuleStore());
		engine.start();

		// 第一次添加：rule r1 含两个 action
		Rule r1 = Rule.builder()
			.id("r1")
			.name("r1")
			.topicFilter("sensor/+/temp")
			.addAction(ActionRef.of("seq", "s1"))
			.addAction(ActionRef.of("seq", "s2"))
			.build();
		rm.addRule(r1);
		// 触发一次消息
		invokeFunctionManager(fnMgr, "sensor/room1/temp", "hello".getBytes(), false);
		awaitSize(events, 2);
		assertEquals(2, events.size());
		assertEquals("s1:hello", events.get(0));
		assertEquals("s2:hello", events.get(1));

		// 移除规则
		rm.removeRule("r1");
		events.clear();
		invokeFunctionManager(fnMgr, "sensor/room1/temp", "x".getBytes(), false);
		awaitSize(events, 0);
		assertTrue(events.isEmpty());

		// 重新添加
		events.clear();
		rm.addRule(Rule.builder()
			.id("r1")
			.topicFilter("sensor/+/temp")
			.addAction(ActionRef.of("seq", "s3"))
			.build());
		invokeFunctionManager(fnMgr, "sensor/room1/temp", "y".getBytes(), false);
		awaitSize(events, 1);
		assertEquals(1, events.size());
		assertEquals("s3:y", events.get(0));
	}

	@Test
	void sinkCacheSharedAcrossRules() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		// 两条规则共用相同的 ActionRef（type+name 一致）
		rm.addRule(Rule.builder()
			.id("a")
			.topicFilter("a/#")
			.addAction(ActionRef.of("seq", "shared"))
			.build());
		rm.addRule(Rule.builder()
			.id("b")
			.topicFilter("b/#")
			.addAction(ActionRef.of("seq", "shared"))
			.build());

		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		invokeFunctionManager(fnMgr, "b/y", "2".getBytes(), false);
		awaitSize(events, 2);
		assertEquals(2, events.size());
		assertEquals("shared:1", events.get(0));
		assertEquals("shared:2", events.get(1));
	}

	@Test
	void failedActionDoesNotStopOthersByDefault() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		AtomicInteger counter = new AtomicInteger();
		ActionFactory seq = new ActionFactory() {
			@Override
			public String getType() {
				return "mix";
			}

			@Override
			public Action create(ActionRef ref) {
				int n = counter.incrementAndGet();
				return new Action() {
					@Override
					public String getName() {
						return "mix-" + n;
					}

					@Override
					public void send(RuleContext ctx) {
						if (n == 1) {
							throw new RuntimeException("boom");
						}
						events.add(getName());
					}
				};
			}
		};
		rm.getActionRegistry().registerFactory(seq);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("r1")
			.topicFilter("a/#")
			.stopOnError(false)
			.addAction(ActionRef.of("mix", "first"))
			.addAction(ActionRef.of("mix", "second"))
			.build());

		invokeFunctionManager(fnMgr, "a/x", "".getBytes(), false);
		awaitSize(events, 1);
		assertEquals(1, events.size());
		assertEquals("mix-2", events.get(0));
	}

	@Test
	void stopOnErrorStopsSubsequent() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		AtomicInteger counter = new AtomicInteger();
		ActionFactory seq = new ActionFactory() {
			@Override
			public String getType() {
				return "stop";
			}

			@Override
			public Action create(ActionRef ref) {
				int n = counter.incrementAndGet();
				return new Action() {
					@Override
					public String getName() {
						return "stop-" + n;
					}

					@Override
					public void send(RuleContext ctx) {
						if (n == 1) {
							throw new RuntimeException("boom");
						}
						events.add(getName());
					}
				};
			}
		};
		rm.getActionRegistry().registerFactory(seq);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("r1")
			.topicFilter("a/#")
			.stopOnError(true)
			.addAction(ActionRef.of("stop", "first"))
			.addAction(ActionRef.of("stop", "second"))
			.build());

		invokeFunctionManager(fnMgr, "a/x", "".getBytes(), false);
		awaitSize(events, 0);
		assertTrue(events.isEmpty(), "stopOnError should stop subsequent actions");
	}

	@Test
	void disabledRuleDoesNotExecute() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("off")
			.topicFilter("a/#")
			.enabled(false)
			.addAction(ActionRef.of("seq", "x"))
			.build());
		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		awaitSize(events, 0);
		assertTrue(events.isEmpty());
	}

	@Test
	void unregisterRemovesRule() {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		Rule r1 = Rule.builder()
			.id("r1")
			.topicFilter("a/#")
			.addAction(ActionRef.of("seq", "s1"))
			.build();
		rm.addRule(r1);
		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		awaitSize(events, 1);
		assertEquals(1, events.size());

		rm.removeRule("r1");
		events.clear();
		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		awaitSize(events, 0);
		assertTrue(events.isEmpty());
	}

	/**
	 * 规则超时：超时后剩余 action 不再执行，并记一次失败。
	 */
	@Test
	void timeoutCancelsRemainingActions() throws Exception {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch started = new CountDownLatch(1);
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		ActionFactory slow = new ActionFactory() {
			@Override
			public String getType() {
				return "slow";
			}

			@Override
			public Action create(ActionRef ref) {
				return new Action() {
					@Override
					public String getName() {
						return "slow-1";
					}

					@Override
					public void send(RuleContext ctx) throws Exception {
						started.countDown();
						Thread.sleep(3000L);
						events.add("slow-done");
					}
				};
			}
		};
		rm.getActionRegistry().registerFactory(slow);
		rm.getActionRegistry().registerFactory(new ActionFactory() {
			@Override
			public String getType() {
				return "seq";
			}

			@Override
			public Action create(ActionRef ref) {
				return new Action() {
					@Override
					public String getName() {
						return "after";
					}

					@Override
					public void send(RuleContext ctx) {
						events.add("after");
					}
				};
			}
		});
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("slow-rule")
			.topicFilter("slow/#")
			.timeoutMs(300L)
			.addAction(ActionRef.of("slow", "s1"))
			.addAction(ActionRef.of("seq", "after"))
			.build());

		invokeFunctionManager(fnMgr, "slow/x", "1".getBytes(), false);
		assertTrue(started.await(2, TimeUnit.SECONDS), "slow action should start");
		// 超时后剩余 action 不应执行
		Thread.sleep(800L);
		assertFalse(events.contains("after"), "remaining action must be skipped after timeout");
		assertTrue(engine.getMetrics().snapshot().values().stream()
			.anyMatch(stat -> stat.getFailureCount() > 0), "timeout should record a failure");
	}

	/**
	 * codec 解码：规则配置 json codec 后，matcher 能拿到结构化 payload。
	 */
	@Test
	void codecDecodesPayloadForMatcher() throws Exception {
		List<String> events = Collections.synchronizedList(new ArrayList<>());
		RuleManager rm = newRuleManager(events);
		rm.registerMatcherFactory(new org.dromara.mica.mqtt.broker.rule.ext.aviator.AviatorExprMatcher.Factory());
		rm.getCodecRegistry().registerFactory(new org.dromara.mica.mqtt.broker.rule.codec.PayloadCodecFactory() {
			@Override
			public String getName() {
				return "json";
			}

			@Override
			public org.dromara.mica.mqtt.broker.rule.codec.PayloadCodec create() {
				return new org.dromara.mica.mqtt.broker.rule.codec.JsonPayloadCodec();
			}
		});
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("json-rule")
			.topicFilter("json/#")
			.codecType("json")
			.when("payload.temperature > 80")
			.addAction(ActionRef.of("seq", "hot"))
			.build());

		invokeFunctionManager(fnMgr, "json/x", "{\"temperature\":90}".getBytes(), false);
		awaitSize(events, 1);
		assertEquals(1, events.size(), "json matcher should hit and run the action");

		events.clear();
		invokeFunctionManager(fnMgr, "json/x", "{\"temperature\":10}".getBytes(), false);
		Thread.sleep(200L);
		assertTrue(events.isEmpty(), "json matcher should not hit below threshold");
	}

	/**
	 * 直接通过 MqttFunctionManager.get(topic) 取出所有 listener，依次调用。
	 */
	private static void invokeFunctionManager(MqttFunctionManager fnMgr,
											  String topic, byte[] payload, boolean retain) {
		List<org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener> fns = fnMgr.get(topic);
		// 使用 null ChannelContext：RuleEngine 已做 null 保护（用于单测）
		ChannelContext ctx = null;
		MqttPublishMessage msg = publishMessage(topic, payload, retain);
		for (org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener fn : fns) {
			try {
				fn.onMessage(ctx, "client1", topic, MqttQoS.QOS0, msg);
			} catch (Throwable ignore) {
				// 失败 action 内部已 log
			}
		}
	}

	@Test
	void actionRegistryCacheReturnsSameInstance() {
		ActionRegistry sr = new ActionRegistry();
		AtomicInteger created = new AtomicInteger();
		sr.registerFactory(new ActionFactory() {
			@Override
			public String getType() {
				return "x";
			}

			@Override
			public Action create(ActionRef ref) {
				created.incrementAndGet();
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
		ActionRef ref = ActionRef.of("x", "n");
		Action a = sr.materialize(ref);
		Action b = sr.materialize(ref);
		assertSame(a, b);
		assertEquals(1, created.get());
	}
}
