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
}
