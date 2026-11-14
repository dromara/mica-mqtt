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

import net.dreamlu.mica.net.http.common.HeaderName;
import net.dreamlu.mica.net.http.common.HeaderValue;
import net.dreamlu.mica.net.http.common.HttpRequest;
import net.dreamlu.mica.net.http.common.HttpResponse;
import net.dreamlu.mica.net.http.common.router.HttpFilter;
import net.dreamlu.mica.net.http.common.router.HttpFilterChain;
import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.core.server.http.api.code.ResultCode;
import org.dromara.mica.mqtt.core.server.http.api.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Token 认证过滤器,从 {@code <headerName>: <scheme> <token>} 中提取 token,
 * 委托给 {@link ITokenValidator} 校验。
 * <p>
 * 用户可自行实现 {@link ITokenValidator} 调用 OAuth2 / 自建服务 / JWT 解析等逻辑。
 * 也可直接复用内置 {@link BasicAuthValidator} 实现 Basic 认证。
 *
 * @author L.cm
 */
public class TokenAuthFilter implements HttpFilter {
	private static final Logger logger = LoggerFactory.getLogger(TokenAuthFilter.class);
	public static final HeaderName WWW_AUTHENTICATE = HeaderName.from("WWW-Authenticate");
	public static final String AUTHORIZATION_HEADER_NAME = "authorization";
	public static final String BEARER_SCHEME = "Bearer";
	public static final String DEFAULT_REALM = "Mica mqtt realm";

	private final String headerName;
	private final String schemePrefix;
	private final HeaderValue wwwAuthenticate;
	private final ITokenValidator tokenValidator;

	/**
	 * 使用 Bearer scheme + 默认 Authorization 头构造
	 *
	 * @param tokenValidator token 校验器
	 */
	public TokenAuthFilter(ITokenValidator tokenValidator) {
		this(AUTHORIZATION_HEADER_NAME, BEARER_SCHEME, tokenValidator);
	}

	/**
	 * 自定义 headerName,使用 Bearer scheme + 默认 realm
	 *
	 * @param headerName     存放 token 的请求头名称,默认 {@value #AUTHORIZATION_HEADER_NAME}
	 * @param tokenValidator token 校验器
	 */
	public TokenAuthFilter(String headerName, ITokenValidator tokenValidator) {
		this(headerName, BEARER_SCHEME, tokenValidator);
	}

	/**
	 * 自定义 headerName + scheme,使用默认 realm
	 *
	 * @param headerName     存放 token 的请求头名称,默认 {@value #AUTHORIZATION_HEADER_NAME}
	 * @param scheme         认证 scheme(Basic / Bearer / 自定义)
	 * @param tokenValidator token 校验器
	 */
	public TokenAuthFilter(String headerName, String scheme, ITokenValidator tokenValidator) {
		Objects.requireNonNull(headerName, "Auth header name is null");
		Objects.requireNonNull(scheme, "Auth scheme is null");
		this.headerName = headerName;
		this.schemePrefix = scheme + ' ';
		this.wwwAuthenticate = HeaderValue.from(scheme + " realm=\"" + DEFAULT_REALM + "\"");
		this.tokenValidator = Objects.requireNonNull(tokenValidator, "Token validator is null");
	}

	@Override
	public HttpResponse doFilter(HttpRequest request, HttpFilterChain chain) throws Exception {
		String authorization = request.getHeader(headerName);
		if (StrUtil.isBlank(authorization)) {
			return unauthorized(request);
		}
		int length = schemePrefix.length();
		if (authorization.length() <= length
			|| !authorization.regionMatches(true, 0, schemePrefix, 0, length)) {
			return unauthorized(request);
		}
		String token = authorization.substring(length).trim();
		if (StrUtil.isBlank(token)) {
			return unauthorized(request);
		}
		boolean authenticated;
		try {
			authenticated = tokenValidator.validate(request, token);
		} catch (Throwable e) {
			logger.error("{} auth error, header: {}", schemePrefix.trim(), headerName, e);
			return unauthorized(request);
		}
		if (authenticated) {
			return chain.doFilter(request);
		}
		return unauthorized(request);
	}

	/**
	 * 返回未认证
	 *
	 * @param request 请求
	 * @return HttpResponse
	 */
	protected HttpResponse unauthorized(HttpRequest request) {
		HttpResponse response = new HttpResponse(request);
		response.addHeader(WWW_AUTHENTICATE, wwwAuthenticate);
		return Result.fail(response, ResultCode.E103);
	}
}
