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
import org.dromara.mica.mqtt.broker.rule.RuleChangeListener;

import java.util.List;

/**
 * 规则持久化抽象。
 *
 * @author L.cm
 */
public interface RuleStore {

	/**
	 * 保存或更新规则。
	 *
	 * @param rule 规则
	 */
	void save(Rule rule);

	/**
	 * 删除规则。
	 *
	 * @param id 规则 id
	 */
	void delete(String id);

	/**
	 * 查找规则。
	 *
	 * @param id 规则 id
	 * @return 规则，不存在返回 null
	 */
	Rule find(String id);

	/**
	 * 加载所有规则。
	 *
	 * @return 规则列表
	 */
	List<Rule> loadAll();

	/**
	 * 注册变更监听器（热加载场景），默认实现为空操作。
	 *
	 * @param listener 监听器
	 */
	default void setListener(RuleChangeListener listener) {
	}
}
