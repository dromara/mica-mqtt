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

package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.core.server.MqttServer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 明文 MQTT 端口收到非 MQTT 流量时的服务端行为。
 *
 * <p>背景：客户端把 TLS ClientHello（首字节 0x16）或 HTTP 请求（首字节 'G' = 0x47）发到了明文 MQTT 端口，
 * 旧实现会抛出 {@code IllegalArgumentException: invalid QoS: 3}，日志既误导又伴随裸堆栈。
 * 修复后应快速拒绝连接，且不能影响正常 MQTT 客户端。
 *
 * @author L.cm
 */
class NonMqttTrafficTest {
	private static final int PORT = 1997;

	@Test
	void nonMqttTrafficIsRejectedAndLegitClientStillWorks() throws Exception {
		MqttServer server = MqttServer.create()
			.enableMqtt(PORT)
			.statEnable(false)
			.debug()
			.start();
		try {
			// 1. TLS ClientHello
			assertConnectionClosed(new byte[]{0x16, 0x03, 0x01, 0x00, 0x2a, 0x01, 0x00, 0x00, 0x00});
			// 2. HTTP GET
			assertConnectionClosed("GET /mqtt HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			// 3. 正常 MQTT 客户端不受影响
			byte[] connect = new byte[]{0x10, 0x0E, 0x00, 0x04, 'M', 'Q', 'T', 'T',
				0x04, 0x02, 0x00, 0x3c, 0x00, 0x02, 'a', 'b'};
			try (Socket socket = new Socket("127.0.0.1", PORT)) {
				OutputStream out = socket.getOutputStream();
				out.write(connect);
				out.flush();
				// 服务端应返回 CONNACK（0x20）
				InputStream in = socket.getInputStream();
				int b1 = in.read();
				assertEquals(0x20, b1, "正常 CONNECT 应收到 CONNACK");
			}
		} finally {
			server.stop();
		}
	}

	/**
	 * 发送非 MQTT 字节后，服务端应关闭连接（读到 -1 或抛 IOException）
	 */
	private static void assertConnectionClosed(byte[] bytes) throws Exception {
		try (Socket socket = new Socket("127.0.0.1", PORT)) {
			socket.setSoTimeout(5000);
			OutputStream out = socket.getOutputStream();
			out.write(bytes);
			out.flush();
			InputStream in = socket.getInputStream();
			int read = in.read();
			// -1 表示服务端已关闭；抛 IOException 同样表示连接被断开
			assertTrue(read == -1, "服务端应关闭非 MQTT 连接，但读到: " + read);
		} catch (java.io.IOException e) {
			// 连接被重置也算断开
		}
	}
}
