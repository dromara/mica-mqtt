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

package org.dromara.mica.mqtt.core.server.http.api.code;

import net.dreamlu.mica.net.http.common.HttpResponseStatus;

/**
 * 响应 code 码
 *
 * @author L.cm
 */
public enum ResultCode {
	/**
	 * 成功
	 */
	SUCCESS(HttpResponseStatus.C200, 1),
	/**
	 * 关键请求参数缺失
	 */
	E101(HttpResponseStatus.C400, 101),
	/**
	 * 请求参数错误
	 */
	E102(HttpResponseStatus.C400, 102),
	/**
	 * 用户名或密码错误
	 */
	E103(HttpResponseStatus.C401, 103),
	/**
	 * 请求方法错误
	 */
	E104(HttpResponseStatus.C405, 104),
	/**
	 * 未知错误
	 */
	E105(HttpResponseStatus.C500, 105),
	/**
	 * 请求方法错误
	 */
	E404(HttpResponseStatus.C404, 404),
	;

	private final HttpResponseStatus statusCode;
	private final int resultCode;

	ResultCode(HttpResponseStatus statusCode, int resultCode) {
		this.statusCode = statusCode;
		this.resultCode = resultCode;
	}

	public HttpResponseStatus getStatusCode() {
		return statusCode;
	}

	public int getResultCode() {
		return resultCode;
	}
}
