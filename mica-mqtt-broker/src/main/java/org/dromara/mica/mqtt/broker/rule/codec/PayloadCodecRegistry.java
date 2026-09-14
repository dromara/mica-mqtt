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

package org.dromara.mica.mqtt.broker.rule.codec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Codec 注册表。
 *
 * @author L.cm
 */
public class PayloadCodecRegistry {

	private final ConcurrentMap<String, PayloadCodec> cache = new ConcurrentHashMap<>();

	/**
	 * 注册 codec 工厂。
	 *
	 * @param factory codec 工厂
	 */
	public void registerFactory(PayloadCodecFactory factory) {
		cache.computeIfAbsent(factory.getName(), name -> factory.create());
	}

	/**
	 * 按名称查找 codec，未注册时返回 null。
	 *
	 * @param name codec 名（可为空表示 raw）
	 * @return codec 或 null
	 */
	public PayloadCodec get(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		return cache.get(name);
	}

	/**
	 * 清空所有缓存的 codec（broker 关闭时调用）。
	 */
	public void clear() {
		cache.clear();
	}
}
