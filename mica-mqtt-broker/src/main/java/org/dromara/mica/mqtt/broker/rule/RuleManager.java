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

package org.dromara.mica.mqtt.broker.rule;

import org.dromara.mica.mqtt.broker.rule.codec.PayloadCodecFactory;
import org.dromara.mica.mqtt.broker.rule.codec.PayloadCodecRegistry;
import org.dromara.mica.mqtt.broker.rule.loader.RuleLoader;
import org.dromara.mica.mqtt.broker.rule.matcher.MatcherRegistry;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcherFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionRegistry;
import org.dromara.mica.mqtt.broker.rule.store.RuleEvent;
import org.dromara.mica.mqtt.broker.rule.store.RuleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * 规则增删改查 + 持久化 / 加载器编排。
 *
 * @author L.cm
 */
public class RuleManager {

	private static final Logger logger = LoggerFactory.getLogger(RuleManager.class);

	private final ActionRegistry actionRegistry = new ActionRegistry();
	private final MatcherRegistry matcherRegistry = new MatcherRegistry();
	private final PayloadCodecRegistry codecRegistry = new PayloadCodecRegistry();
	private final List<RuleLoader> loaders = new CopyOnWriteArrayList<>();
	private final List<RuleEventListener> listeners = new CopyOnWriteArrayList<>();
	private RuleStore ruleStore;
	private final AtomicBoolean started = new AtomicBoolean();

	/**
	 * 注册 action 工厂。
	 *
	 * @param factory 工厂
	 */
	public void registerActionFactory(ActionFactory factory) {
		actionRegistry.registerFactory(factory);
	}

	/**
	 * 注册 matcher 工厂。
	 *
	 * @param factory 工厂
	 */
	public void registerMatcherFactory(RuleMatcherFactory factory) {
		matcherRegistry.registerFactory(factory);
	}

	/**
	 * 注册 codec 工厂。
	 *
	 * @param factory 工厂
	 */
	public void registerCodecFactory(PayloadCodecFactory factory) {
		codecRegistry.registerFactory(factory);
	}

	public ActionRegistry getActionRegistry() {
		return actionRegistry;
	}

	public MatcherRegistry getMatcherRegistry() {
		return matcherRegistry;
	}

	public PayloadCodecRegistry getCodecRegistry() {
		return codecRegistry;
	}

	/**
	 * 设置持久化存储。
	 *
	 * @param store 存储
	 */
	public void setRuleStore(RuleStore store) {
		this.ruleStore = store;
	}

	/**
	 * 添加加载器。
	 *
	 * @param loader 加载器
	 */
	public void addLoader(RuleLoader loader) {
		this.loaders.add(loader);
	}

	/**
	 * 注册规则变更监听器。
	 *
	 * @param listener 监听器
	 */
	public void addListener(RuleEventListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * 新增规则。
	 *
	 * @param rule 规则
	 * @return 保存后的规则
	 */
	public Rule addRule(Rule rule) {
		if (ruleStore == null) {
			throw new IllegalStateException("RuleStore is not set");
		}
		ruleStore.save(rule);
		fireEvent(RuleEvent.added(rule));
		return rule;
	}

	/**
	 * 更新规则（按 id）。
	 *
	 * @param id      规则 id
	 * @param updater 转换函数
	 * @return 更新后的规则
	 */
	public Rule updateRule(String id, UnaryOperator<Rule> updater) {
		if (ruleStore == null) {
			throw new IllegalStateException("RuleStore is not set");
		}
		Rule prev = ruleStore.find(id);
		if (prev == null) {
			throw new IllegalArgumentException("Rule not found: " + id);
		}
		Rule next = updater.apply(prev);
		if (next == null) {
			throw new IllegalArgumentException("Updater returned null for rule " + id);
		}
		ruleStore.save(next);
		fireEvent(RuleEvent.updated(next, prev.getId()));
		return next;
	}

	/**
	 * 删除规则。
	 *
	 * @param id 规则 id
	 * @return 是否删除成功
	 */
	public boolean removeRule(String id) {
		if (ruleStore == null) {
			throw new IllegalStateException("RuleStore is not set");
		}
		if (ruleStore.find(id) == null) {
			return false;
		}
		ruleStore.delete(id);
		fireEvent(RuleEvent.removed(id));
		return true;
	}

	public Rule getRule(String id) {
		return ruleStore == null ? null : ruleStore.find(id);
	}

	public List<Rule> listRules() {
		return ruleStore == null ? new ArrayList<>() : ruleStore.loadAll();
	}

	/**
	 * 启动：执行加载器加载 + 监听 hot-reload。
	 * <p>
	 * 使用 {@link AtomicBoolean#compareAndSet(boolean, boolean)} 保证并发调用下只会
	 * 加载/派发一次事件（早期的 check-then-set 非原子，并发 start 会重复 fire ADDED）。
	 * </p>
	 */
	public void start() {
		if (!started.compareAndSet(false, true)) {
			return;
		}
		// 加载已有规则
		if (ruleStore != null) {
			for (Rule r : ruleStore.loadAll()) {
				fireEvent(RuleEvent.added(r));
			}
		}
		// 执行 loader
		for (RuleLoader loader : loaders) {
			try {
				List<Rule> rules = loader.loadAll();
				for (Rule r : rules) {
					if (ruleStore != null) {
						ruleStore.save(r);
					}
					fireEvent(RuleEvent.added(r));
				}
				loader.setListener(new RuleChangeListener() {
					@Override
					public void onSaved(Rule rule) {
						if (ruleStore != null) {
							ruleStore.save(rule);
						}
						fireEvent(RuleEvent.added(rule));
					}

					@Override
					public void onDeleted(String ruleId) {
						if (ruleStore != null) {
							ruleStore.delete(ruleId);
						}
						fireEvent(RuleEvent.removed(ruleId));
					}
				});
			} catch (Exception e) {
				logger.error("Failed to load rules from {}", loader.getSource(), e);
			}
		}
	}

	/**
	 * 停止：清空所有缓存与注册的监听器、加载器。
	 */
	public void stop() {
		if (!started.compareAndSet(true, false)) {
			return;
		}
		// 先摘除 listeners，避免 stop 过程中事件回调造成状态不一致
		listeners.clear();
		// 关闭各 registry 缓存的资源（actionRegistry 会关闭 MqttAction 持有的 MqttClient）
		actionRegistry.clear();
		matcherRegistry.clear();
		codecRegistry.clear();
		// 清空加载器引用，便于 GC
		loaders.clear();
	}

	private void fireEvent(RuleEvent event) {
		for (RuleEventListener l : listeners) {
			try {
				l.onRuleEvent(event);
			} catch (Exception e) {
				logger.warn("RuleEventListener failed: {}", e.getMessage(), e);
			}
		}
	}
}
