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

import java.util.Map;

/**
 * {@link RuleStore} 的 SPI 工厂。
 * <p>
 * 用户实现本接口即可把规则持久化到数据库 / 配置中心：
 * {@code META-INF/services/org.dromara.mica.mqtt.broker.rule.store.RuleStoreFactory}。
 * </p>
 *
 * @author L.cm
 */
public interface RuleStoreFactory {

	/**
	 * @return store 类型标识
	 */
	String getType();

	/**
	 * 创建 store 实例。
	 *
	 * @param props store 配置
	 * @return store 实例
	 */
	RuleStore create(Map<String, String> props);
}
