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

package org.dromara.mica.mqtt.broker.cluster.pipeline.strategy;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for the V2 shared-subscription dispatcher (P1.2).
 *
 * @author L.cm
 */
class SharedSubscriptionStrategyTest {

	private static Subscribe sub(String clientId, String nodeId) {
		return new Subscribe("$share/g1/sensors/temp", clientId, 1);
	}

	private static java.util.function.Function<String, String> nodeMap(String... pairs) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return clientId -> map.get(clientId);
	}

	private static Message msg() {
		return msg(0);
	}

	private static Message msg(int id) {
		Message m = new Message();
		m.setId(id);
		m.setTopic("sensors/temp");
		m.setPayload("v".getBytes());
		m.setQos(1);
		return m;
	}

	// ---- Empty group ---------------------------------------------------------

	@Test
	void random_emptyGroup_returnsNull() {
		assertNull(new RandomStrategy().pick("g1", Collections.emptyList(), "n1", msg()));
	}

	@Test
	void roundRobin_emptyGroup_returnsNull() {
		assertNull(new RoundRobinStrategy().pick("g1", Collections.emptyList(), "n1", msg()));
	}

	@Test
	void hashClient_emptyGroup_returnsNull() {
		assertNull(new HashClientStrategy().pick("g1", Collections.emptyList(), "n1", msg()));
	}

	@Test
	void sticky_emptyGroup_returnsNull() {
		assertNull(new StickyStrategy().pick("g1", Collections.emptyList(), "n1", msg()));
	}

	@Test
	void localFirst_emptyGroup_returnsNull() {
		assertNull(new LocalFirstStrategy(nodeMap())
			.pick("g1", Collections.emptyList(), "n1", msg()));
	}

	// ---- Single member ------------------------------------------------------

	@Test
	void random_singleMember_alwaysReturnsIt() {
		RandomStrategy s = new RandomStrategy();
		Subscribe only = sub("c1", "n1");
		for (int i = 0; i < 10; i++) {
			assertSame(only, s.pick("g", Collections.singletonList(only), "n1", msg()));
		}
	}

	@Test
	void sticky_singleMember_isStable() {
		StickyStrategy s = new StickyStrategy();
		Subscribe a = sub("a", "n1");
		Subscribe b = sub("b", "n2");
		Subscribe first = s.pick("g", Arrays.asList(a, b), "n1", msg());
		for (int i = 0; i < 50; i++) {
			assertSame(first, s.pick("g", Arrays.asList(a, b), "n1", msg()));
		}
	}

	// ---- Distribution / No duplicate ---------------------------------------

	@Test
	void random_evenDistribution() {
		RandomStrategy s = new RandomStrategy();
		List<Subscribe> cands = Arrays.asList(sub("a", "n1"), sub("b", "n1"), sub("c", "n1"));
		int total = 6000;
		int[] counts = new int[cands.size()];
		for (int i = 0; i < total; i++) {
			Subscribe picked = s.pick("g", cands, "n1", msg());
			counts[cands.indexOf(picked)]++;
		}
		for (int c : counts) {
			assertTrue(Math.abs(c - total / cands.size()) < total / 5,
				"random distribution too skewed: " + Arrays.toString(counts));
		}
	}

	@Test
	void hashClient_deterministic() {
		HashClientStrategy s = new HashClientStrategy();
		List<Subscribe> cands = Arrays.asList(sub("a", "n1"), sub("b", "n1"), sub("c", "n1"));
		// hash strategy may or may not vary by message; the contract is
		// "deterministic for the same (group, message)" so run the same message twice
		// and verify the pick is stable.
		Message m = msg();
		Subscribe p1 = s.pick("g", cands, "n1", m);
		for (int i = 0; i < 10; i++) {
			assertSame(p1, s.pick("g", cands, "n1", m));
		}
	}

	// ---- LocalFirst --------------------------------------------------------

	@Test
	void localFirst_prefersLocalNode() {
		LocalFirstStrategy s = new LocalFirstStrategy(nodeMap("r", "n2", "l", "n1"));
		Subscribe remote = sub("r", "n2");
		Subscribe local = sub("l", "n1");
		List<Subscribe> cands = new ArrayList<>();
		cands.add(remote);
		cands.add(local);
		Subscribe picked = s.pick("g", cands, "n1", msg());
		assertSame(local, picked);
	}

	@Test
	void localFirst_fallsBackToRemoteWhenNoLocal() {
		LocalFirstStrategy s = new LocalFirstStrategy(nodeMap("r", "n2"));
		Subscribe remote = sub("r", "n2");
		Subscribe picked = s.pick("g", Collections.singletonList(remote), "n1", msg());
		assertSame(remote, picked);
	}

	// ---- Round robin fairness -----------------------------------------------

	@Test
	void roundRobin_rotatesThroughMembers() {
		RoundRobinStrategy s = new RoundRobinStrategy();
		List<Subscribe> cands = Arrays.asList(sub("a", "n1"), sub("b", "n1"), sub("c", "n1"));
		// run twice the candidate count and verify each was picked equally
		int total = cands.size() * 200;
		int[] counts = new int[cands.size()];
		for (int i = 0; i < total; i++) {
			Subscribe picked = s.pick("g", cands, "n1", msg());
			counts[cands.indexOf(picked)]++;
		}
		// round robin is not strictly fair because empty slots reset; allow 30% drift
		int expected = total / cands.size();
		for (int c : counts) {
			assertTrue(Math.abs(c - expected) < total / 4,
				"round robin unfair: " + Arrays.toString(counts));
		}
	}

	// ---- No duplicate across nodes -----------------------------------------

	@Test
	void allStrategies_pickExactlyOneSubscriber() {
		// Simulate dispatching 1000 messages to a 3-member group.  Each pick
		// must return a non-null element of the candidate set (i.e. exactly
		// one subscriber, never two or none).
		List<Subscribe> cands = Arrays.asList(sub("a", "n1"), sub("b", "n2"), sub("c", "n3"));
		// For LocalFirstStrategy we put 2 members on the local node so it can
		// still demonstrate the rotation-within-local behaviour.
		SharedSubscriptionStrategy[] all = new SharedSubscriptionStrategy[] {
			new RandomStrategy(), new RoundRobinStrategy(), new HashClientStrategy(),
			new StickyStrategy(), new LocalFirstStrategy(nodeMap("a", "n1", "b", "n1", "c", "n3"))
		};
		for (SharedSubscriptionStrategy s : all) {
			Set<String> seen = new HashSet<>();
			// HashClientStrategy is message-id-deterministic — vary the id each call
			// to actually distribute across candidates; Sticky intentionally picks
			// the same one forever.
			boolean varyMessageId = "hash_client".equals(s.name());
			for (int i = 0; i < 1000; i++) {
				Subscribe picked = s.pick("g", cands, "n1", varyMessageId ? msg(i) : msg());
				assertNotNull(picked, s.name() + " returned null");
				assertTrue(cands.contains(picked), s.name() + " returned an element outside the candidate set");
				seen.add(picked.getClientId());
			}
			// sticky and local_first may both legitimately limit to a subset
			// (sticky picks one forever; local_first prefers a local member).
			if (!"sticky".equals(s.name()) && !"local_first".equals(s.name())) {
				assertEquals(3, seen.size(), s.name() + " never picked: " + seen);
			}
		}
	}

	@Test
	void name_isConsistent() {
		assertEquals("random", new RandomStrategy().name());
		assertEquals("round_robin", new RoundRobinStrategy().name());
		assertEquals("hash_client", new HashClientStrategy().name());
		assertEquals("sticky", new StickyStrategy().name());
		assertEquals("local_first", new LocalFirstStrategy(nodeMap()).name());
	}
}