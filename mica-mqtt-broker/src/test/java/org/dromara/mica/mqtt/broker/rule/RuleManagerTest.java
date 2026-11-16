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

import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
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
	}

	@Test
	void addAndGetRule() {
		Rule rule = Rule.builder()
			.id("r1")
			.name("test")
			.topicFilter("a/b/c")
			.addAction(ActionRef.of("log"))
			.build();
		ruleManager.addRule(rule);
		Rule got = ruleManager.getRule("r1");
		assertNotNull(got);
		assertEquals("r1", got.getId());
		assertEquals("test", got.getName());
		assertEquals(1, got.getActions().size());
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
	void actionRefBuilder() {
		ActionRef ref = ActionRef.builder("log")
			.name("logger-1")
			.prop("level", "debug")
			.build();
		assertEquals("log", ref.getType());
		assertEquals("logger-1", ref.getName());
		assertEquals("debug", ref.getProps().get("level"));
	}

	/**
	 * match(topic)：按 topic 通配符匹配规则。
	 */
	@Test
	void matchByTopic() {
		ruleManager.addRule(Rule.builder().id("r1").topicFilter("sensor/+/temp").build());
		ruleManager.addRule(Rule.builder().id("r2").topicFilter("device/#").build());
		ruleManager.addRule(Rule.builder().id("r3").topicFilter("other/topic").build());

		List<Rule> matched = ruleManager.match("sensor/room1/temp");
		assertEquals(1, matched.size());
		assertEquals("r1", matched.get(0).getId());

		List<Rule> matchedAll = ruleManager.match("device/a/b");
		assertEquals(1, matchedAll.size());
		assertEquals("r2", matchedAll.get(0).getId());

		assertTrue(ruleManager.match("nothing/here").isEmpty());
	}

	/**
	 * when(expr) 等价于 aviator matcher。
	 */
	@Test
	void whenExprMapsToAviatorMatcher() {
		Rule rule = Rule.builder()
			.id("r1")
			.topicFilter("a")
			.when("payload.temperature > 80")
			.build();
		assertEquals("aviator", rule.getMatcherType());
		assertEquals("payload.temperature > 80", rule.getMatcherProps().get("when"));
		assertEquals("payload.temperature > 80", rule.getWhenExpr());
	}

	/**
	 * 存储侧变更（外部写入）应触发事件；本地写入不应重复触发。
	 */
	@Test
	void storeListenerFiresForExternalChanges() {
		ruleManager.start();
		int addedBefore = addedCount.get();
		int removedBefore = removedCount.get();

		// 模拟外部（配置中心）直接改存储
		store.save(Rule.builder().id("external").topicFilter("x").build());
		assertEquals(addedBefore + 1, addedCount.get());

		store.delete("external");
		assertEquals(removedBefore + 1, removedCount.get());
	}
}
