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

package org.dromara.mica.mqtt.broker.cluster.store;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainShardRouterTest {

	@Test
	void placementIsStableAndRespectsReplicationFactor() {
		RetainShardRouter router = new RetainShardRouter();
		List<String> nodes = Arrays.asList("node-3", "node-1", "node-2");

		List<String> first = router.replicasOf("sensors/a", nodes, 2);
		List<String> second = router.replicasOf("sensors/a", Arrays.asList("node-2", "node-3", "node-1"), 2);

		assertEquals(first, second);
		assertEquals(2, first.size());
		assertTrue(first.stream().distinct().count() == 2);
		assertEquals(3, router.replicasOf("sensors/a", nodes, 9).size());
	}

	@Test
	void addingNodeOnlyMovesPrimaryToTheNewNode() {
		RetainShardRouter router = new RetainShardRouter();
		List<String> oldNodes = Arrays.asList("node-1", "node-2", "node-3");
		List<String> newNodes = Arrays.asList("node-1", "node-2", "node-3", "node-4");
		for (int i = 0; i < 1_000; i++) {
			String topic = "devices/" + i;
			String oldPrimary = router.replicasOf(topic, oldNodes, 1).get(0);
			String newPrimary = router.replicasOf(topic, newNodes, 1).get(0);
			if (!oldPrimary.equals(newPrimary)) {
				assertEquals("node-4", newPrimary);
			}
		}
	}

	/**
	 * Guards the bounded top-k selection against an independent full-sort oracle.
	 * <p>
	 * The oracle re-implements the rendezvous hash (which must not change) and ranks every
	 * candidate with a comparator-free sort, so it shares no code with the incremental
	 * insertion logic under test.
	 * </p>
	 */
	@Test
	void topKMatchesFullSortForEveryReplicationFactor() {
		RetainShardRouter router = new RetainShardRouter();
		List<String> nodes = Arrays.asList(
			"node-9", "node-1", "node-5", "node-3", "node-7", "node-2", "node-8", "node-4", "node-6");
		for (int i = 0; i < 200; i++) {
			String topic = "telemetry/" + i;
			for (int factor = 1; factor <= nodes.size() + 2; factor++) {
				assertEquals(referenceReplicas(topic, nodes, factor),
					router.replicasOf(topic, nodes, factor),
					"mismatch for topic=" + topic + " factor=" + factor);
			}
		}
	}

	@Test
	void degenerateInputsAreHandledConsistently() {
		RetainShardRouter router = new RetainShardRouter();
		assertTrue(router.replicasOf(null, Arrays.asList("node-1"), 1).isEmpty());
		assertTrue(router.replicasOf("a/b", null, 1).isEmpty());
		assertTrue(router.replicasOf("a/b", java.util.Collections.emptyList(), 3).isEmpty());
		// Non-positive factors are clamped to a single replica.
		assertEquals(1, router.replicasOf("a/b", Arrays.asList("node-1", "node-2"), 0).size());
		assertEquals(1, router.replicasOf("a/b", Arrays.asList("node-1", "node-2"), -5).size());
		// Blank / null node ids are skipped rather than becoming replicas.
		List<String> replicas = router.replicasOf("a/b", Arrays.asList("node-1", "", null), 5);
		assertEquals(Arrays.asList("node-1"), replicas);
	}

	/**
	 * Independent oracle: ranks candidates by the rendezvous hash (descending, unsigned)
	 * with a tie-break on node id, then truncates to the replication factor.
	 */
	private static List<String> referenceReplicas(String topic, List<String> nodeIds, int replicationFactor) {
		List<String> candidates = new java.util.ArrayList<>();
		for (String nodeId : nodeIds) {
			if (nodeId != null && !nodeId.isEmpty()) {
				candidates.add(nodeId);
			}
		}
		candidates.sort((left, right) -> {
			int byScore = Long.compareUnsigned(hash(topic, right), hash(topic, left));
			return byScore != 0 ? byScore : left.compareTo(right);
		});
		int count = Math.min(Math.max(1, replicationFactor), candidates.size());
		return new java.util.ArrayList<>(candidates.subList(0, count));
	}

	/** Mirrors {@code RetainShardRouter.hash}: FNV-1a over {@code topic + '\0' + nodeId}. */
	private static long hash(String topic, String nodeId) {
		byte[] bytes = (topic + '\u0000' + nodeId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		long hash = 0xcbf29ce484222325L;
		for (byte value : bytes) {
			hash ^= value & 0xFFL;
			hash *= 0x100000001b3L;
		}
		return hash;
	}
}
