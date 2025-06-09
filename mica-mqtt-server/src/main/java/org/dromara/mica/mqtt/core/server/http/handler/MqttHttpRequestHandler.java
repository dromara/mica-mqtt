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

import org.dromara.mica.mqtt.core.server.http.api.code.ResultCode;
import org.dromara.mica.mqtt.core.server.http.api.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.http.common.RequestLine;
import org.tio.http.common.handler.HttpRequestFunction;
import org.tio.http.common.handler.HttpRequestHandler;

import java.util.List;

/**
 * mqtt http 消息处理
 *
 * @author L.cm
 */
public class MqttHttpRequestHandler implements HttpRequestHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttHttpRequestHandler.class);

	@Override
	public HttpResponse handler(HttpRequest request) {
		RequestLine requestLine = request.getRequestLine();
		// 1. 处理过滤器
		List<HttpFilter> httpFilters = MqttHttpRoutes.getFilters();
		try {
			for (HttpFilter filter : httpFilters) {
				if (!filter.filter(request)) {
					return filter.response(request);
				}
			}
		} catch (Exception e) {
			return resp500(request, requestLine, e);
		}
		// 2. 路由处理
		HttpRequestFunction handler = MqttHttpRoutes.getHandler(requestLine);
		if (handler == null) {
			return resp404(request, requestLine);
		}
		if (logger.isInfoEnabled()) {
			logger.info("mqtt http api {} path:{}", requestLine.method, requestLine.getPathAndQuery());
		}
		try {
			return handler.apply(request);
		} catch (Exception e) {
			return resp500(request, requestLine, e);
		}
	}

	@Override
	public HttpResponse resp404(HttpRequest request, RequestLine requestLine) {
		if (logger.isErrorEnabled()) {
			logger.error("mqtt http {} path:{} 404", requestLine.getMethod().name(), requestLine.getPathAndQuery());
		}
		return Result.fail(request, ResultCode.E404);
	}

	@Override
	public HttpResponse resp500(HttpRequest request, RequestLine requestLine, Throwable throwable) {
		if (logger.isErrorEnabled()) {
			logger.error("mqtt http {} path:{} error", requestLine.getMethod().name(), requestLine.getPathAndQuery(), throwable);
		}
		return Result.fail(request, ResultCode.E105);
	}

}
