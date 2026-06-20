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

package org.dromara.mica.mqtt.broker.cluster.metrics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the monitoring-friendly helpers in {@link ClusterMetrics}.
 *
 * @author L.cm
 */
class ClusterMetricsTest {

	@Test
	void snapshotReturnsAllCounters() {
		ClusterMetrics m = new ClusterMetrics();
		m.publishForwardSentInc();
		m.publishForwardSentInc();
		m.sharedDispatchSentInc();
		Map<String, Long> snap = m.snapshot();
		assertEquals(12, snap.size());
		assertEquals(2L, snap.get("publishForwardSent"));
		assertEquals(1L, snap.get("sharedDispatchSent"));
		assertEquals(0L, snap.get("sharedDispatchDropped"));
	}

	@Test
	void toPrometheusContainsAllCounters() {
		ClusterMetrics m = new ClusterMetrics();
		m.clusterSendErrorsInc();
		m.clusterSendErrorsInc();
		m.clusterSendErrorsInc();
		String prom = m.toPrometheus();
		assertTrue(prom.contains("mqtt_cluster_publish_forward_sent_total 0"),
			"missing publishForwardSent");
		assertTrue(prom.contains("mqtt_cluster_cluster_send_errors_total 3"),
			"missing clusterSendErrors");
		assertTrue(prom.contains("# HELP"));
		assertTrue(prom.contains("# TYPE"));
	}

	@Test
	void concurrentIncrementsAreLossless() throws InterruptedException {
		ClusterMetrics m = new ClusterMetrics();
		int threads = 8;
		int increments = 100_000;
		Thread[] workers = new Thread[threads];
		for (int t = 0; t < threads; t++) {
			workers[t] = new Thread(() -> {
				for (int i = 0; i < increments; i++) {
					m.sharedDispatchSentInc();
				}
			});
		}
		for (Thread w : workers) w.start();
		for (Thread w : workers) w.join();
		assertEquals((long) threads * increments, m.getSharedDispatchSent());
	}
}