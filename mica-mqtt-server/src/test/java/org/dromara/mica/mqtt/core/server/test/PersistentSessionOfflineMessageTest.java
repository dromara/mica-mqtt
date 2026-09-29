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

import net.dreamlu.mica.net.client.ClientChannelContext;
import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * 持久会话与离线消息集成测试：
 * <ol>
 *     <li>Clean Start=false 订阅 → 断开 → 发布 QoS1 → 同 clientId 重连收到离线消息；</li>
 *     <li>Clean Start=true 重连丢弃旧会话，离线消息不回放；</li>
 *     <li>关闭持久会话后离线消息直接丢弃。</li>
 * </ol>
 *
 * @author wcmzllx
 */
class PersistentSessionOfflineMessageTest {
	/**
	 * 由 {@link #findFreePort()} 在 {@code @BeforeAll} 中动态分配，
	 * 避免与并行用例或本机已占用端口冲突。
	 */
	private static int port;
	private static final String CLIENT_ID = "persistent-client";
	private static final String TOPIC = "persist/offline";
	private static final byte[] OFFLINE_PAYLOAD = "offline-payload".getBytes(StandardCharsets.UTF_8);
	private static MqttServer server;
	private static InMemoryMqttSessionManager sessionManager;

	@BeforeAll
	static void setUpAll() {
		sessionManager = new InMemoryMqttSessionManager(1000, 0L, 1000);
		port = findFreePort();
		server = MqttServer.create()
			.enableMqtt(port)
			.statEnable(false)
			.sessionManager(sessionManager)
			.enablePersistentSession(true)
			.start();
	}

	@AfterAll
	static void tearDownAll() {
		if (server != null) {
			server.stop();
		}
	}

	/**
	 * 持久会话：断开期间的 QoS1 消息进入离线队列，重连后回放。
	 */
	@Test
	@Timeout(40)
	void testPersistentSessionReceivesOfflineMessage() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		CountDownLatch offlineReceived = new CountDownLatch(1);

		MqttClient subscriber = persistentClient(CLIENT_ID);
		subscriber.subscribe(TOPIC, MqttQoS.QOS1, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				received.add(new String(payload, StandardCharsets.UTF_8));
				offlineReceived.countDown();
			}
		});
		// 断开连接，服务端保留订阅与会话
		waitUntil(() -> !sessionManager.getSubscriptions(CLIENT_ID).isEmpty());
		Assertions.assertFalse(sessionManager.getSubscriptions(CLIENT_ID).isEmpty(),
			"server should have registered the subscription");
		Assertions.assertTrue(sessionManager.isPersistentSession(CLIENT_ID),
			"cleanStart=false + sessionExpiryInterval=3600 should be a persistent session");
		subscriber.disconnect();
		Thread.sleep(500);
		Assertions.assertTrue(sessionManager.hasSession(CLIENT_ID),
			"persistent session should survive disconnect");
		Assertions.assertTrue(sessionManager.isPersistentSession(CLIENT_ID),
			"session should stay persistent after disconnect");

		MqttClient publisher = awaitConnected("publisher-" + System.nanoTime(), port, true, null);
		// 用原始字节发布，避免 JSON 序列化给 payload 加上引号，便于精确断言
		publisher.publish(new MqttPublishBuilder()
			.topicName(TOPIC)
			.payload(OFFLINE_PAYLOAD)
			.qos(MqttQoS.QOS1));

		// 订阅者离线时消息进入离线队列
		Assertions.assertTrue(waitUntil(() -> sessionManager.getOfflineMessageCount(CLIENT_ID) == 1),
			"offline message should be queued for persistent session, actual: "
				+ sessionManager.getOfflineMessageCount(CLIENT_ID));
		publisher.stop();

		// 同 clientId 重连，Clean Start=false → sessionPresent=1，应回放离线消息。
		// 用全局监听器接收：真实客户端重连后无需重新 SUBSCRIBE，服务端已有订阅会直接投递。
		MqttClient reconnected = MqttClient.create()
			.ip("127.0.0.1")
			.port(port)
			.clientId(CLIENT_ID)
			.cleanStart(false)
			.sessionExpiryIntervalSecs(3600)
			.globalMessageListener((context, topic, message, payload) -> {
				received.add(new String(payload, StandardCharsets.UTF_8));
				offlineReceived.countDown();
			})
			.connectSync();

		Assertions.assertTrue(offlineReceived.await(15, TimeUnit.SECONDS),
			"reconnected persistent session should receive queued offline message");
		Assertions.assertEquals(1, received.size());
		Assertions.assertEquals("offline-payload", received.get(0));
		// 回放后服务端离线队列被清空
		Assertions.assertTrue(waitUntil(() -> sessionManager.getOfflineMessageCount(CLIENT_ID) == 0),
			"offline queue should be drained after replay");

		reconnected.stop();
	}

	/**
	 * Clean Start=true：旧会话被清理，离线消息不回放。
	 */
	@Test
	@Timeout(40)
	void testCleanStartDropsOfflineMessages() throws Exception {
		String clientId = "clean-client-" + System.nanoTime();

		MqttClient subscriber = persistentClient(clientId);
		subscriber.subscribe(TOPIC, MqttQoS.QOS1, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
			}
		});
		// 等服务端确认订阅后再断开，避免 SUBSCRIBE/DISCONNECT 竞态
		waitUntil(() -> !sessionManager.getSubscriptions(clientId).isEmpty());
		subscriber.disconnect();
		Thread.sleep(500);
		Assertions.assertTrue(sessionManager.isPersistentSession(clientId),
			"cleanStart=false session should be persistent after disconnect");

		MqttClient publisher = awaitConnected("publisher-" + System.nanoTime(), port, true, null);
		publisher.publish(new MqttPublishBuilder()
			.topicName(TOPIC)
			.payload("should-be-dropped".getBytes(StandardCharsets.UTF_8))
			.qos(MqttQoS.QOS1));

		Assertions.assertTrue(waitUntil(() -> sessionManager.getOfflineMessageCount(clientId) == 1),
			"offline message should be queued for persistent session, actual: "
				+ sessionManager.getOfflineMessageCount(clientId));
		publisher.stop();

		// Clean Start=true 重连：旧会话直接丢弃（spec 3.1.2.11.4）
		MqttClient reconnected = awaitConnected(clientId, port, true, null);

		Assertions.assertTrue(waitUntil(() -> sessionManager.getOfflineMessageCount(clientId) == 0),
			"clean start must discard the previous session and its offline queue; "
				+ "offlineCount=" + sessionManager.getOfflineMessageCount(clientId)
				+ ", hasSession=" + sessionManager.hasSession(clientId)
				+ ", persistent=" + sessionManager.isPersistentSession(clientId)
				+ ", cleanStart=" + sessionManager.isCleanStart(clientId)
				+ ", expiry=" + sessionManager.getSessionExpiryInterval(clientId));
		Assertions.assertFalse(sessionManager.isPersistentSession(clientId),
			"clean start session must not be persistent");

		reconnected.stop();
	}

	/**
	 * 非持久会话（enablePersistentSession=false）：离线消息直接丢弃。
	 */
	@Test
	@Timeout(40)
	void testNonPersistentSessionDropsOfflineMessage() throws Exception {
		IMqttSessionManager nonPersistent = new InMemoryMqttSessionManager(1000, 0L, 1000);
		int secondPort = findFreePort();
		MqttServer localServer = MqttServer.create()
			.enableMqtt(secondPort)
			.statEnable(false)
			.sessionManager(nonPersistent)
			.enablePersistentSession(false)
			.start();
		try {
			String clientId = "non-persistent-" + System.nanoTime();
			MqttClient subscriber = awaitConnected(clientId, secondPort, false, 3600);
			subscriber.subscribe(TOPIC, MqttQoS.QOS1, new IMqttClientMessageListener() {
				@Override
				public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				}
			});
			waitUntil(() -> !nonPersistent.getSubscriptions(clientId).isEmpty());
			subscriber.disconnect();
			Thread.sleep(500);

			MqttClient publisher = awaitConnected("publisher-" + System.nanoTime(), secondPort, true, null);
			publisher.publish(new MqttPublishBuilder()
				.topicName(TOPIC)
				.payload("dropped".getBytes(StandardCharsets.UTF_8))
				.qos(MqttQoS.QOS1));
			Thread.sleep(500);
			publisher.stop();

			Thread.sleep(500);
			Assertions.assertEquals(0, nonPersistent.getOfflineMessageCount(clientId),
				"non-persistent session must not queue offline messages");
			Assertions.assertFalse(nonPersistent.isPersistentSession(clientId),
				"enablePersistentSession(false) must not mark sessions as persistent");
		} finally {
			localServer.stop();
		}
	}

	private static MqttClient persistentClient(String clientId) {
		return awaitConnected(clientId, port, false, 3600);
	}

	/**
	 * 向操作系统申请一个当前空闲的端口，避免写死端口与并行用例 / 本机已占用端口冲突。
	 */
	private static int findFreePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new IllegalStateException("failed to allocate a free port for test", e);
		}
	}

	/**
	 * 建立连接并等待连接真正就绪。
	 * <p>
	 * {@code connectSync()} 返回时客户端侧的 accepted 标记可能尚未置位，
	 * 此时 publish 会被客户端直接丢弃，导致用例出现偶发失败。
	 */
	private static MqttClient awaitConnected(String clientId, int serverPort, boolean cleanStart, Integer sessionExpirySecs) {
		MqttClientCreator creator = MqttClient.create()
			.ip("127.0.0.1")
			.port(serverPort)
			.clientId(clientId)
			.cleanStart(cleanStart);
		if (sessionExpirySecs != null) {
			creator.sessionExpiryIntervalSecs(sessionExpirySecs);
		}
		MqttClient client = creator.connectSync();
		Assertions.assertTrue(waitUntil(() -> {
			ClientChannelContext context = client.getContext();
			return context != null && context.isAccepted();
		}), "clientId:" + clientId + " should become ready after connect");
		return client;
	}

	private static boolean waitUntil(BooleanSupplier condition) {
		long deadline = System.currentTimeMillis() + 5000L;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean()) {
				return true;
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return condition.getAsBoolean();
	}
}
