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

import org.dromara.mica.mqtt.broker.rule.Rule;
import org.dromara.mica.mqtt.broker.rule.RuleChannelInfo;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.RuleEngine;
import org.dromara.mica.mqtt.broker.rule.RuleManager;
import org.dromara.mica.mqtt.broker.rule.store.InMemoryRuleStore;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HttpAction 单测：同步调用、错误码、以及「规则命中不阻塞 IO 线程」的异步行为。
 *
 * @author L.cm
 */
class HttpActionTest {

	@Test
	void sendsPayloadToEndpoint() throws Exception {
		try (TinyHttpServer server = new TinyHttpServer(0L, 200)) {
			HttpAction action = new HttpAction("http-1", server.url(), "POST",
				"application/json", 3000, Collections.emptyMap());
			RuleContext ctx = newContext("hello".getBytes());
			action.send(ctx);
			assertTrue(server.received().await(3, TimeUnit.SECONDS));
			assertEquals("hello", server.body());
		}
	}

	@Test
	void nonSuccessStatusRaisesException() throws Exception {
		try (TinyHttpServer server = new TinyHttpServer(0L, 500)) {
			HttpAction action = new HttpAction("http-2", server.url(), "POST",
				"application/json", 3000, Collections.emptyMap());
			RuleContext ctx = newContext("hello".getBytes());
			assertThrows(Exception.class, () -> action.send(ctx));
		}
	}

	/**
	 * 慢后端不应阻塞 IO 线程：规则命中后 IO 线程立即返回，请求在 action 线程池上完成。
	 */
	@Test
	void slowEndpointDoesNotBlockCallerThread() throws Exception {
		try (TinyHttpServer server = new TinyHttpServer(800L, 200)) {
			RuleManager rm = new RuleManager();
			rm.setRuleStore(new InMemoryRuleStore());
			rm.getActionRegistry().registerFactory(new HttpActionFactory());
			MqttFunctionManager fnMgr = new MqttFunctionManager();
			RuleEngine engine = new RuleEngine(rm, fnMgr, null);
			engine.attach();
			engine.start();

			rm.addRule(Rule.builder()
				.id("http-rule")
				.topicFilter("http/#")
				.addAction(ActionRef.builder("http")
					.prop("url", server.url())
					.prop("method", "POST")
					.prop("timeoutMs", "3000")
					.build())
				.build());

			long start = System.currentTimeMillis();
			invoke(fnMgr, "http/x", "payload".getBytes());
			long cost = System.currentTimeMillis() - start;
			assertTrue(cost < 300L, "IO thread should not wait for the slow endpoint, cost=" + cost + "ms");
			assertTrue(server.received().await(5, TimeUnit.SECONDS));
			assertEquals("payload", server.body());
		}
	}

	private static RuleContext newContext(byte[] payload) {
		return new RuleContext(null, new RuleChannelInfo(null, 0, null),
			"client1", "http/x", MqttQoS.QOS0, payload, false, Collections.emptyMap(), null);
	}

	private static void invoke(MqttFunctionManager fnMgr, String topic, byte[] payload) {
		MqttFixedHeader header = new MqttFixedHeader(
			org.dromara.mica.mqtt.codec.MqttMessageType.PUBLISH, false, MqttQoS.QOS0, false, 0);
		MqttPublishMessage msg = new MqttPublishMessage(header,
			new MqttPublishVariableHeader(topic, 0), payload);
		for (org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener fn : fnMgr.get(topic)) {
			fn.onMessage(null, "client1", topic, MqttQoS.QOS0, msg);
		}
	}

	/**
	 * 极简 HTTP 服务端：接收一次请求并返回固定状态码。
	 */
	private static final class TinyHttpServer implements AutoCloseable {
		private final ServerSocket serverSocket;
		private final Thread thread;
		private final CountDownLatch received = new CountDownLatch(1);
		private final AtomicReference<String> body = new AtomicReference<>("");

		TinyHttpServer(long delayMs, int status) throws Exception {
			this.serverSocket = new ServerSocket(0);
			this.thread = new Thread(() -> {
				try (Socket socket = serverSocket.accept()) {
					BufferedReader reader = new BufferedReader(
						new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
					String line = reader.readLine();
					int contentLength = 0;
					while (line != null && !line.isEmpty()) {
						if (line.toLowerCase().startsWith("content-length:")) {
							contentLength = Integer.parseInt(line.substring(15).trim());
						}
						line = reader.readLine();
					}
					char[] buf = new char[Math.max(0, contentLength)];
					int read = 0;
					while (read < contentLength) {
						int n = reader.read(buf, read, contentLength - read);
						if (n < 0) {
							break;
						}
						read += n;
					}
					body.set(new String(buf, 0, read));
					if (delayMs > 0) {
						Thread.sleep(delayMs);
					}
					byte[] response = ("HTTP/1.1 " + status + " OK\r\nContent-Length: 0\r\n\r\n")
						.getBytes(StandardCharsets.UTF_8);
					try (OutputStream out = socket.getOutputStream()) {
						out.write(response);
						out.flush();
					}
				} catch (Exception ignore) {
					// 服务端线程结束
				} finally {
					received.countDown();
				}
			}, "tiny-http-server");
			this.thread.setDaemon(true);
			this.thread.start();
		}

		String url() {
			return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/ingest";
		}

		CountDownLatch received() {
			return received;
		}

		String body() {
			return body.get();
		}

		@Override
		public void close() throws Exception {
			serverSocket.close();
		}
	}
}
