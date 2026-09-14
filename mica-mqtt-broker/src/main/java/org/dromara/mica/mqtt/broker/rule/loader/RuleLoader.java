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

package org.dromara.mica.mqtt.broker.rule.loader;

import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.RuleChangeListener;

import java.util.List;

/**
 * 规则加载器：启动期加载 + 可选热加载。
 *
 * @author L.cm
 */
public interface RuleLoader {

	/**
	 * @return 来源标识（用于日志/调试）
	 */
	String getSource();

	/**
	 * 全量加载规则。
	 *
	 * @return 规则列表
	 */
	List<Rule> loadAll();

	/**
	 * 注册热加载变更监听器。
	 *
	 * @param listener 监听器（null 表示只加载一次）
	 */
	default void setListener(RuleChangeListener listener) {
	}
}
