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

	/**
	 * 整条规则执行超时（毫秒），默认 5000，小于等于 0 表示不限时。
	 */
	public static final long DEFAULT_TIMEOUT_MS = 5000L;

	private final String id;
	private final String name;
	private final String topicFilter;
	private final boolean enabled;
	private final String codecType;
	private final String matcherType;
	private final Map<String, String> matcherProps;
	private final String whenExpr;
	private final List<ActionRef> actions;
	private final boolean stopOnError;
	private final long timeoutMs;
	private final Map<String, String> labels;

	private Rule(Builder b) {
		this.id = b.id != null ? b.id : UUID.randomUUID().toString();
		this.name = b.name;
		this.topicFilter = Objects.requireNonNull(b.topicFilter, "topicFilter is required");
		this.enabled = b.enabled;
		this.codecType = b.codecType;
		this.whenExpr = b.whenExpr;
		String matcherType = b.matcherType;
		Map<String, String> matcherProps = b.matcherProps;
		// when(expr) 是 matcherType=aviator 的语法糖：表达式编译失败会在挂载期暴露
		if (b.whenExpr != null && !b.whenExpr.isEmpty()) {
			matcherType = "aviator";
			matcherProps = new LinkedHashMap<>(matcherProps == null
				? Collections.emptyMap()
				: matcherProps);
			matcherProps.put("when", b.whenExpr);
		}
		this.matcherType = matcherType;
		this.matcherProps = matcherProps == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(matcherProps));
		List<ActionRef> actionList = new ArrayList<>(b.actions);
		this.actions = Collections.unmodifiableList(actionList);
		this.stopOnError = b.stopOnError;
		this.timeoutMs = b.timeoutMs;
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

	/**
	 * @return payload codec 名称，为 {@code null} 表示不解码（原始字节）
	 */
	public String getCodecType() {
		return codecType;
	}

	public String getMatcherType() {
		return matcherType;
	}

	public Map<String, String> getMatcherProps() {
		return matcherProps;
	}

	/**
	 * @return 直接书写的条件表达式（{@code when(expr)}），未使用则为 {@code null}
	 */
	public String getWhenExpr() {
		return whenExpr;
	}

	public List<ActionRef> getActions() {
		return actions;
	}

	public boolean isStopOnError() {
		return stopOnError;
	}

	/**
	 * @return 整条规则执行超时（毫秒），小于等于 0 表示不限时
	 */
	public long getTimeoutMs() {
		return timeoutMs;
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
		private String codecType;
		private String matcherType;
		private Map<String, String> matcherProps;
		private String whenExpr;
		private final List<ActionRef> actions = new ArrayList<>();
		private boolean stopOnError;
		private long timeoutMs = DEFAULT_TIMEOUT_MS;
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

		/**
		 * 指定 payload codec（{@code raw} / {@code string} / {@code json} 或 SPI 注册名）。
		 *
		 * @param codecType codec 名称，{@code null} 表示不解码
		 * @return this builder
		 */
		public Builder codecType(String codecType) {
			this.codecType = codecType;
			return this;
		}

		/**
		 * 直接书写条件表达式（等价于 {@code matcherType("aviator")}）。
		 *
		 * @param expr AviatorScript 表达式
		 * @return this builder
		 */
		public Builder when(String expr) {
			this.whenExpr = expr;
			return this;
		}

		/**
		 * 设置整条规则执行超时。
		 *
		 * @param timeoutMs 超时（毫秒），小于等于 0 表示不限时
		 * @return this builder
		 */
		public Builder timeoutMs(long timeoutMs) {
			this.timeoutMs = timeoutMs;
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
