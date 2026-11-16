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

import java.util.Map;

/**
 * {@link RuleMatcher} 的 SPI 工厂。
 *
 * @author L.cm
 */
public interface RuleMatcherFactory {

	/**
	 * @return matcher 类型标识
	 */
	String getType();

	/**
	 * 创建 matcher 实例。
	 *
	 * @param props 来自 Rule 的 matcher 配置
	 * @return matcher
	 */
	RuleMatcher create(Map<String, String> props);

	/**
	 * 由 {@link MatcherRegistry} 在注册时回调，注入注册表本身。
	 * <p>
	 * 需要引用其它 matcher 的工厂（如 compound）可覆写此方法拿到注册表。
	 * </p>
	 */
	default void setRegistry(MatcherRegistry registry) {
	}
}
