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

package org.dromara.mica.mqtt.broker.rule.action;

import org.dromara.mica.mqtt.broker.rule.RuleContext;

/**
 * 真正干活的单元：转发 / 写库 / 调 HTTP 等。
 * <p>
 * 实现必须是无 rule 状态的（同一 ActionRef 共享同一 Action 实例）。
 * </p>
 *
 * @author L.cm
 */
public interface Action {

	/**
	 * 调试用，唯一实例名。
	 *
	 * @return action 实例名
	 */
	String getName();

	/**
	 * 处理一条规则上下文。
	 *
	 * @param ctx 规则执行上下文
	 * @throws Exception 业务异常
	 */
	void send(RuleContext ctx) throws Exception;
}
