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

/**
 * 单一 action 的指标快照。
 *
 * @author L.cm
 */
public final class ActionStat {

	private final String ruleId;
	private final String actionName;
	private final long successCount;
	private final long failureCount;
	private final long totalLatencyMs;
	private final long maxLatencyMs;

	public ActionStat(String ruleId, String actionName,
					long successCount, long failureCount,
					long totalLatencyMs, long maxLatencyMs) {
		this.ruleId = ruleId;
		this.actionName = actionName;
		this.successCount = successCount;
		this.failureCount = failureCount;
		this.totalLatencyMs = totalLatencyMs;
		this.maxLatencyMs = maxLatencyMs;
	}

	public String getRuleId() {
		return ruleId;
	}

	public String getActionName() {
		return actionName;
	}

	public long getSuccessCount() {
		return successCount;
	}

	public long getFailureCount() {
		return failureCount;
	}

	public long getTotalLatencyMs() {
		return totalLatencyMs;
	}

	public long getMaxLatencyMs() {
		return maxLatencyMs;
	}

	public double getAvgLatencyMs() {
		long total = successCount + failureCount;
		return total == 0 ? 0 : (double) totalLatencyMs / total;
	}
}
