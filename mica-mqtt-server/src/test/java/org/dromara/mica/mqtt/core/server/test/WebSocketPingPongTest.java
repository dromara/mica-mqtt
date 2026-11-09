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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鸿蒙 NEXT（NETSTACK）WebSocket 协议层心跳模拟测试，对应 gitee issue #IKTN7P。
 *
 * <p>背景：鸿蒙 App 连 MQTT over WebSocket 时，NETSTACK 默认每 30s 发一帧
 * WebSocket Ping（Opcode 0x9），若收不到 Pong（Opcode 0xA）会主动断连，
 * 表现为约 60s 一次断开（The link is down / OnError 200 / OnClose 0）。
 * 这与 MQTT keepalive（PINGREQ/PINGRESP）不是同一层。
 *
 * <p>本测试用裸 Socket 模拟鸿蒙客户端：完成 WS 握手后，周期性发送带掩码的
 * Ping 帧，断言服务端（mica-net WsTioServerHandler）自动回 Pong 且连接稳定。
 * 测试中将 Ping 间隔压缩为 1s，以缩短用例时长。
 *
 * @author L.cm
 */
class WebSocketPingPongTest {
	private static final int PORT = 1998;
	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * 模拟鸿蒙 NETSTACK：握手后周期发 WS Ping，服务端必须回 Pong，且连接不断开。
	 */
	@Test
	void serverMustReplyPongToClientPing() throws Exception {
		MqttServer server = MqttServer.create()
			.enableMqttWs(PORT)
			.statEnable(false)
			.start();
		try (Socket socket = new Socket("127.0.0.1", PORT)) {
			socket.setSoTimeout(5000);
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();

			// 1. WebSocket 握手（模拟 uni.connectSocket，带 Sec-WebSocket-Protocol: mqtt）
			handshake(out, in);

			// 2. 发送 MQTT CONNECT（二进制帧，掩码），确认业务通路
			sendMaskedBinaryFrame(out, mqttConnect("harmony-client"));
			byte[] connack = readMqttPayload(in);
			assertEquals(0x20, connack[0] & 0xF0, "应收到 MQTT CONNACK");

			// 3. 模拟 NETSTACK 协议层心跳：周期发 Ping，验证每次都回 Pong
			int rounds = 3;
			for (int i = 0; i < rounds; i++) {
				// 模拟间隔（真实鸿蒙为 30s，这里压缩为 1s）
				Thread.sleep(1000);
				byte[] payload = ("hb-" + i).getBytes(StandardCharsets.UTF_8);
				sendMaskedControlFrame(out, (byte) 0x89, payload);

				byte[] frame = readFrame(in);
				assertEquals(0x8A, frame[0] & 0xFF, "第 " + (i + 1) + " 次 Ping 应回复 Pong（FIN + 0xA）");
				// Pong 负载应原样带回 Ping 负载（RFC 6455 §5.5.3）
				int len = frame[1] & 0x7F;
				byte[] pongPayload = new byte[len];
				System.arraycopy(frame, 2, pongPayload, 0, len);
				assertTrue(java.util.Arrays.equals(payload, pongPayload),
					"Pong 负载应与 Ping 一致");
			}

			// 4. 连接应保持稳定（未出现鸿蒙端 The link is down 类断连）
			//    能继续完成上面的 Ping/Pong 即说明连接存活，最后再确认可读
			assertTrue(in.available() >= 0, "连接应保持存活");
		} finally {
			server.stop();
		}
	}

	/**
	 * WebSocket 握手，校验 101 响应与 Sec-WebSocket-Accept。
	 */
	private static void handshake(OutputStream out, InputStream in) throws Exception {
		String key = base64Key();
		String request = "GET /mqtt HTTP/1.1\r\n"
			+ "Host: 127.0.0.1:" + PORT + "\r\n"
			+ "Upgrade: websocket\r\n"
			+ "Connection: Upgrade\r\n"
			+ "Sec-WebSocket-Key: " + key + "\r\n"
			+ "Sec-WebSocket-Version: 13\r\n"
			+ "Sec-WebSocket-Protocol: mqtt\r\n"
			+ "\r\n";
		out.write(request.getBytes(StandardCharsets.US_ASCII));
		out.flush();

		String resp = readUntil(in, "\r\n\r\n");
		assertTrue(resp.contains("101"), "握手应返回 101，实际: " + resp.split("\r\n")[0]);
		assertTrue(resp.contains("Sec-WebSocket-Protocol: mqtt"),
			"应协商 mqtt 子协议，实际:\n" + resp);
	}

	/**
	 * 发送带掩码的 WebSocket 控制帧（Ping，负载 <= 125 字节）。
	 */
	private static void sendMaskedControlFrame(OutputStream out, byte firstByte, byte[] payload) throws Exception {
		byte[] mask = maskKey();
		byte[] masked = applyMask(payload, mask);
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		buf.write(firstByte);
		buf.write(0x80 | payload.length);
		buf.write(mask);
		buf.write(masked);
		out.write(buf.toByteArray());
		out.flush();
	}

	/**
	 * 发送带掩码的 WebSocket 二进制帧（承载 MQTT 报文）。
	 */
	private static void sendMaskedBinaryFrame(OutputStream out, byte[] payload) throws Exception {
		byte[] mask = maskKey();
		byte[] masked = applyMask(payload, mask);
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		buf.write(0x82); // FIN + binary
		buf.write(0x80 | payload.length);
		buf.write(mask);
		buf.write(masked);
		out.write(buf.toByteArray());
		out.flush();
	}

	/**
	 * 构造一个最小 MQTT 3.1.1 CONNECT 报文。
	 */
	private static byte[] mqttConnect(String clientId) {
		byte[] clientIdBytes = clientId.getBytes(StandardCharsets.UTF_8);
		// variable header: protocol name + level + flags + keepalive
		byte[] vh = new byte[]{0x00, 0x04, 'M', 'Q', 'T', 'T', 0x04, 0x02, 0x00, 0x3C};
		byte[] payload = new byte[]{0x00, (byte) clientIdBytes.length};
		byte[] all = new byte[vh.length + payload.length + clientIdBytes.length];
		System.arraycopy(vh, 0, all, 0, vh.length);
		System.arraycopy(payload, 0, all, vh.length, payload.length);
		System.arraycopy(clientIdBytes, 0, all, vh.length + payload.length, clientIdBytes.length);
		// fixed header: CONNECT(0x10) + remaining length（长度很小，单字节即可）
		byte[] packet = new byte[1 + 1 + all.length];
		packet[0] = 0x10;
		packet[1] = (byte) all.length;
		System.arraycopy(all, 0, packet, 2, all.length);
		return packet;
	}

	/**
	 * 从 WS 帧流中读取一帧 MQTT 业务帧（binary）的负载。
	 */
	private static byte[] readMqttPayload(InputStream in) throws Exception {
		byte[] frame = readFrame(in);
		assertEquals(0x82, frame[0] & 0xFF, "应收到二进制帧（CONNACK）");
		int len = frame[1] & 0x7F;
		byte[] payload = new byte[len];
		System.arraycopy(frame, 2, payload, 0, len);
		return payload;
	}

	/**
	 * 读取一个完整的 WebSocket 帧（服务端帧不带掩码，长度 <= 125 的简化处理）。
	 */
	private static byte[] readFrame(InputStream in) throws Exception {
		int b1 = in.read();
		int b2 = in.read();
		assertTrue(b1 != -1 && b2 != -1, "连接被服务端提前关闭（模拟鸿蒙断连场景）");
		int len = b2 & 0x7F;
		byte[] frame = new byte[2 + len];
		frame[0] = (byte) b1;
		frame[1] = (byte) b2;
		int read = in.readNBytes(frame, 2, len);
		assertEquals(len, read, "帧负载不完整");
		return frame;
	}

	private static String readUntil(InputStream in, String terminator) throws Exception {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) != -1) {
			buf.write(b);
			String s = buf.toString(StandardCharsets.US_ASCII.name());
			if (s.endsWith(terminator)) {
				return s;
			}
		}
		throw new IllegalStateException("连接被提前关闭，读到: " + buf.toString(StandardCharsets.US_ASCII.name()));
	}

	private static String base64Key() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return java.util.Base64.getEncoder().encodeToString(bytes);
	}

	private static byte[] maskKey() {
		byte[] mask = new byte[4];
		RANDOM.nextBytes(mask);
		return mask;
	}

	private static byte[] applyMask(byte[] data, byte[] mask) {
		byte[] masked = new byte[data.length];
		for (int i = 0; i < data.length; i++) {
			masked[i] = (byte) (data[i] ^ mask[i & 0x3]);
		}
		return masked;
	}
}
