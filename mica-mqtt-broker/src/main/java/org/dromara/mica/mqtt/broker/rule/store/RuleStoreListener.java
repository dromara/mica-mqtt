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

package org.dromara.mica.mqtt.broker.rule.store;

import org.dromara.mica.mqtt.broker.rule.Rule;

/**
 * 规则存储变更监听：用于「存储侧热加载」场景。
 * <p>
 * 与 {@link org.dromara.mica.mqtt.broker.rule.loader.RuleLoader} 的监听区别在于
 * 数据源：loader 是启动期一次性加载 + 可选热加载，store listener 是存储本身
 * 被外部（配置中心 / 其它节点）改动后的回调。
 * </p>
 *
 * @author L.cm
 */
public interface RuleStoreListener {

	/**
	 * 规则被保存（新增或更新）。
	 *
	 * @param rule 规则
	 */
	void onSaved(Rule rule);

	/**
	 * 规则被删除。
	 *
	 * @param ruleId 规则 id
	 */
	void onDeleted(String ruleId);
}
