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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认 {@link RuleStore} 实现：内存 ConcurrentMap。
 *
 * @author L.cm
 */
public class InMemoryRuleStore implements RuleStore {

	private static final Logger logger = LoggerFactory.getLogger(InMemoryRuleStore.class);

	private final ConcurrentMap<String, Rule> rules = new ConcurrentHashMap<>();
	private volatile RuleChangeListener listener;

	@Override
	public void save(Rule rule) {
		Rule prev = rules.put(rule.getId(), rule);
		RuleChangeListener l = listener;
		if (l != null) {
			try {
				l.onSaved(rule);
			} catch (Exception e) {
				logger.warn("RuleChangeListener.onSaved failed for rule {}", rule.getId(), e);
			}
		}
		if (prev == null) {
			logger.info("Rule saved: id={}, name={}", rule.getId(), rule.getName());
		}
	}

	@Override
	public void delete(String id) {
		Rule prev = rules.remove(id);
		if (prev != null) {
			RuleChangeListener l = listener;
			if (l != null) {
				try {
					l.onDeleted(id);
				} catch (Exception e) {
					logger.warn("RuleChangeListener.onDeleted failed for rule {}", id, e);
				}
			}
			logger.info("Rule deleted: id={}", id);
		}
	}

	@Override
	public Rule find(String id) {
		return rules.get(id);
	}

	@Override
	public List<Rule> loadAll() {
		return new ArrayList<>(rules.values());
	}

	@Override
	public void setListener(RuleChangeListener listener) {
		this.listener = listener;
	}
}
