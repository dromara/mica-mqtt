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

package org.dromara.mica.mqtt.core.server.http.api.result;

import org.dromara.mica.mqtt.core.server.http.api.code.ResultCode;
import org.tio.http.common.*;
import org.tio.utils.json.JsonUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * api Result
 *
 * @author L.cm
 */
public final class Result {

	/**
	 * 响应成功
	 *
	 * @return HttpResponse
	 */
	public static HttpResponse ok() {
		return ok(new HttpResponse());
	}

	/**
	 * 响应成功
	 *
	 * @param response HttpResponse
	 * @return HttpResponse
	 */
	public static HttpResponse ok(HttpResponse response) {
		return result(response, ResultCode.SUCCESS);
	}

	/**
	 * 响应成功
	 *
	 * @param data Object
	 * @return HttpResponse
	 */
	public static HttpResponse ok(Object data) {
		return ok(new HttpResponse(), data);
	}

	/**
	 * 响应成功
	 *
	 * @param request HttpRequest
	 * @param data    Object
	 * @return HttpResponse
	 */
	public static HttpResponse ok(HttpRequest request, Object data) {
		return ok(new HttpResponse(request), data);
	}

	/**
	 * 响应成功
	 *
	 * @param response HttpResponse
	 * @param data     Object
	 * @return HttpResponse
	 */
	public static HttpResponse ok(HttpResponse response, Object data) {
		ResultCode resultCode = ResultCode.SUCCESS;
		Map<String, Object> json = new HashMap<>(4);
		json.put("code", resultCode.getResultCode());
		json.put("data", data);
		return result(response, resultCode, json);
	}

	/**
	 * 响应失败
	 *
	 * @param resultCode ResultCode
	 * @return HttpResponse
	 */
	public static HttpResponse fail(ResultCode resultCode) {
		return fail(new HttpResponse(), resultCode);
	}

	/**
	 * 响应失败
	 *
	 * @param request    HttpRequest
	 * @param resultCode ResultCode
	 * @return HttpResponse
	 */
	public static HttpResponse fail(HttpRequest request, ResultCode resultCode) {
		return fail(new HttpResponse(request), resultCode);
	}

	/**
	 * 响应失败
	 *
	 * @param response   HttpResponse
	 * @param resultCode ResultCode
	 * @return HttpResponse
	 */
	public static HttpResponse fail(HttpResponse response, ResultCode resultCode) {
		return result(response, resultCode);
	}

	private static HttpResponse result(HttpResponse response, ResultCode resultCode) {
		Map<String, Object> json = new HashMap<>(2);
		json.put("code", resultCode.getResultCode());
		return result(response, resultCode, json);
	}

	private static HttpResponse result(HttpResponse response, ResultCode resultCode, Object value) {
		response.addHeader(HeaderName.Content_Type, HeaderValue.Content_Type.APPLICATION_JSON);
		response.setStatus(resultCode.getStatusCode());
		response.setBody(JsonUtil.toJsonString(value).getBytes(HttpConst.CHARSET));
		response.setCharset(HttpConst.CHARSET);
		return response;
	}

}
