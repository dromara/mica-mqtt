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

package org.dromara.mica.mqtt.broker.cluster;

import net.dreamlu.mica.net.client.ClientChannelContext;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
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
 * 集群模式下持久会话的离线消息回归测试。
 * <p>
 * {@code ClusterMqttSessionManager} 是 {@code IMqttSessionManager} 的装饰器，
 * {@code IMqttSessionManager} 的离线消息方法默认实现是「不入队、不返回」，
 * 装饰器若不重写，集群模式下持久会话的离线消息会被静默丢弃
 * （单机模式正常，因为单机用的是 {@code InMemoryMqttSessionManager}）。
 * </p>
 *
 * @author L.cm
 */
class ClusterPersistentSessionOfflineMessageTest {

	private static final String CLIENT_ID = "cluster-persistent-client";
	private static final String TOPIC = "persist/cluster/offline";
	private static final byte[] OFFLINE_PAYLOAD = "cluster-offline-payload".getBytes(StandardCharsets.UTF_8);

	private static int port;
	private static MqttServer server;
	private static MqttClusterBrokerCreator creator;
	private static ClusterMqttSessionManager sessionManager;

	@BeforeAll
	static void setUpAll() {
		port = findFreePort();
		MqttServerCreator serverCreator = MqttServer.create()
			.name("cluster-offline-test")
			.nodeName("127.0.0.1:" + findFreePort())
			.enableMqtt(port)
			.statEnable(false)
			.enablePersistentSession(true);
		creator = MqttBroker.create(serverCreator)
			.clusterConfig(new MqttClusterConfig().enabled(true)
				.clusterHost("127.0.0.1")
				.clusterPort(findFreePort()));
		server = creator.start();
		sessionManager = creator.getClusterSessionManager();
	}

	@AfterAll
	static void tearDownAll() {
		if (server != null) {
			server.stop();
		}
	}

	/**
	 * 集群模式下持久会话的离线消息必须入队，并在重连后回放。
	 * <p>
	 * 修复前：{@code ClusterMqttSessionManager} 未重写离线消息方法，
	 * 入队结果恒为 false，离线队列长度恒为 0，消息静默丢失。
	 * </p>
	 */
	@Test
	@Timeout(60)
	void clusterMode_persistentSession_receivesOfflineMessage() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		CountDownLatch offlineReceived = new CountDownLatch(1);

		MqttClient subscriber = awaitConnected(CLIENT_ID, false, 3600);
		subscriber.subscribe(TOPIC, MqttQoS.QOS1, new IMqttClientMessageListener() {
			@Override
			public void onMessage(net.dreamlu.mica.net.core.ChannelContext context, String topic,
									MqttPublishMessage message, byte[] payload) {
				received.add(new String(payload, StandardCharsets.UTF_8));
				offlineReceived.countDown();
			}
		});
		Assertions.assertTrue(waitUntil(() -> !sessionManager.getSubscriptions(CLIENT_ID).isEmpty()),
			"服务端应已登记订阅");
		Assertions.assertTrue(sessionManager.isPersistentSession(CLIENT_ID),
			"cleanStart=false + sessionExpiryInterval=3600 应为持久会话");

		subscriber.disconnect();
		Thread.sleep(500);
		Assertions.assertTrue(sessionManager.hasSession(CLIENT_ID),
			"持久会话应在断开后保留");

		MqttClient publisher = awaitConnected("cluster-publisher-" + System.nanoTime(), true, null);
		publisher.publish(new MqttPublishBuilder()
			.topicName(TOPIC)
			.payload(OFFLINE_PAYLOAD)
			.qos(MqttQoS.QOS1));

		Assertions.assertTrue(
			waitUntil(() -> sessionManager.getOfflineMessageCount(CLIENT_ID) == 1),
			"集群模式下离线消息应入队，实际队列长度: "
				+ sessionManager.getOfflineMessageCount(CLIENT_ID));
		publisher.stop();

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

		Assertions.assertTrue(offlineReceived.await(20, TimeUnit.SECONDS),
			"重连后的持久会话应收到排队的离线消息");
		Assertions.assertEquals(1, received.size());
		Assertions.assertEquals("cluster-offline-payload", received.get(0));
		Assertions.assertTrue(waitUntil(() -> sessionManager.getOfflineMessageCount(CLIENT_ID) == 0),
			"回放后离线队列应被清空");

		reconnected.stop();
	}

	/**
	 * 非持久会话在集群模式下不应入队（与单机行为保持一致）。
	 */
	@Test
	@Timeout(60)
	void clusterMode_nonPersistentSession_dropsOfflineMessage() throws Exception {
		String clientId = "cluster-clean-" + System.nanoTime();
		MqttClient subscriber = awaitConnected(clientId, true, null);
		subscriber.subscribe(TOPIC, MqttQoS.QOS1, new IMqttClientMessageListener() {
			@Override
			public void onMessage(net.dreamlu.mica.net.core.ChannelContext context, String topic,
									MqttPublishMessage message, byte[] payload) {
			}
		});
		Assertions.assertTrue(waitUntil(() -> !sessionManager.getSubscriptions(clientId).isEmpty()));
		subscriber.disconnect();
		Thread.sleep(500);

		MqttClient publisher = awaitConnected("cluster-publisher-" + System.nanoTime(), true, null);
		publisher.publish(new MqttPublishBuilder()
			.topicName(TOPIC)
			.payload("dropped".getBytes(StandardCharsets.UTF_8))
			.qos(MqttQoS.QOS1));
		Thread.sleep(800);
		publisher.stop();

		Assertions.assertEquals(0, sessionManager.getOfflineMessageCount(clientId),
			"非持久会话不应缓存离线消息");
	}

	private static MqttClient awaitConnected(String clientId, boolean cleanStart, Integer sessionExpirySecs) {
		MqttClientCreator creator = MqttClient.create()
			.ip("127.0.0.1")
			.port(port)
			.clientId(clientId)
			.cleanStart(cleanStart);
		if (sessionExpirySecs != null) {
			creator.sessionExpiryIntervalSecs(sessionExpirySecs);
		}
		MqttClient client = creator.connectSync();
		Assertions.assertTrue(waitUntil(() -> {
			ClientChannelContext context = client.getContext();
			return context != null && context.isAccepted();
		}), "clientId:" + clientId + " 应在连接后进入就绪状态");
		return client;
	}

	private static int findFreePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new IllegalStateException("failed to allocate a free port for test", e);
		}
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
