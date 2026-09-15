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

import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * 内置 HttpAction：JDK HttpURLConnection 同步调用（无三方依赖）。
 * <p>
 * MVP 阶段使用同步实现。后续会替换为 action 线程池 + 异步 HttpClient
 * （避免在 t-io IO 线程上阻塞慢 HTTP 后端）。
 * </p>
 *
 * @author L.cm
 */
public class HttpAction implements Action {

	private static final Logger logger = LoggerFactory.getLogger(HttpAction.class);

	private final String name;
	private final String url;
	private final String method;
	private final String contentType;
	private final int timeoutMs;
	private final Map<String, String> headers;

	public HttpAction(String name, String url, String method, String contentType,
					int timeoutMs, Map<String, String> headers) {
		this.name = name == null || name.isEmpty() ? "http" : name;
		this.url = url;
		this.method = method == null || method.isEmpty() ? "POST" : method.toUpperCase();
		this.contentType = contentType == null ? "application/octet-stream" : contentType;
		this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000;
		this.headers = headers;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void send(RuleContext ctx) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		try {
			conn.setRequestMethod(method);
			conn.setConnectTimeout(timeoutMs);
			conn.setReadTimeout(timeoutMs);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setRequestProperty("Content-Type", contentType);
			if (headers != null) {
				for (Map.Entry<String, String> e : headers.entrySet()) {
					if (e.getKey() != null) {
						conn.setRequestProperty(e.getKey(),
							e.getValue() == null ? "" : e.getValue());
					}
				}
			}
			// 透传 mqtt5 user property
			if (ctx.getHeaders() != null) {
				for (Map.Entry<String, String> e : ctx.getHeaders().entrySet()) {
					if (StrUtil.startWith(e.getKey(), "X-Mqtt-")) {
						conn.setRequestProperty(e.getKey(),
							e.getValue() == null ? "" : e.getValue());
					}
				}
			}
			// payload
			byte[] payload = ctx.getPayload();
			if (payload != null && payload.length > 0) {
				conn.setDoOutput(true);
				try (OutputStream os = conn.getOutputStream()) {
					os.write(payload);
				}
			}
			int code = conn.getResponseCode();
			if (code < 200 || code >= 300) {
				throw new IOException("HttpAction " + name
					+ " HTTP " + code + " from " + url);
			}
			logger.debug("HttpAction {} posted {} bytes to {} -> {}",
				name, payload == null ? 0 : payload.length, url, code);
		} finally {
			conn.disconnect();
		}
	}
}
