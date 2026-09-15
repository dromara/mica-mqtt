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

package org.dromara.mica.mqtt.broker.rule.matcher;

import org.dromara.mica.mqtt.broker.rule.RuleContext;

/**
 * 可选匹配器：在 topic 命中后，再做一次内容级匹配。
 *
 * @author L.cm
 */
@FunctionalInterface
public interface RuleMatcher {

	/**
	 * @param ctx 规则上下文
	 * @return true 放行执行 actions
	 */
	boolean matches(RuleContext ctx);
}
