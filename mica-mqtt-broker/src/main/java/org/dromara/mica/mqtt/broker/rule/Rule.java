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

import org.dromara.mica.mqtt.broker.rule.action.ActionRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变的规则定义，描述"如何运行"；不持有运行时对象。
 *
 * @author L.cm
 */
public final class Rule {

	private final String id;
	private final String name;
	private final String topicFilter;
	private final boolean enabled;
	private final String matcherType;
	private final Map<String, String> matcherProps;
	private final List<ActionRef> actions;
	private final boolean stopOnError;
	private final Map<String, String> labels;

	private Rule(Builder b) {
		this.id = b.id != null ? b.id : UUID.randomUUID().toString();
		this.name = b.name;
		this.topicFilter = Objects.requireNonNull(b.topicFilter, "topicFilter is required");
		this.enabled = b.enabled;
		this.matcherType = b.matcherType;
		this.matcherProps = b.matcherProps == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(b.matcherProps));
		List<ActionRef> actionList = new ArrayList<>(b.actions);
		this.actions = Collections.unmodifiableList(actionList);
		this.stopOnError = b.stopOnError;
		this.labels = b.labels == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(b.labels));
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getTopicFilter() {
		return topicFilter;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getMatcherType() {
		return matcherType;
	}

	public Map<String, String> getMatcherProps() {
		return matcherProps;
	}

	public List<ActionRef> getActions() {
		return actions;
	}

	public boolean isStopOnError() {
		return stopOnError;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Rule)) {
			return false;
		}
		Rule rule = (Rule) o;
		return Objects.equals(id, rule.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "Rule{" +
			"id='" + id + '\'' +
			", name='" + name + '\'' +
			", topicFilter='" + topicFilter + '\'' +
			", enabled=" + enabled +
			", actions=" + actions.size() +
			'}';
	}

	/**
	 * Rule 构建器。
	 */
	public static final class Builder {
		private String id;
		private String name;
		private String topicFilter;
		private boolean enabled = true;
		private String matcherType;
		private Map<String, String> matcherProps;
		private final List<ActionRef> actions = new ArrayList<>();
		private boolean stopOnError;
		private Map<String, String> labels;

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder topicFilter(String topicFilter) {
			this.topicFilter = topicFilter;
			return this;
		}

		public Builder enabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public Builder matcherType(String matcherType) {
			this.matcherType = matcherType;
			return this;
		}

		public Builder matcherProps(Map<String, String> matcherProps) {
			this.matcherProps = matcherProps;
			return this;
		}

		public Builder addAction(ActionRef ref) {
			this.actions.add(ref);
			return this;
		}

		public Builder actions(List<ActionRef> refs) {
			this.actions.clear();
			if (refs != null) {
				this.actions.addAll(refs);
			}
			return this;
		}

		public Builder stopOnError(boolean stopOnError) {
			this.stopOnError = stopOnError;
			return this;
		}

		public Builder labels(Map<String, String> labels) {
			this.labels = labels;
			return this;
		}

		public Rule build() {
			return new Rule(this);
		}
	}
}
