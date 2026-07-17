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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Deterministic rendezvous-hash placement for retained MQTT topics. */
public class RetainShardRouter {

	public List<String> replicasOf(String topic, Collection<String> nodeIds, int replicationFactor) {
		List<NodeScore> scores = new ArrayList<>();
		if (topic == null || nodeIds == null) {
			return new ArrayList<>();
		}
		for (String nodeId : nodeIds) {
			if (nodeId != null && !nodeId.isEmpty()) {
				scores.add(new NodeScore(nodeId, hash(topic, nodeId)));
			}
		}
		scores.sort((left, right) -> {
			int scoreCompare = Long.compareUnsigned(right.score, left.score);
			return scoreCompare != 0 ? scoreCompare : left.nodeId.compareTo(right.nodeId);
		});
		int count = Math.min(Math.max(1, replicationFactor), scores.size());
		List<String> replicas = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			replicas.add(scores.get(i).nodeId);
		}
		return replicas;
	}

	private static long hash(String topic, String nodeId) {
		byte[] bytes = (topic + '\u0000' + nodeId).getBytes(StandardCharsets.UTF_8);
		long hash = 0xcbf29ce484222325L;
		for (byte value : bytes) {
			hash ^= value & 0xFFL;
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private static final class NodeScore {
		private final String nodeId;
		private final long score;

		private NodeScore(String nodeId, long score) {
			this.nodeId = nodeId;
			this.score = score;
		}
	}
}
