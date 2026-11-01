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

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.server.ServerChannelContext;
import net.dreamlu.mica.net.server.TioServerConfig;
import org.dromara.mica.mqtt.codec.MqttEncoder;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttConnectBuilder;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 互踢场景的报文级测试，直接用裸 Socket 校验 broker 下发的 DISCONNECT 报文。
 *
 * @author L.cm
 */
class MqttSessionTakenOverWireTest {
	private static final int PORT = 1998;
	private static MqttServer server;

	@BeforeAll
	static void setUpAll() {
		server = MqttServer.create()
			.enableMqtt(PORT)
			.statEnable(false)
			.start();
	}

	@AfterAll
	static void tearDownAll() {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void kickedClientShouldReceiveDisconnectWithSessionTakenOver() throws Exception {
		try (Socket first = new Socket("127.0.0.1", PORT);
			 Socket second = new Socket("127.0.0.1", PORT)) {
			first.setSoTimeout(10_000);
			second.setSoTimeout(10_000);

			sendConnect(first, "dupClientId");
			// CONNACK: 固定头 2 字节 + 变长头 2 字节 + 属性长度 1 字节
			byte[] connAck = readMqttMessage(first);
			assertEquals((byte) 0x20, connAck[0], "应收到 CONNACK");

			sendConnect(second, "dupClientId");
			byte[] connAck2 = readMqttMessage(second);
			assertEquals((byte) 0x20, connAck2[0], "第二个连接应收到 CONNACK");

			// 第一个连接应收到 DISCONNECT(0x8E Session taken over)
			// DISCONNECT：固定头 1 字节 + remainingLength 1 字节 + reasonCode 1 字节 + 属性长度 1 字节
			byte[] received = readAtLeast(first, 4);
			assertEquals(4, received.length, "应收到 4 字节的 DISCONNECT 报文");
			assertEquals((byte) 0xE0, received[0], "DISCONNECT 固定头");
			assertEquals((byte) 0x02, received[1], "DISCONNECT remaining length");
			assertEquals((byte) 0x8E, received[2], "DISCONNECT reason code 应为 Session taken over");
			assertEquals((byte) 0x00, received[3], "DISCONNECT 属性长度应为 0");
		}
	}

	private void sendConnect(Socket socket, String clientId) throws Exception {
		OutputStream out = socket.getOutputStream();
		out.write(encodeConnect(clientId));
		out.flush();
	}

	private byte[] encodeConnect(String clientId) {
		MqttMessage connect = new MqttConnectBuilder()
			.protocolVersion(MqttVersion.MQTT_5)
			.clientId(clientId)
			.cleanStart(true)
			.keepAlive(60)
			.build();
		ChannelContext context = new ServerChannelContext(
			new TioServerConfig("wire-test", null, null), "wire-test-" + clientId);
		ByteBuffer buffer = MqttEncoder.INSTANCE.doEncode(context, connect);
		// encoder 写入后未 flip，这里手动切换到读模式
		buffer.flip();
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}

	/**
	 * 读取一个完整的 MQTT 报文：1 字节固定头 + 变长 remainingLength + 报文体
	 */
	private byte[] readMqttMessage(Socket socket) throws Exception {
		InputStream in = socket.getInputStream();
		int first = in.read();
		if (first < 0) {
			throw new IllegalStateException("连接已关闭，未读到报文");
		}
		int multiplier = 1;
		int remaining = 0;
		int digit;
		int loops = 0;
		do {
			digit = in.read();
			if (digit < 0) {
				throw new IllegalStateException("连接已关闭，未读到 remaining length");
			}
			remaining += (digit & 127) * multiplier;
			multiplier *= 128;
			loops++;
		} while ((digit & 128) != 0 && loops < 4);
		byte[] body = readFully(socket, remaining);
		byte[] message = new byte[1 + loops + remaining];
		message[0] = (byte) first;
		message[1] = (byte) digit;
		System.arraycopy(body, 0, message, 2 + loops - 1, remaining);
		return message;
	}

	private byte[] readFully(Socket socket, int length) throws Exception {
		InputStream in = socket.getInputStream();
		byte[] buf = new byte[length];
		int read = 0;
		while (read < length) {
			int n = in.read(buf, read, length - read);
			if (n < 0) {
				break;
			}
			read += n;
		}
		return buf;
	}

	private byte[] readAtLeast(Socket socket, int length) throws Exception {
		InputStream in = socket.getInputStream();
		byte[] buf = new byte[length];
		int read = 0;
		while (read < length) {
			int n = in.read(buf, read, length - read);
			if (n < 0) {
				break;
			}
			read += n;
		}
		return buf;
	}
}
