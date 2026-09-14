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

package org.dromara.mica.mqtt.broker.rule.sink;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的 sink 引用（type + name + props）。
 * <p>
 * 使用 {@link Builder} 链式构建，避免每次 {@code prop()} 调用都复制整个 props Map。
 * </p>
 *
 * @author L.cm
 */
public final class SinkRef {

	private final String type;
	private final String name;
	private final Map<String, Object> props;

	private SinkRef(String type, String name, Map<String, Object> props) {
		this.type = Objects.requireNonNull(type, "type is required");
		this.name = (name == null || name.isEmpty()) ? type : name;
		this.props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
	}

	public static SinkRef of(String type) {
		return new SinkRef(type, type, Collections.<String, Object>emptyMap());
	}

	public static SinkRef of(String type, String name) {
		return new SinkRef(type, name, Collections.<String, Object>emptyMap());
	}

	/**
	 * 创建可变 builder，便于一次性追加多个 prop，避免链式 {@code prop()} 重复复制。
	 *
	 * @param type sink 类型
	 * @return builder
	 */
	public static Builder builder(String type) {
		return new Builder(type);
	}

	/**
	 * 基于当前实例派生 builder（继承 type/name/props）。
	 *
	 * @return builder
	 */
	public Builder toBuilder() {
		return new Builder(type, name, props);
	}

	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public Map<String, Object> getProps() {
		return props;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SinkRef)) {
			return false;
		}
		SinkRef that = (SinkRef) o;
		return Objects.equals(type, that.type)
			&& Objects.equals(name, that.name)
			&& Objects.equals(props, that.props);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, name, props);
	}

	@Override
	public String toString() {
		return "SinkRef{" +
			"type='" + type + '\'' +
			", name='" + name + '\'' +
			", props=" + props.size() +
			'}';
	}

	/**
	 * SinkRef 可变构建器，一次性累积 props 后生成不可变 SinkRef。
	 */
	public static final class Builder {
		private final String type;
		private String name;
		private final Map<String, Object> props = new LinkedHashMap<>();

		private Builder(String type) {
			this(type, type, Collections.<String, Object>emptyMap());
		}

		private Builder(String type, String name, Map<String, Object> props) {
			this.type = Objects.requireNonNull(type, "type is required");
			this.name = name;
			if (props != null) {
				this.props.putAll(props);
			}
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder prop(String key, Object value) {
			this.props.put(key, value);
			return this;
		}

		public Builder props(Map<String, Object> props) {
			if (props != null) {
				this.props.putAll(props);
			}
			return this;
		}

		public SinkRef build() {
			return new SinkRef(type, name, props);
		}
	}
}
