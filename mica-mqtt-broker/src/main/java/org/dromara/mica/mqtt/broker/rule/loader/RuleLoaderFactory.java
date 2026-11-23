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

import java.util.Map;

/**
 * {@link RuleLoader} 的 SPI 工厂。
 * <p>
 * 用于把「规则来源」也做成可插拔的：用户实现本接口并注册到
 * {@code META-INF/services/org.dromara.mica.mqtt.broker.rule.loader.RuleLoaderFactory}，
 * 即可接入 Nacos / Apollo / 数据库等来源。
 * </p>
 *
 * @author L.cm
 */
public interface RuleLoaderFactory {

	/**
	 * @return loader 类型标识
	 */
	String getType();

	/**
	 * 创建 loader 实例。
	 *
	 * @param props loader 配置
	 * @return loader 实例
	 */
	RuleLoader create(Map<String, String> props);
}
