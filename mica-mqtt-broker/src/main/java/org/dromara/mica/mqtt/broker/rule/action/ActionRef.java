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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的 action 引用（type + name + props）。
 * <p>
 * 使用 {@link Builder} 链式构建，避免每次 {@code prop()} 调用都复制整个 props Map。
 * </p>
 *
 * @author L.cm
 */
public final class ActionRef {

	private final String type;
	private final String name;
	private final Map<String, Object> props;

	private ActionRef(String type, String name, Map<String, Object> props) {
		this.type = Objects.requireNonNull(type, "type is required");
		this.name = (name == null || name.isEmpty()) ? type : name;
		this.props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
	}

	public static ActionRef of(String type) {
		return new ActionRef(type, type, Collections.<String, Object>emptyMap());
	}

	public static ActionRef of(String type, String name) {
		return new ActionRef(type, name, Collections.<String, Object>emptyMap());
	}

	/**
	 * 创建可变 builder，便于一次性追加多个 prop，避免链式 {@code prop()} 重复复制。
	 *
	 * @param type action 类型
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

	/**
	 * 获取字符串属性，缺失返回 {@code null}。
	 *
	 * @param key 属性名
	 * @return 字符串值
	 */
	public String getString(String key) {
		Object v = props.get(key);
		return v == null ? null : v.toString();
	}

	/**
	 * 获取字符串属性，缺失或为 {@code null} 返回默认值。
	 *
	 * @param key 属性名
	 * @param def 默认值
	 * @return 字符串值
	 */
	public String getString(String key, String def) {
		Object v = props.get(key);
		return v == null ? def : v.toString();
	}

	/**
	 * 获取 int 属性，缺失返回 {@code null}，无法解析抛 {@link NumberFormatException}。
	 *
	 * @param key 属性名
	 * @return 整数值
	 */
	public Integer getInt(String key) {
		Object v = props.get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Number) {
			return ((Number) v).intValue();
		}
		return Integer.parseInt(v.toString());
	}

	/**
	 * 获取 int 属性，缺失或无法解析时返回默认值。
	 *
	 * @param key 属性名
	 * @param def 默认值
	 * @return 整数值
	 */
	public int getInt(String key, int def) {
		Object v = props.get(key);
		if (v == null) {
			return def;
		}
		try {
			return Integer.parseInt(v.toString());
		} catch (NumberFormatException nfe) {
			return def;
		}
	}

	/**
	 * 获取 long 属性，缺失返回 {@code null}，无法解析抛 {@link NumberFormatException}。
	 *
	 * @param key 属性名
	 * @return 长整数值
	 */
	public Long getLong(String key) {
		Object v = props.get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Number) {
			return ((Number) v).longValue();
		}
		return Long.parseLong(v.toString());
	}

	/**
	 * 获取 long 属性，缺失或无法解析时返回默认值。
	 *
	 * @param key 属性名
	 * @param def 默认值
	 * @return 长整数值
	 */
	public long getLong(String key, long def) {
		Object v = props.get(key);
		if (v == null) {
			return def;
		}
		try {
			return Long.parseLong(v.toString());
		} catch (NumberFormatException nfe) {
			return def;
		}
	}

	/**
	 * 获取 boolean 属性，缺失返回 {@code null}，无法解析抛 {@link IllegalArgumentException}。
	 *
	 * @param key 属性名
	 * @return 布尔值
	 */
	public Boolean getBoolean(String key) {
		Object v = props.get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Boolean) {
			return (Boolean) v;
		}
		return Boolean.parseBoolean(v.toString());
	}

	/**
	 * 获取 boolean 属性，缺失返回默认值。
	 *
	 * @param key 属性名
	 * @param def 默认值
	 * @return 布尔值
	 */
	public boolean getBoolean(String key, boolean def) {
		Object v = props.get(key);
		if (v == null) {
			return def;
		}
		if (v instanceof Boolean) {
			return (Boolean) v;
		}
		return Boolean.parseBoolean(v.toString());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ActionRef)) {
			return false;
		}
		ActionRef that = (ActionRef) o;
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
		return "ActionRef{" +
			"type='" + type + '\'' +
			", name='" + name + '\'' +
			", props=" + props.size() +
			'}';
	}

	/**
	 * ActionRef 可变构建器，一次性累积 props 后生成不可变 ActionRef。
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

		public ActionRef build() {
			return new ActionRef(type, name, props);
		}
	}
}
