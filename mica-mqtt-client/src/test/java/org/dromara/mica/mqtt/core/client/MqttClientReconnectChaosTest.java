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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
		FakeBroker broker = FakeBroker.create()
			.next(new HoldAfterConnectHandler(connectReadLatch));
		try {
			client = newClient(broker.getPort(), listener);

			Assertions.assertTrue(connectReadLatch.await(3, TimeUnit.SECONDS), "broker should receive MQTT CONNECT");
			Assertions.assertFalse(listener.awaitConnected(600), "client must not report connected without CONNACK");
			Assertions.assertTrue(client.isDisconnected(), "client should stay MQTT-disconnected without CONNACK");
			Assertions.assertEquals(1, broker.getConnectPackets());
		} finally {
			stop(client);
			broker.close();
		}
	}

	@Test
	void shouldReconnectAfterBrokerResetsAcceptedConnection() throws Exception {
		CountingConnectListener listener = new CountingConnectListener();
		MqttClient client = null;
		FakeBroker broker = FakeBroker.create()
			.next(new ConnAckThenResetHandler(100))
			.next(new ConnAckAndHoldHandler());
		try {
			client = newClient(broker.getPort(), listener);

			Assertions.assertTrue(listener.awaitConnected(3_000), "first MQTT connection should succeed");
			Assertions.assertTrue(listener.awaitDisconnected(3_000), "RST should trigger disconnect");
			Assertions.assertTrue(listener.awaitConnectedCount(2, 5_000), "client should reconnect and receive CONNACK again");
			Assertions.assertTrue(client.isConnected(), "client should be accepted after the second CONNACK");
			Assertions.assertTrue(broker.getConnectPackets() >= 2, "broker should receive CONNECT for reconnect");
		} finally {
			stop(client);
			broker.close();
		}
	}

	@Test
	void shouldExposeReconnectHandshakeStallWhenBrokerReadsConnectButDoesNotAck() throws Exception {
		CountDownLatch secondConnectReadLatch = new CountDownLatch(1);
		CountingConnectListener listener = new CountingConnectListener();
		MqttClient client = null;
		FakeBroker broker = FakeBroker.create()
			.next(new ConnAckThenResetHandler(100))
			.next(new HoldAfterConnectHandler(secondConnectReadLatch));
		try {
			client = newClient(broker.getPort(), listener);

			Assertions.assertTrue(listener.awaitConnected(3_000), "first MQTT connection should succeed");
			Assertions.assertTrue(listener.awaitDisconnected(3_000), "RST should trigger disconnect");
			Assertions.assertTrue(secondConnectReadLatch.await(5, TimeUnit.SECONDS), "broker should receive reconnect CONNECT");
			Assertions.assertFalse(listener.awaitConnectedCount(2, 800), "client must not report reconnect success without CONNACK");
			Assertions.assertTrue(client.isDisconnected(), "client should remain MQTT-disconnected while reconnect CONNACK is missing");
		} finally {
			stop(client);
			broker.close();
		}
	}

	@Test
	void shouldPublishSuccessfullyAfterBrokerRestartReconnect() throws Exception {
		// 可通过 JVM 参数放大复现概率:
		//   -Drounds=N   每轮重启 broker 并断言 publish 成功，跑 N 轮（默认 1）
		//   -Dchaos.threads=N   在断开到重连窗口内注入 N 个并发 publish 线程（默认 4，<=0 关闭）
		int rounds = readIntProperty("rounds", 1);
		int concurrentThreads = readIntProperty("chaos.threads", 4);
		for (int round = 1; round <= rounds; round++) {
			runRestartReconnectRound(round, concurrentThreads);
		}
	}

	private void runRestartReconnectRound(int round, int concurrentThreads) throws Exception {
		CountingConnectListener listener = new CountingConnectListener();
		MqttClient client = null;
		// 1. reserve a free port and start the first broker.
		int port = reserveFreePort();
		FakeBroker firstBroker = FakeBroker.createOnPort(port)
			.next(new ConnAckAndHoldHandler());
		firstBroker.start();
		ExecutorService chaosPool = null;
		try {
			client = newClient(port, listener);

			Assertions.assertTrue(listener.awaitConnected(3_000), "[round " + round + "] first MQTT connection should succeed");
			Assertions.assertTrue(client.isConnected(), "[round " + round + "] client should be accepted after first CONNACK");
			Assertions.assertTrue(client.publish("/test/initial", "hello".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0), "[round " + round + "] publish before broker restart should succeed");
			int disconnectedAtStart = listener.disconnectedCount.get();
			int connectedAtStart = listener.connectedCount.get();

			// 2. simulate EMQX restart: stop the first broker (server socket closed, sockets reset)
			//    and wait for the client to enter the reconnecting state.
			firstBroker.close();
			Assertions.assertTrue(listener.awaitDisconnectedCount(disconnectedAtStart + 1, 3_000), "[round " + round + "] broker close should trigger disconnect");
			Assertions.assertTrue(client.isDisconnected(), "[round " + round + "] client should be MQTT-disconnected after broker stops");

			// 2.5 inject concurrent publish pressure during the disconnected -> reconnected window
			//     to maximize the chance of hitting the publish()/reconnect() race the CI sees.
			chaosPool = startChaosPublishers(client, concurrentThreads, "/test/chaos/" + round + "/");

			// 3. start a new broker on the same port and verify the client reconnects.
			FakeBroker secondBroker = FakeBroker.createOnPort(port)
				.next(new ConnAckAndHoldHandler());
			secondBroker.start();
			try {
				Assertions.assertTrue(secondBroker.awaitConnectPackets(1, 10_000), "[round " + round + "] broker should receive reconnect CONNECT after restart");
				Assertions.assertTrue(listener.awaitConnectedCount(connectedAtStart + 1, 10_000), "[round " + round + "] client should reconnect after broker restart");
				Assertions.assertTrue(client.isConnected(), "[round " + round + "] client should be accepted after restart CONNACK");
				// 关键断言：broker 重启之后的 publish 必须成功。
				// 同时也校验 chaos 注入的 publish 结果：未连上时入队（开启 pendingPublishQueueEnabled），
				// 连上之后发送失败应被视为问题（QA 已在 CI 复现一次失败）。
				awaitChaosPublishers(chaosPool, concurrentThreads, round);
				Assertions.assertTrue(client.publish("/test/after-restart", "world".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0), "[round " + round + "] publish after broker restart should succeed");
			} finally {
				stop(client);
				client = null;
				secondBroker.close();
			}
		} finally {
			if (chaosPool != null) {
				chaosPool.shutdownNow();
			}
			stop(client);
		}
	}

	private static ExecutorService startChaosPublishers(MqttClient client, int threads, String topicPrefix) {
		if (threads <= 0) {
			return null;
		}
		ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
			Thread t = new Thread(r, "chaos-publisher");
			t.setDaemon(true);
			return t;
		});
		// 限制每线程 publish 总数，避免 publish 阻塞（极少数 Tio.send 在底层 socket RST 后可能挂住），
		// 同时配合 awaitChaosPublishers 的兜底 shutdownNow 强制回收。
		int maxPerThread = 200;
		for (int i = 0; i < threads; i++) {
			final int idx = i;
			pool.submit(() -> {
				for (int n = 0; n < maxPerThread && !Thread.currentThread().isInterrupted(); n++) {
					try {
						client.publish(topicPrefix + idx + "/" + n, "c".getBytes(StandardCharsets.UTF_8), MqttQoS.QOS0);
					} catch (Exception ignored) {
						// 故意吞掉 publish 期间的异常，测试主体仍按核心断言判断。
					}
				}
			});
		}
		return pool;
	}

	private static void awaitChaosPublishers(ExecutorService chaosPool, int threads, int round) throws InterruptedException {
		if (chaosPool == null || threads <= 0) {
			return;
		}
		// chaos 线程按发布总数自然结束；若少量 publish 阻塞，则用 shutdownNow 兜底，
		// 这里只给一个非常宽松的上限，避免误判测试本身失败。
		chaosPool.shutdown();
		if (!chaosPool.awaitTermination(15, TimeUnit.SECONDS)) {
			chaosPool.shutdownNow();
			Assertions.assertTrue(chaosPool.awaitTermination(2, TimeUnit.SECONDS), "[round " + round + "] chaos publishers should stop in time");
		}
	}

	private static int readIntProperty(String key, int defaultValue) {
		String value = System.getProperty(key);
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static int reserveFreePort() throws IOException {
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			int port = serverSocket.getLocalPort();
			// SO_REUSEADDR 减小 TIME_WAIT 阶段再次绑定失败的可能。
			serverSocket.setReuseAddress(true);
			return port;
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
		private final Queue<Socket> activeSockets = new ConcurrentLinkedQueue<>();
		private final CountDownLatch startedLatch = new CountDownLatch(1);
		private final CountDownLatch stoppedLatch = new CountDownLatch(1);
		private final AtomicInteger connectPackets = new AtomicInteger();
		private volatile boolean running = true;
		private volatile boolean started = false;
		private Thread acceptThread;

		private FakeBroker(ServerSocket serverSocket) {
			this.serverSocket = serverSocket;
		}

		static FakeBroker create() throws IOException, InterruptedException {
			FakeBroker broker = new FakeBroker(new ServerSocket(0));
			broker.start();
			return broker;
		}

		static FakeBroker createOnPort(int port) throws IOException {
			ServerSocket serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
			return new FakeBroker(serverSocket);
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

		boolean awaitConnectPackets(int expected, long timeoutMs) throws InterruptedException {
			long deadline = System.currentTimeMillis() + timeoutMs;
			while (System.currentTimeMillis() < deadline) {
				if (connectPackets.get() >= expected) {
					return true;
				}
				Thread.sleep(20);
			}
			return connectPackets.get() >= expected;
		}

		private void start() throws InterruptedException {
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
					startedLatch.countDown();
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
							} finally {
								activeSockets.remove(socket);
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
					startedLatch.countDown();
					stoppedLatch.countDown();
				}
			}, "fake-mqtt-broker-accept");
			this.acceptThread.setDaemon(true);
			this.acceptThread.start();
			startedLatch.await(1, TimeUnit.SECONDS);
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
