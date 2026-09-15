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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 内存版 {@link RuleMetrics}，使用 {@link LongAdder} 计数。
 *
 * @author L.cm
 */
public class RuleMetricsRecorder implements RuleMetrics {

	private final ConcurrentMap<String, Stat> stats = new ConcurrentHashMap<>();

	@Override
	public void recordSuccess(String ruleId, String actionName, long costMs) {
		Stat stat = stats.computeIfAbsent(key(ruleId, actionName),
			k -> new Stat(ruleId, actionName));
		stat.successCount.increment();
		stat.totalLatencyMs.add(costMs);
		stat.recordMax(costMs);
	}

	@Override
	public void recordFailure(String ruleId, String actionName, long costMs) {
		Stat stat = stats.computeIfAbsent(key(ruleId, actionName),
			k -> new Stat(ruleId, actionName));
		stat.failureCount.increment();
		stat.totalLatencyMs.add(costMs);
		stat.recordMax(costMs);
	}

	@Override
	public Map<String, ActionStat> snapshot() {
		Map<String, ActionStat> snap = new HashMap<>(stats.size());
		stats.forEach((k, v) -> snap.put(k, v.toActionStat()));
		return snap;
	}

	private static String key(String ruleId, String actionName) {
		return ruleId + "|" + actionName;
	}

	private static final class Stat {
		final String ruleId;
		final String actionName;
		final LongAdder successCount = new LongAdder();
		final LongAdder failureCount = new LongAdder();
		final LongAdder totalLatencyMs = new LongAdder();
		volatile long maxLatencyMs;

		Stat(String ruleId, String actionName) {
			this.ruleId = ruleId;
			this.actionName = actionName;
		}

		void recordMax(long costMs) {
			long cur = maxLatencyMs;
			if (costMs > cur) {
				synchronized (this) {
					if (costMs > maxLatencyMs) {
						maxLatencyMs = costMs;
					}
				}
			}
		}

		ActionStat toActionStat() {
			return new ActionStat(
				ruleId,
				actionName,
				successCount.sum(),
				failureCount.sum(),
				totalLatencyMs.sum(),
				maxLatencyMs
			);
		}
	}
}
