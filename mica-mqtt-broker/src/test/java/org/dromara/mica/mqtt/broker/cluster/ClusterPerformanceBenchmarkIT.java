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

package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.pipeline.strategy.LocalFirstStrategy;
import org.dromara.mica.mqtt.broker.cluster.store.MemoryKvStoreImpl;
import org.dromara.mica.mqtt.broker.cluster.store.RetainIndex;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in, repeatable micro baseline for the two 100k-entry hot paths in the
 * cluster design. Run through the {@code cluster-benchmark} Maven profile.
 */
class ClusterPerformanceBenchmarkIT {

	@Test
	void strategyAndRetainWildcardMeetDesignLatencyTargets() {
		int entryCount = Integer.getInteger("cluster.benchmark.entries", 100_000);
		long strategyTargetMs = Long.getLong("cluster.benchmark.strategy.p99.ms", 10L);
		long retainTargetMs = Long.getLong("cluster.benchmark.retain.p99.ms", 5L);

		List<Subscribe> subscribers = new ArrayList<>(entryCount);
		for (int i = 0; i < entryCount; i++) {
			subscribers.add(new Subscribe("$share/load/sensor/+/temp", "client-" + i, 1));
		}
		Message dispatch = message("sensor/1/temp");
		LocalFirstStrategy strategy = new LocalFirstStrategy(clientId -> "node-1");
		for (int i = 0; i < 5; i++) {
			strategy.pick("load", subscribers, "node-1", dispatch);
		}
		long strategyP99Nanos = measureP99(200,
			() -> strategy.pick("load", subscribers, "node-1", dispatch));

		RetainIndex retainIndex = new RetainIndex(new MemoryKvStoreImpl());
		for (int i = 0; i < entryCount; i++) {
			int site = i / 1_000;
			int device = i % 1_000;
			String topic = String.format("sensor/site%03d/device%03d/temp", site, device);
			retainIndex.put(topic, message(topic));
		}
		String wildcard = "sensor/site050/+/temp";
		for (int i = 0; i < 5; i++) {
			retainIndex.match(wildcard);
		}
		int[] matchedCount = new int[1];
		long retainP99Nanos = measureP99(200,
			() -> matchedCount[0] = retainIndex.match(wildcard).size());
		assertEquals(Math.min(1_000, Math.max(0, entryCount - 50_000)), matchedCount[0]);

		double strategyP99Ms = strategyP99Nanos / 1_000_000.0;
		double retainP99Ms = retainP99Nanos / 1_000_000.0;
		System.out.printf("CLUSTER_PERFORMANCE_BASELINE entries=%d strategy_p99_ms=%.3f retain_p99_ms=%.3f%n",
			entryCount, strategyP99Ms, retainP99Ms);
		assertTrue(strategyP99Nanos <= strategyTargetMs * 1_000_000L,
			"100k local-first strategy P99 exceeded " + strategyTargetMs + "ms: " + strategyP99Ms);
		assertTrue(retainP99Nanos <= retainTargetMs * 1_000_000L,
			"100k retain wildcard P99 exceeded " + retainTargetMs + "ms: " + retainP99Ms);
	}

	private static long measureP99(int iterations, Runnable operation) {
		long[] samples = new long[iterations];
		for (int i = 0; i < iterations; i++) {
			long started = System.nanoTime();
			operation.run();
			samples[i] = System.nanoTime() - started;
		}
		Arrays.sort(samples);
		return samples[(int) Math.ceil(samples.length * 0.99) - 1];
	}

	private static Message message(String topic) {
		Message message = new Message();
		message.setTopic(topic);
		message.setPayload(new byte[]{1});
		message.setQos(1);
		message.setRetain(true);
		return message;
	}
}
