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
import org.dromara.mica.mqtt.broker.rule.sink.Sink;
import org.dromara.mica.mqtt.broker.rule.sink.SinkFactory;
import org.dromara.mica.mqtt.broker.rule.sink.SinkRef;
import org.dromara.mica.mqtt.broker.rule.sink.SinkRegistry;
import org.dromara.mica.mqtt.broker.rule.store.InMemoryRuleStore;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleEngine 单测：sink 顺序执行 / 失败继续 / 运行时增删 / 缓存共享。
 *
 * @author L.cm
 */
class RuleEngineTest {

	private static MqttPublishMessage publishMessage(String topic, byte[] payload, boolean retain) {
		MqttFixedHeader header = new MqttFixedHeader(
			org.dromara.mica.mqtt.codec.MqttMessageType.PUBLISH, false,
			MqttQoS.QOS0, retain, 0);
		MqttPublishVariableHeader vh = new MqttPublishVariableHeader(topic, 0);
		return new MqttPublishMessage(header, vh, payload);
	}

	private static RuleManager newRuleManager(List<String> sinkEvents) {
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		AtomicInteger counter = new AtomicInteger();
		SinkFactory seq = new SinkFactory() {
			@Override
			public String getType() {
				return "seq";
			}

			@Override
			public Sink create(SinkRef ref) {
				String name = ref.getName();
				int seq = counter.incrementAndGet();
				return new Sink() {
					@Override
					public String getName() {
						return name == null ? "seq-" + seq : name;
					}

					@Override
					public void send(RuleContext ctx) {
						sinkEvents.add(getName() + ":" + new String(ctx.getPayload()));
					}
				};
			}
		};
		SinkRegistry sr = rm.getSinkRegistry();
		sr.registerFactory(seq);
		return rm;
	}

	@Test
	void sinkOrderAndRuntimeAddRemove() {
		List<String> events = new ArrayList<>();
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();

		rm.setRuleStore(new InMemoryRuleStore());
		engine.start();

		// 第一次添加：rule r1 含两个 sink
		Rule r1 = Rule.builder()
			.id("r1")
			.name("r1")
			.topicFilter("sensor/+/temp")
			.addSink(SinkRef.of("seq", "s1"))
			.addSink(SinkRef.of("seq", "s2"))
			.build();
		rm.addRule(r1);
		// 触发一次消息
		invokeFunctionManager(fnMgr, "sensor/room1/temp", "hello".getBytes(), false);
		assertEquals(2, events.size());
		assertEquals("s1:hello", events.get(0));
		assertEquals("s2:hello", events.get(1));

		// 移除规则
		rm.removeRule("r1");
		events.clear();
		invokeFunctionManager(fnMgr, "sensor/room1/temp", "x".getBytes(), false);
		assertTrue(events.isEmpty());

		// 重新添加
		events.clear();
		rm.addRule(Rule.builder()
			.id("r1")
			.topicFilter("sensor/+/temp")
			.addSink(SinkRef.of("seq", "s3"))
			.build());
		invokeFunctionManager(fnMgr, "sensor/room1/temp", "y".getBytes(), false);
		assertEquals(1, events.size());
		assertEquals("s3:y", events.get(0));
	}

	@Test
	void sinkCacheSharedAcrossRules() {
		List<String> events = new ArrayList<>();
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		// 两条规则共用相同的 SinkRef（type+name 一致）
		rm.addRule(Rule.builder()
			.id("a")
			.topicFilter("a/#")
			.addSink(SinkRef.of("seq", "shared"))
			.build());
		rm.addRule(Rule.builder()
			.id("b")
			.topicFilter("b/#")
			.addSink(SinkRef.of("seq", "shared"))
			.build());

		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		invokeFunctionManager(fnMgr, "b/y", "2".getBytes(), false);
		assertEquals(2, events.size());
		assertEquals("shared:1", events.get(0));
		assertEquals("shared:2", events.get(1));
	}

	@Test
	void failedSinkDoesNotStopOthersByDefault() {
		List<String> events = new ArrayList<>();
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		AtomicInteger counter = new AtomicInteger();
		SinkFactory seq = new SinkFactory() {
			@Override
			public String getType() {
				return "mix";
			}

			@Override
			public Sink create(SinkRef ref) {
				int n = counter.incrementAndGet();
				return new Sink() {
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
		rm.getSinkRegistry().registerFactory(seq);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("r1")
			.topicFilter("a/#")
			.stopOnError(false)
			.addSink(SinkRef.of("mix", "first"))
			.addSink(SinkRef.of("mix", "second"))
			.build());

		invokeFunctionManager(fnMgr, "a/x", "".getBytes(), false);
		assertEquals(1, events.size());
		assertEquals("mix-2", events.get(0));
	}

	@Test
	void stopOnErrorStopsSubsequent() {
		List<String> events = new ArrayList<>();
		RuleManager rm = new RuleManager();
		rm.setRuleStore(new InMemoryRuleStore());
		AtomicInteger counter = new AtomicInteger();
		SinkFactory seq = new SinkFactory() {
			@Override
			public String getType() {
				return "stop";
			}

			@Override
			public Sink create(SinkRef ref) {
				int n = counter.incrementAndGet();
				return new Sink() {
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
		rm.getSinkRegistry().registerFactory(seq);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("r1")
			.topicFilter("a/#")
			.stopOnError(true)
			.addSink(SinkRef.of("stop", "first"))
			.addSink(SinkRef.of("stop", "second"))
			.build());

		invokeFunctionManager(fnMgr, "a/x", "".getBytes(), false);
		assertTrue(events.isEmpty(), "stopOnError should stop subsequent sinks");
	}

	@Test
	void disabledRuleDoesNotExecute() {
		List<String> events = new ArrayList<>();
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		rm.addRule(Rule.builder()
			.id("off")
			.topicFilter("a/#")
			.enabled(false)
			.addSink(SinkRef.of("seq", "x"))
			.build());
		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		assertTrue(events.isEmpty());
	}

	@Test
	void unregisterRemovesRule() {
		List<String> events = new ArrayList<>();
		RuleManager rm = newRuleManager(events);
		MqttFunctionManager fnMgr = new MqttFunctionManager();
		RuleEngine engine = new RuleEngine(rm, fnMgr, null);
		engine.attach();
		engine.start();

		Rule r1 = Rule.builder()
			.id("r1")
			.topicFilter("a/#")
			.addSink(SinkRef.of("seq", "s1"))
			.build();
		rm.addRule(r1);
		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		assertEquals(1, events.size());

		rm.removeRule("r1");
		events.clear();
		invokeFunctionManager(fnMgr, "a/x", "1".getBytes(), false);
		assertTrue(events.isEmpty());
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
				// 失败 sink 内部已 log
			}
		}
	}

	@Test
	void sinkRegistryCacheReturnsSameInstance() {
		SinkRegistry sr = new SinkRegistry();
		AtomicInteger created = new AtomicInteger();
		sr.registerFactory(new SinkFactory() {
			@Override
			public String getType() {
				return "x";
			}

			@Override
			public Sink create(SinkRef ref) {
				created.incrementAndGet();
				return new Sink() {
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
		SinkRef ref = SinkRef.of("x", "n");
		Sink a = sr.materialize(ref);
		Sink b = sr.materialize(ref);
		assertSame(a, b);
		assertEquals(1, created.get());
	}
}
