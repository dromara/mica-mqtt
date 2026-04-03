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

package org.dromara.mica.mqtt.core.server.http.handler;

import net.dreamlu.mica.net.http.common.Method;

import java.util.Objects;

/**
 * Handler info
 *
 * @author L.cm
 */
public class RouteInfo {
	private final String path;
	private final Method method;

	public RouteInfo(String path, Method method) {
		this.path = path;
		this.method = method;
	}

	public String getPath() {
		return path;
	}

	public Method getMethod() {
		return method;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RouteInfo that = (RouteInfo) o;
		return Objects.equals(path, that.path) && method == that.method;
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, method);
	}

	@Override
	public String toString() {
		return "HandlerInfo{" +
			"path='" + path + '\'' +
			", method=" + method +
			'}';
	}
}
