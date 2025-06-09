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

import org.tio.http.common.Method;
import org.tio.http.common.RequestLine;
import org.tio.http.common.handler.HttpRequestFunction;

import java.util.*;

/**
 * mqtt http api 路由
 *
 * @author L.cm
 */
public final class MqttHttpRoutes {
	private static final LinkedList<HttpFilter> FILTERS = new LinkedList<>();
	private static final Map<RouteInfo, HttpRequestFunction> ROUTS = new HashMap<>();

	/**
	 * 注册 filter 到 first
	 *
	 * @param filter HttpFilter
	 */
	public static void addFirstFilter(HttpFilter filter) {
		FILTERS.addFirst(filter);
	}

	/**
	 * 注册 filter
	 *
	 * @param filter HttpFilter
	 */
	public static void addFilter(HttpFilter filter) {
		FILTERS.add(filter);
	}

	/**
	 * 注册 filter
	 *
	 * @param index  index
	 * @param filter HttpFilter
	 */
	public static void addFilter(int index, HttpFilter filter) {
		FILTERS.add(index, filter);
	}

	/**
	 * 读取所以的过滤器
	 *
	 * @return 过滤器集合
	 */
	public static List<HttpFilter> getFilters() {
		return Collections.unmodifiableList(FILTERS);
	}

	/**
	 * 注册路由
	 *
	 * @param method  请求方法
	 * @param path    路径
	 * @param function HttpHandler
	 */
	public static void register(Method method, String path, HttpRequestFunction function) {
		ROUTS.put(new RouteInfo(path, method), function);
	}

	/**
	 * 读取路由
	 *
	 * @param requestLine RequestLine
	 * @return HttpHandler
	 */
	public static HttpRequestFunction getHandler(RequestLine requestLine) {
		String path = requestLine.getPath();
		Method method = requestLine.getMethod();
		return ROUTS.get(new RouteInfo(path, method));
	}

	/**
	 * 读取所有路由
	 *
	 * @return 路由信息
	 */
	public static Map<RouteInfo, HttpRequestFunction> getRouts() {
		return Collections.unmodifiableMap(ROUTS);
	}

}
