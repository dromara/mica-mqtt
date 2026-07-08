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

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用一个极简 fake broker 覆盖服务端重启和异常握手边界。
 *
 * @author L.cm
 */
class MqttClientReconnectChaosTest {

	@Test
	void shouldRemainDisconnectedWhenBrokerNeverSendsConnAck() throws Exception {
		CountDownLatch connectReadLatch = new CountDownLatch(1);
		CountingConnectListener listener = new CountingConnectListener();
		MqttClient client = null;
		try (FakeBroker broker = FakeBroker.create()
			.next(new HoldAfterConnectHandler(connectReadLatch))) {
			client = newClient(broker.getPort(), listener);

			Assertions.assertTrue(connectReadLatch.await(3, TimeUnit.SECONDS), "broker should receive MQTT CONNECT");
			Assertions.assertFalse(listener.awaitConnected(600), "client must not report connected without CONNACK");
			Assertions.assertTrue(client.isDisconnected(), "client should stay MQTT-disconnected without CONNACK");
			Assertions.assertEquals(1, broker.getConnectPackets());
		} finally {
			stop(client);
		}
	}

	@Test
	void shouldReconnectAfterBrokerResetsAcceptedConnection() throws Exception {
		CountingConnectListener listener = new CountingConnectListener();
		MqttClient client = null;
		try (FakeBroker broker = FakeBroker.create()
			.next(new ConnAckThenResetHandler(100))
			.next(new ConnAckAndHoldHandler())) {
			client = newClient(broker.getPort(), listener);

			Assertions.assertTrue(listener.awaitConnected(3_000), "first MQTT connection should succeed");
			Assertions.assertTrue(listener.awaitDisconnected(3_000), "RST should trigger disconnect");
			Assertions.assertTrue(listener.awaitConnectedCount(2, 5_000), "client should reconnect and receive CONNACK again");
			Assertions.assertTrue(client.isConnected(), "client should be accepted after the second CONNACK");
			Assertions.assertTrue(broker.getConnectPackets() >= 2, "broker should receive CONNECT for reconnect");
		} finally {
			stop(client);
		}
	}

	@Test
	void shouldExposeReconnectHandshakeStallWhenBrokerReadsConnectButDoesNotAck() throws Exception {
		CountDownLatch secondConnectReadLatch = new CountDownLatch(1);
		CountingConnectListener listener = new CountingConnectListener();
		MqttClient client = null;
		try (FakeBroker broker = FakeBroker.create()
			.next(new ConnAckThenResetHandler(100))
			.next(new HoldAfterConnectHandler(secondConnectReadLatch))) {
			client = newClient(broker.getPort(), listener);

			Assertions.assertTrue(listener.awaitConnected(3_000), "first MQTT connection should succeed");
			Assertions.assertTrue(listener.awaitDisconnected(3_000), "RST should trigger disconnect");
			Assertions.assertTrue(secondConnectReadLatch.await(5, TimeUnit.SECONDS), "broker should receive reconnect CONNECT");
			Assertions.assertFalse(listener.awaitConnectedCount(2, 800), "client must not report reconnect success without CONNACK");
			Assertions.assertTrue(client.isDisconnected(), "client should remain MQTT-disconnected while reconnect CONNACK is missing");
		} finally {
			stop(client);
		}
	}

	@Test
	void shouldPublishSuccessfullyAfterBrokerRestartReconnect() throws Exception {
		CountingConnectListener listener = new CountingConnectListener();
		MqttClient client = null;
		// 1. reserve a free port and start the first broker.
		int port = reserveFreePort();
		FakeBroker firstBroker = FakeBroker.createOnPort(port)
			.next(new ConnAckAndHoldHandler());
		firstBroker.start();
		try {
			client = newClient(port, listener);

			Assertions.assertTrue(listener.awaitConnected(3_000), "first MQTT connection should succeed");
			Assertions.assertTrue(client.isConnected(), "client should be accepted after first CONNACK");
			Assertions.assertTrue(client.publish("/test/initial", "hello".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0), "publish before broker restart should succeed");
			int disconnectedAtStart = listener.disconnectedCount.get();

			// 2. simulate EMQX restart: stop the first broker (server socket closed, sockets reset)
			//    and wait for the client to enter the reconnecting state.
			firstBroker.close();
			Assertions.assertTrue(listener.awaitDisconnectedCount(disconnectedAtStart + 1, 3_000), "broker close should trigger disconnect");
			Assertions.assertTrue(client.isDisconnected(), "client should be MQTT-disconnected after broker stops");

			// 3. start a new broker on the same port and verify the client reconnects.
			FakeBroker secondBroker = FakeBroker.createOnPort(port)
				.next(new ConnAckAndHoldHandler());
			secondBroker.start();
			try {
				Assertions.assertTrue(listener.awaitConnectedCount(2, 10_000), "client should reconnect after broker restart");
				Assertions.assertTrue(client.isConnected(), "client should be accepted after restart CONNACK");
				// The key assertion: publish after broker restart must succeed.
				Assertions.assertTrue(client.publish("/test/after-restart", "world".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0), "publish after broker restart should succeed");
			} finally {
				secondBroker.close();
			}
		} finally {
			stop(client);
		}
	}

	private static int reserveFreePort() throws IOException {
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			return serverSocket.getLocalPort();
		}
	}

	private static MqttClient newClient(int port, IMqttClientConnectListener listener) {
		return MqttClient.create()
			.name("Mqtt-Reconnect-Chaos-Test")
			.ip("127.0.0.1")
			.port(port)
			.timeout(1)
			.reInterval(100)
			.keepAliveSecs(1)
			.clientId("chaos-test-" + System.nanoTime())
			.connectListener(listener)
			.pendingPublishQueueEnabled()
			.connectSync();
	}

	private static void stop(MqttClient client) {
		if (client != null) {
			client.stop();
		}
	}

	private interface SocketHandler {
		void handle(Socket socket) throws Exception;
	}

	private static final class FakeBroker implements AutoCloseable {
		private final ServerSocket serverSocket;
		private final Queue<SocketHandler> handlers = new ArrayDeque<>();
		private final Queue<Socket> activeSockets = new ArrayDeque<>();
		private final CountDownLatch stoppedLatch = new CountDownLatch(1);
		private final AtomicInteger connectPackets = new AtomicInteger();
		private volatile boolean running = true;
		private volatile boolean started = false;
		private Thread acceptThread;

		private FakeBroker(ServerSocket serverSocket) {
			this.serverSocket = serverSocket;
		}

		static FakeBroker create() throws IOException {
			FakeBroker broker = new FakeBroker(new ServerSocket(0));
			broker.start();
			return broker;
		}

		static FakeBroker createOnPort(int port) throws IOException {
			return new FakeBroker(new ServerSocket(port));
		}

		FakeBroker next(SocketHandler handler) {
			this.handlers.add(handler);
			return this;
		}

		int getPort() {
			return serverSocket.getLocalPort();
		}

		int getConnectPackets() {
			return connectPackets.get();
		}

		private void start() {
			if (started) {
				return;
			}
			synchronized (this) {
				if (started) {
					return;
				}
				started = true;
			}
			this.acceptThread = new Thread(() -> {
				try {
					while (running) {
						Socket socket = serverSocket.accept();
						activeSockets.add(socket);
						SocketHandler handler = handlers.poll();
						if (handler == null) {
							handler = new ConnAckAndHoldHandler();
						}
						SocketHandler finalHandler = handler;
						Thread worker = new Thread(() -> {
							try {
								readMqttPacket(socket.getInputStream());
								connectPackets.incrementAndGet();
								finalHandler.handle(socket);
							} catch (SocketException e) {
								if (running) {
									e.printStackTrace();
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}, "fake-mqtt-broker-worker");
						worker.setDaemon(true);
						worker.start();
					}
				} catch (SocketException e) {
					if (running) {
						e.printStackTrace();
					}
				} catch (IOException e) {
					if (running) {
						e.printStackTrace();
					}
				} finally {
					stoppedLatch.countDown();
				}
			}, "fake-mqtt-broker-accept");
			this.acceptThread.setDaemon(true);
			this.acceptThread.start();
		}

		@Override
		public void close() throws Exception {
			running = false;
			try {
				serverSocket.close();
			} catch (IOException ignore) {
				// already closed
			}
			// Drop existing sockets with a TCP RST so the client sees the disconnect immediately
			// instead of waiting for the next keep-alive / read timeout.
			for (Socket socket : activeSockets) {
				try {
					socket.setSoLinger(true, 0);
				} catch (Exception ignore) {
					// ignore
				}
				try {
					socket.close();
				} catch (IOException ignore) {
					// ignore
				}
			}
			activeSockets.clear();
			stoppedLatch.await(1, TimeUnit.SECONDS);
		}
	}

	private static final class HoldAfterConnectHandler implements SocketHandler {
		private final CountDownLatch connectReadLatch;

		private HoldAfterConnectHandler(CountDownLatch connectReadLatch) {
			this.connectReadLatch = connectReadLatch;
		}

		@Override
		public void handle(Socket socket) throws Exception {
			connectReadLatch.countDown();
			Thread.sleep(TimeUnit.SECONDS.toMillis(30));
		}
	}

	private static final class ConnAckAndHoldHandler implements SocketHandler {
		@Override
		public void handle(Socket socket) throws Exception {
			writeConnAck(socket.getOutputStream());
			Thread.sleep(TimeUnit.SECONDS.toMillis(30));
		}
	}

	private static final class ConnAckThenResetHandler implements SocketHandler {
		private final long resetDelayMs;

		private ConnAckThenResetHandler(long resetDelayMs) {
			this.resetDelayMs = resetDelayMs;
		}

		@Override
		public void handle(Socket socket) throws Exception {
			writeConnAck(socket.getOutputStream());
			Thread.sleep(resetDelayMs);
			socket.setSoLinger(true, 0);
			socket.close();
		}
	}

	private static void writeConnAck(OutputStream outputStream) throws IOException {
		outputStream.write(new byte[]{0x20, 0x03, 0x00, 0x00, 0x00});
		outputStream.flush();
	}

	private static void readMqttPacket(InputStream inputStream) throws IOException {
		int firstByte = inputStream.read();
		if (firstByte == -1) {
			throw new EOFException("No MQTT packet received.");
		}
		int multiplier = 1;
		int remainingLength = 0;
		int encodedByte;
		do {
			encodedByte = inputStream.read();
			if (encodedByte == -1) {
				throw new EOFException("Incomplete MQTT remaining length.");
			}
			remainingLength += (encodedByte & 127) * multiplier;
			multiplier *= 128;
		} while ((encodedByte & 128) != 0);
		byte[] body = new byte[remainingLength];
		int offset = 0;
		while (offset < remainingLength) {
			int read = inputStream.read(body, offset, remainingLength - offset);
			if (read == -1) {
				throw new EOFException("Incomplete MQTT packet body.");
			}
			offset += read;
		}
	}

	private static final class CountingConnectListener implements IMqttClientConnectListener {
		private final AtomicInteger connectedCount = new AtomicInteger();
		private final AtomicInteger disconnectedCount = new AtomicInteger();
		private volatile CountDownLatch connectedLatch = new CountDownLatch(1);
		private volatile CountDownLatch disconnectedLatch = new CountDownLatch(1);

		@Override
		public void onConnected(ChannelContext context, boolean isReconnect) {
			connectedCount.incrementAndGet();
			connectedLatch.countDown();
		}

		@Override
		public void onDisconnect(ChannelContext context, Throwable throwable, String remark, boolean isRemove) {
			disconnectedCount.incrementAndGet();
			disconnectedLatch.countDown();
		}

		boolean awaitConnected(long timeoutMs) throws InterruptedException {
			return connectedLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
		}

		boolean awaitDisconnected(long timeoutMs) throws InterruptedException {
			CountDownLatch latch = this.disconnectedLatch;
			return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
		}

		boolean awaitConnectedCount(int expected, long timeoutMs) throws InterruptedException {
			long deadline = System.currentTimeMillis() + timeoutMs;
			while (System.currentTimeMillis() < deadline) {
				if (connectedCount.get() >= expected) {
					return true;
				}
				Thread.sleep(20);
			}
			return connectedCount.get() >= expected;
		}

		boolean awaitDisconnectedCount(int expected, long timeoutMs) throws InterruptedException {
			long deadline = System.currentTimeMillis() + timeoutMs;
			while (System.currentTimeMillis() < deadline) {
				if (disconnectedCount.get() >= expected) {
					return true;
				}
				Thread.sleep(20);
			}
			return disconnectedCount.get() >= expected;
		}
	}
}
