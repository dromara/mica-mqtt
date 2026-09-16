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

import java.util.ArrayList;
import java.util.Collection;
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
	 * <p>
	 * 使用 {@link ConcurrentMap#computeIfAbsent} 保证并发下只有一个线程创建实例：
	 * 早期实现为 {@code get → create → putIfAbsent}，落败的实例（可能已建立 MqttClient
	 * 连接）会被直接丢弃且不会关闭，造成连接泄漏。
	 * </p>
	 *
	 * @param ref action 引用
	 * @return action 实例
	 */
	public Action materialize(ActionRef ref) {
		Action cached = cache.get(ref);
		if (cached != null) {
			return cached;
		}
		final ActionFactory factory = factories.get(ref.getType());
		if (factory == null) {
			throw new IllegalStateException("No ActionFactory for type: " + ref.getType());
		}
		return cache.computeIfAbsent(ref, key -> {
			try {
				return factory.create(key);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new IllegalStateException("Failed to create action for type: " + key.getType(), e);
			}
		});
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
	 * 仅保留仍被引用的 action，其余关闭并移出缓存。
	 * <p>
	 * 规则被删除或更新时调用：同一 {@link ActionRef} 可能被多条规则共享，
	 * 因此必须按「仍存活引用的并集」回收，不能按单条规则盲目失效。
	 * </p>
	 *
	 * @param retained 仍然有效的 action 引用集合
	 */
	public void retainAll(Collection<ActionRef> retained) {
		for (ActionRef ref : new ArrayList<>(cache.keySet())) {
			if (retained.contains(ref)) {
				continue;
			}
			Action action = cache.remove(ref);
			if (action != null) {
				closeQuietly(action);
			}
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
