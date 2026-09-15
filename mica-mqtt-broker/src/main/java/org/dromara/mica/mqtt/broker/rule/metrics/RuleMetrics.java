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

import java.util.Map;

/**
 * 规则执行指标（success / failure / latency）。
 *
 * @author L.cm
 */
public interface RuleMetrics {

	void recordSuccess(String ruleId, String actionName, long costMs);

	void recordFailure(String ruleId, String actionName, long costMs);

	/**
	 * 快照，供 HTTP API / log 拉取。
	 *
	 * @return 指标快照
	 */
	Map<String, ActionStat> snapshot();
}
