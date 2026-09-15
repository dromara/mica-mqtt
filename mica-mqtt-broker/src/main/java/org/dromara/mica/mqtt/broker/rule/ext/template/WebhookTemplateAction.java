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
import org.dromara.mica.mqtt.broker.rule.ext.aviator.AviatorExprMatcher;
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * webhook 模板：HTTP 推送 + Aviator 字符串模板插值 + 重试。
 *
 * @author L.cm
 */
public class WebhookTemplateAction implements Action {

	private static final Logger logger = LoggerFactory.getLogger(WebhookTemplateAction.class);

	private final ActionRef ref;

	public WebhookTemplateAction(ActionRef ref) {
		this.ref = ref;
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	@Override
	public void send(RuleContext ctx) throws Exception {
		String url = ref.getString("url");
		if (url == null) {
			throw new IllegalArgumentException("webhook template requires 'url' prop");
		}
		String method = ref.getString("method", "POST").toUpperCase();
		String contentType = ref.getString("contentType", "application/json");
		int timeoutMs = ref.getInt("timeoutMs", 3000);
		int retry = ref.getInt("retry", 0);
		Object hdrObj = ref.getProps().get("headers");
		@SuppressWarnings("unchecked")
		Map<String, String> headers = hdrObj instanceof Map
			? (Map<String, String>) hdrObj
			: java.util.Collections.emptyMap();
		String template = ref.getString("template");
		String body;
		if (template != null) {
			body = interpolate(template, ctx);
		} else {
			body = new String(ctx.getPayload() == null ? new byte[0] : ctx.getPayload(),
				StandardCharsets.UTF_8);
		}
		int attempts = retry + 1;
		Exception last = null;
		for (int i = 0; i < attempts; i++) {
			try {
				sendOnce(url, method, contentType, headers, body, timeoutMs);
				return;
			} catch (Exception e) {
				last = e;
				logger.warn("webhook attempt {}/{} failed: {}", i + 1, attempts, e.getMessage());
			}
		}
		throw new RuntimeException("webhook all attempts failed", last);
	}

	private String interpolate(String template, RuleContext ctx) {
		// 简单占位符 ${expr} -> aviator 求值
		Matcher matcher = Pattern
			.compile("\\$\\{([^}]+)}").matcher(template);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String expr = matcher.group(1);
			Object v;
			try {
				AviatorExprMatcher m = new AviatorExprMatcher(expr);
				v = m.execute(AviatorExprMatcher.envOf(ctx));
			} catch (Exception e) {
				v = "";
			}
			matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v == null ? "" : v.toString()));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private void sendOnce(String url, String method, String contentType,
						 Map<String, String> headers, String body, int timeoutMs) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod(method);
		conn.setConnectTimeout(timeoutMs);
		conn.setReadTimeout(timeoutMs);
		conn.setDoOutput(true);
		byte[] data = body.getBytes(StandardCharsets.UTF_8);
		conn.setRequestProperty("Content-Type", contentType);
		conn.setRequestProperty("Content-Length", String.valueOf(data.length));
		for (Map.Entry<String, String> e : headers.entrySet()) {
			conn.setRequestProperty(e.getKey(), e.getValue());
		}
		if (conn instanceof HttpsURLConnection) {
			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
			((HttpsURLConnection) conn).setSSLSocketFactory(ctx.getSocketFactory());
		}
		try {
			conn.connect();
			try (OutputStream os = conn.getOutputStream()) {
				os.write(data);
			}
			int code = conn.getResponseCode();
			if (code >= 400) {
				throw new RuntimeException("webhook HTTP " + code);
			}
		} finally {
			conn.disconnect();
		}
	}

	private static class TrustAllManager implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	/**
	 * ActionFactory：注册 type=webhook。
	 */
	public static class Factory implements ActionFactory {
		@Override
		public String getType() {
			return "webhook";
		}

		@Override
		public Action create(ActionRef ref) {
			return new WebhookTemplateAction(ref);
		}
	}
}
