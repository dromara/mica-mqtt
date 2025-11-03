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

package org.dromara.mica.mqtt.core.server.http.api.auth;

import org.dromara.mica.mqtt.core.server.http.api.code.ResultCode;
import org.dromara.mica.mqtt.core.server.http.api.result.Result;
import org.dromara.mica.mqtt.core.server.http.handler.HttpFilter;
import org.tio.http.common.HeaderName;
import org.tio.http.common.HeaderValue;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.utils.hutool.StrUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Basic 认证
 *
 * @author L.cm
 */
public class BasicAuthFilter implements HttpFilter {
	public static final HeaderName WWW_AUTHENTICATE = HeaderName.from("WWW-Authenticate");
	public static final HeaderValue BASIC_REALM = HeaderValue.from("Basic realm=\"Mica mqtt realm\"");
	public static final String BASIC_AUTH_HEADER_NAME = "authorization";
	public static final String AUTHORIZATION_PREFIX = "Basic ";
	private final String token;

	public BasicAuthFilter(String username, String password) {
		this.token = getBasicToken(username, password);
	}

	@Override
	public boolean filter(HttpRequest request) throws Exception {
		String authorization = request.getHeader(BASIC_AUTH_HEADER_NAME);
		if (StrUtil.isBlank(authorization)) {
			return false;
		}
		int length = AUTHORIZATION_PREFIX.length();
		if (length >= authorization.length()) {
			return false;
		}
		return token.equals(authorization.substring(length));
	}

	@Override
	public HttpResponse response(HttpRequest request) {
		HttpResponse response = new HttpResponse(request);
		response.addHeader(WWW_AUTHENTICATE, BASIC_REALM);
		return Result.fail(response, ResultCode.E103);
	}

	private static String getBasicToken(String username, String password) {
		Objects.requireNonNull(username, "Basic auth username is null");
		Objects.requireNonNull(password, "Basic auth password is null");
		byte[] tokenBytes = (username + ':' + password).getBytes(StandardCharsets.UTF_8);
		return Base64.getEncoder().encodeToString(tokenBytes);
	}
}
