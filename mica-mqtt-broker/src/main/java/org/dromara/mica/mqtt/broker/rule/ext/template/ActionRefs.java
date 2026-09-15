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

package org.dromara.mica.mqtt.broker.rule.ext.template;

import org.dromara.mica.mqtt.broker.rule.action.ActionRef;

/**
 * ActionRef 便捷取值工具。
 *
 * @author L.cm
 */
public final class ActionRefs {

	private ActionRefs() {
	}

	public static String getString(ActionRef ref, String key) {
		Object v = ref.getProps().get(key);
		return v == null ? null : v.toString();
	}

	public static String getString(ActionRef ref, String key, String def) {
		Object v = ref.getProps().get(key);
		return v == null ? def : v.toString();
	}

	public static Integer getInt(ActionRef ref, String key) {
		Object v = ref.getProps().get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Number) {
			return ((Number) v).intValue();
		}
		return Integer.parseInt(v.toString());
	}

	public static Long getLong(ActionRef ref, String key) {
		Object v = ref.getProps().get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Number) {
			return ((Number) v).longValue();
		}
		return Long.parseLong(v.toString());
	}

	public static Boolean getBoolean(ActionRef ref, String key) {
		Object v = ref.getProps().get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Boolean) {
			return (Boolean) v;
		}
		return Boolean.parseBoolean(v.toString());
	}
}