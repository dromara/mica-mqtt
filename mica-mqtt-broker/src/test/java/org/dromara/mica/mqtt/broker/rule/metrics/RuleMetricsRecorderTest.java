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

package org.dromara.mica.mqtt.broker.rule.metrics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleMetricsRecorder 单测。
 *
 * @author L.cm
 */
class RuleMetricsRecorderTest {

	@Test
	void recordSuccessAndFailure() {
		RuleMetricsRecorder m = new RuleMetricsRecorder();
		m.recordSuccess("r1", "s1", 10);
		m.recordSuccess("r1", "s1", 20);
		m.recordFailure("r1", "s1", 5);
		Map<String, ActionStat> snap = m.snapshot();
		ActionStat stat = snap.get("r1|s1");
		assertNotNull(stat);
		assertEquals(2, stat.getSuccessCount());
		assertEquals(1, stat.getFailureCount());
		assertEquals(35, stat.getTotalLatencyMs());
		assertEquals(20, stat.getMaxLatencyMs());
	}

	@Test
	void multipleActionsTrackedSeparately() {
		RuleMetricsRecorder m = new RuleMetricsRecorder();
		m.recordSuccess("r1", "a", 1);
		m.recordSuccess("r1", "b", 2);
		Map<String, ActionStat> snap = m.snapshot();
		assertEquals(2, snap.size());
		assertNotNull(snap.get("r1|a"));
		assertNotNull(snap.get("r1|b"));
	}

	@Test
	void avgLatencyCalculated() {
		RuleMetricsRecorder m = new RuleMetricsRecorder();
		m.recordSuccess("r", "s", 10);
		m.recordFailure("r", "s", 20);
		ActionStat stat = m.snapshot().get("r|s");
		assertNotNull(stat);
		assertEquals(15.0, stat.getAvgLatencyMs(), 0.001);
	}

	@Test
	void emptySnapshot() {
		RuleMetricsRecorder m = new RuleMetricsRecorder();
		assertTrue(m.snapshot().isEmpty());
	}
}
