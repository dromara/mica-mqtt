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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttInflightReconnectTest {
	@Test
	void qos1重连后重发未确认消息() throws Exception {
		verifyReconnect(MqttQoS.QOS1, false);
	}

	@Test
	void qos2会话恢复后继续发送PubRel() throws Exception {
		verifyReconnect(MqttQoS.QOS2, true);
	}

	@Test
	void qos2会话丢失后重新发送Publish() throws Exception {
		verifyReconnect(MqttQoS.QOS2, false);
	}

	private void verifyReconnect(MqttQoS qos, boolean sessionPresent) throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		MqttClient client = null;
		DefaultMqttClientSession session = new DefaultMqttClientSession();
		CountDownLatch finish = new CountDownLatch(1);
		try (ServerSocket server = new ServerSocket(0)) {
			server.setSoTimeout(8000);
			CountDownLatch connected = new CountDownLatch(1);
			Future<int[]> result = executor.submit(() -> {
				int packetId;
				try (Socket first = server.accept()) {
					first.setSoTimeout(8000);
					readPacket(first.getInputStream());
					write(first, 0x20, 2, 0, 0);
					connected.countDown();
					byte[] publish = readPacket(first.getInputStream());
					int topicLength = ((publish[1] & 0xff) << 8) | (publish[2] & 0xff);
					int offset = 3 + topicLength;
					packetId = ((publish[offset] & 0xff) << 8) | (publish[offset + 1] & 0xff);
					if (qos == MqttQoS.QOS2) {
						write(first, 0x50, 2, packetId >> 8, packetId);
						assertEquals(0x62, readPacket(first.getInputStream())[0] & 0xff);
					}
				}
				try (Socket second = server.accept()) {
					second.setSoTimeout(8000);
					readPacket(second.getInputStream());
					write(second, 0x20, 2, sessionPresent ? 1 : 0, 0);
					byte[] retried = readPacket(second.getInputStream());
					int retryId;
					if (qos == MqttQoS.QOS2 && sessionPresent) {
						retryId = ((retried[1] & 0xff) << 8) | (retried[2] & 0xff);
						write(second, 0x70, 2, retryId >> 8, retryId);
					} else {
						int len = ((retried[1] & 0xff) << 8) | (retried[2] & 0xff);
						int idx = 3 + len;
						retryId = ((retried[idx] & 0xff) << 8) | (retried[idx + 1] & 0xff);
						write(second, qos == MqttQoS.QOS2 ? 0x50 : 0x40, 2, retryId >> 8, retryId);
						if (qos == MqttQoS.QOS2) {
							assertEquals(0x62, readPacket(second.getInputStream())[0] & 0xff);
							write(second, 0x70, 2, retryId >> 8, retryId);
						}
					}
					long ackDeadline = System.currentTimeMillis() + 5000;
					while (session.getPendingPublish(packetId) != null && System.currentTimeMillis() < ackDeadline) {
						Thread.sleep(10);
					}
					assertTrue(session.getPendingPublishes().isEmpty());
					finish.await(5, TimeUnit.SECONDS);
					return new int[]{packetId, retryId, retried[0] & 0xff};
				}
			});
			client = MqttClient.create().ip("127.0.0.1").port(server.getLocalPort())
				.clientId("inflight-test").version(MqttVersion.MQTT_3_1_1).cleanStart(false).clientSession(session)
				.reInterval(100).connectSync();
			assertTrue(connected.await(5, TimeUnit.SECONDS));
			long deadline = System.currentTimeMillis() + 5000;
			while (!client.isConnected() && System.currentTimeMillis() < deadline) {
				Thread.sleep(10);
			}
			assertTrue(client.publish("test", new byte[]{1}, qos));
			finish.countDown();
			int[] values = result.get(12, TimeUnit.SECONDS);
			assertEquals(values[0], values[1]);
			assertEquals(qos == MqttQoS.QOS2 ? (sessionPresent ? 0x62 : 0x3c) : 0x3a, values[2]);
		} finally {
			finish.countDown();
			if (client != null) {
				client.stop();
			}
			executor.shutdownNow();
		}
	}

	private static byte[] readPacket(InputStream input) throws Exception {
		DataInputStream stream = new DataInputStream(input);
		int header = stream.read();
		if (header < 0) {
			throw new EOFException();
		}
		int remaining = 0;
		int multiplier = 1;
		int digit;
		do {
			digit = stream.readUnsignedByte();
			remaining += (digit & 127) * multiplier;
			multiplier *= 128;
		} while ((digit & 128) != 0);
		byte[] packet = new byte[remaining + 1];
		packet[0] = (byte) header;
		stream.readFully(packet, 1, remaining);
		return packet;
	}

	private static void write(Socket socket, int... bytes) throws Exception {
		OutputStream output = socket.getOutputStream();
		for (int value : bytes) {
			output.write(value);
		}
		output.flush();
	}
}
