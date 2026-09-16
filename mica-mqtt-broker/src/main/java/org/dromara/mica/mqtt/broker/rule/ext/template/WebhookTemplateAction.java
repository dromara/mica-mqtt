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

import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * webhook 模板：HTTP 推送 + Aviator 字符串模板插值 + 重试。
 *
 * <p>YAML 配置：
 * <pre>
 * - type: webhook
 *   name: push
 *   props:
 *     url: "https://example.com/hook"
 *     method: POST
 *     template: "{\"t\":${payload.temperature}}"
 *     retry: 2
 * </pre>
 *
 * <p>HTTPS 使用 JDK 默认信任链。如需对接自签名证书服务端，请通过
 * {@link TemplateServices} 注入自定义 {@link SSLSocketFactory}，不要关闭证书校验。
 *
 * @author L.cm
 */
public class WebhookTemplateAction implements Action {

	private static final Logger logger = LoggerFactory.getLogger(WebhookTemplateAction.class);

	private final ActionRef ref;
	private final String url;
	private final String method;
	private final String contentType;
	private final int timeoutMs;
	private final int retry;
	private final Map<String, String> headers;
	private final String template;
	private final SSLSocketFactory sslSocketFactory;

	@SuppressWarnings("unchecked")
	public WebhookTemplateAction(ActionRef ref, SSLSocketFactory sslSocketFactory) {
		this.ref = ref;
		this.url = requireUrl(ref);
		this.method = ref.getString("method", "POST").toUpperCase();
		this.contentType = ref.getString("contentType", "application/json");
		this.timeoutMs = ref.getInt("timeoutMs", 3000);
		this.retry = Math.max(0, ref.getInt("retry", 0));
		Object hdr = ref.getProps().get("headers");
		this.headers = hdr instanceof Map ? (Map<String, String>) hdr : Collections.<String, String>emptyMap();
		this.template = ref.getString("template");
		this.sslSocketFactory = sslSocketFactory;
	}

	private static String requireUrl(ActionRef ref) {
		String url = ref.getString("url");
		if (url == null || url.isEmpty()) {
			throw new IllegalArgumentException("webhook action requires 'url' prop");
		}
		return url;
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	@Override
	public void send(RuleContext ctx) {
		byte[] payload = ctx.getPayload() == null ? new byte[0] : ctx.getPayload();
		String body = template != null
			? TemplateExpressions.interpolate(template, ctx)
			: new String(payload, StandardCharsets.UTF_8);
		int attempts = retry + 1;
		Exception last = null;
		for (int i = 0; i < attempts; i++) {
			try {
				sendOnce(body);
				return;
			} catch (Exception e) {
				last = e;
				logger.warn("webhook attempt {}/{} failed: {}", i + 1, attempts, e.getMessage());
			}
		}
		throw new IllegalStateException("webhook all attempts failed", last);
	}

	private void sendOnce(String body) throws Exception {
		byte[] data = body.getBytes(StandardCharsets.UTF_8);
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		try {
			conn.setRequestMethod(method);
			conn.setConnectTimeout(timeoutMs);
			conn.setReadTimeout(timeoutMs);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", contentType);
			conn.setRequestProperty("Content-Length", String.valueOf(data.length));
			for (Map.Entry<String, String> e : headers.entrySet()) {
				conn.setRequestProperty(e.getKey(), e.getValue());
			}
			if (sslSocketFactory != null && conn instanceof HttpsURLConnection) {
				((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
			}
			conn.connect();
			try (OutputStream os = conn.getOutputStream()) {
				os.write(data);
			}
			int code = conn.getResponseCode();
			if (code >= 400) {
				throw new IllegalStateException("webhook HTTP " + code);
			}
		} finally {
			conn.disconnect();
		}
	}

	/**
	 * ActionFactory：注册 type=webhook。
	 */
	public static class Factory implements TemplateActionFactory {
		private volatile TemplateServices services;

		@Override
		public String getType() {
			return "webhook";
		}

		@Override
		public void setTemplateServices(TemplateServices services) {
			this.services = services;
		}

		@Override
		public Action create(ActionRef ref) {
			TemplateServices current = services;
			return new WebhookTemplateAction(ref, current == null ? null : current.getSslSocketFactory());
		}
	}
}
