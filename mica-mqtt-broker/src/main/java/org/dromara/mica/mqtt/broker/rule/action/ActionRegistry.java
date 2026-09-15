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

import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Action 物化 + 按 ActionRef 全量缓存的注册表。
 *
 * @author L.cm
 */
public class ActionRegistry {

	private static final Logger logger = LoggerFactory.getLogger(ActionRegistry.class);

	private final ConcurrentMap<String, ActionFactory> factories = new ConcurrentHashMap<>();
	private final ConcurrentMap<ActionRef, Action> cache = new ConcurrentHashMap<>();

	/**
	 * 注册工厂实现。
	 *
	 * @param factory 工厂
	 */
	public void registerFactory(ActionFactory factory) {
		String type = factory.getType();
		if (StrUtil.isBlank(type)) {
			logger.warn("ActionFactory type is blank, ignore: {}", factory.getClass().getName());
			return;
		}
		ActionFactory prev = factories.putIfAbsent(type, factory);
		if (prev != null && prev.getClass() != factory.getClass()) {
			logger.warn("Duplicate ActionFactory type={}, keep existing {}", type, prev.getClass().getName());
		}
	}

	/**
	 * 物化 action，按 ActionRef 全量缓存（type + name + props）。
	 *
	 * @param ref action 引用
	 * @return action 实例
	 */
	public Action materialize(ActionRef ref) {
		Action action = cache.get(ref);
		if (action != null) {
			return action;
		}
		ActionFactory factory = factories.get(ref.getType());
		if (factory == null) {
			throw new IllegalStateException("No ActionFactory for type: " + ref.getType());
		}
		Action newAction;
		try {
			newAction = factory.create(ref);
		} catch (Exception e) {
			throw new RuntimeException("Failed to create action for type: " + ref.getType(), e);
		}
		Action existing = cache.putIfAbsent(ref, newAction);
		return existing != null ? existing : newAction;
	}

	/**
	 * 主动失效指定 action 缓存（主要用于 action 关闭场景）。
	 *
	 * @param ref action 引用
	 */
	public void invalidate(ActionRef ref) {
		Action action = cache.remove(ref);
		if (action != null) {
			closeQuietly(action);
		}
	}

	/**
	 * 清空所有缓存（broker 关闭时调用）。
	 */
	public void clear() {
		for (Action action : cache.values()) {
			closeQuietly(action);
		}
		cache.clear();
	}

	private static void closeQuietly(Action action) {
		try {
			if (action instanceof AutoCloseable) {
				((AutoCloseable) action).close();
			}
		} catch (Exception e) {
			logger.warn("Failed to close action {}", action.getName(), e);
		}
	}
}
