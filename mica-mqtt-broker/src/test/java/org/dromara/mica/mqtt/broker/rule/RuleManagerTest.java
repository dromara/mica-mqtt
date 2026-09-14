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

import org.dromara.mica.mqtt.broker.rule.sink.SinkRef;
import org.dromara.mica.mqtt.broker.rule.store.InMemoryRuleStore;
import org.dromara.mica.mqtt.broker.rule.store.RuleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleManager 单测。
 *
 * @author L.cm
 */
class RuleManagerTest {

	private InMemoryRuleStore store;
	private RuleManager ruleManager;
	private final List<RuleEvent> events = new ArrayList<>();
	private final AtomicInteger addedCount = new AtomicInteger();
	private final AtomicInteger removedCount = new AtomicInteger();

	@BeforeEach
	void setUp() {
		store = new InMemoryRuleStore();
		ruleManager = new RuleManager();
		ruleManager.setRuleStore(store);
		ruleManager.addListener(evt -> {
			events.add(evt);
			if (evt.getType() == RuleEvent.Type.ADDED) {
				addedCount.incrementAndGet();
			} else if (evt.getType() == RuleEvent.Type.REMOVED) {
				removedCount.incrementAndGet();
			}
		});
		store.setListener(new RuleChangeListener() {
			@Override
			public void onSaved(Rule rule) {
				// 通过 store.save 时会触发
			}

			@Override
			public void onDeleted(String ruleId) {
			}
		});
	}

	@Test
	void addAndGetRule() {
		Rule rule = Rule.builder()
			.id("r1")
			.name("test")
			.topicFilter("a/b/c")
			.addSink(SinkRef.of("log"))
			.build();
		ruleManager.addRule(rule);
		Rule got = ruleManager.getRule("r1");
		assertNotNull(got);
		assertEquals("r1", got.getId());
		assertEquals("test", got.getName());
		assertEquals(1, got.getSinks().size());
		assertEquals(1, addedCount.get());
	}

	@Test
	void updateRuleFiresUpdateEvent() {
		ruleManager.addRule(Rule.builder().id("r1").topicFilter("a/b").build());
		int beforeAdd = addedCount.get();
		ruleManager.updateRule("r1", prev -> Rule.builder()
			.id(prev.getId())
			.topicFilter("a/b")
			.name("updated")
			.build());
		Rule updated = ruleManager.getRule("r1");
		assertEquals("updated", updated.getName());
		assertEquals(RuleEvent.Type.UPDATED, events.get(events.size() - 1).getType());
		// updated 不应重复触发 ADDED，addedCount 应保持不变
		assertEquals(beforeAdd, addedCount.get());
	}

	@Test
	void removeRuleFiresRemovedEvent() {
		ruleManager.addRule(Rule.builder().id("r1").topicFilter("a/b").build());
		assertTrue(ruleManager.removeRule("r1"));
		assertNull(ruleManager.getRule("r1"));
		assertEquals(1, removedCount.get());
	}

	@Test
	void removeNonExistentRuleReturnsFalse() {
		assertFalse(ruleManager.removeRule("missing"));
		assertEquals(0, removedCount.get());
	}

	@Test
	void listRulesReturnsAll() {
		ruleManager.addRule(Rule.builder().id("r1").topicFilter("a").build());
		ruleManager.addRule(Rule.builder().id("r2").topicFilter("b").build());
		List<Rule> rules = ruleManager.listRules();
		assertEquals(2, rules.size());
	}

	@Test
	void loadAllFromStoreOnStart() {
		store.save(Rule.builder().id("r1").topicFilter("a").build());
		store.save(Rule.builder().id("r2").topicFilter("b").build());
		ruleManager.start();
		assertEquals(2, addedCount.get());
	}

	@Test
	void sinkRefBuilder() {
		SinkRef ref = SinkRef.builder("log")
			.name("logger-1")
			.prop("level", "debug")
			.build();
		assertEquals("log", ref.getType());
		assertEquals("logger-1", ref.getName());
		assertEquals("debug", ref.getProps().get("level"));
	}
}
