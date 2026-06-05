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
import net.dreamlu.mica.net.core.Tio;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * mqtt server + client 集成测试，发布版前手工运行
 *
 * @author L.cm
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class MqttServerClientTest {
	private static final Logger logger = LoggerFactory.getLogger(MqttServerClientTest.class);
	private static final int PORT = 1996;
	private static MqttServer server;
	private static MqttClient client;

	@BeforeAll
	static void setUpAll() {
		server = MqttServer.create()
			.enableMqtt(PORT)
			.statEnable(false)
			.debug()
			.start();
		client = MqttClient.create()
			.ip("127.0.0.1")
			.port(PORT)
			.clientId("testClient")
			.connectSync();
	}

	@AfterAll
	static void tearDownAll() {
		if (client != null) {
			client.stop();
		}
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void testQos0Qos1Qos2PublishAndReceive() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(3);
		AtomicInteger qos0Count = new AtomicInteger(0);
		AtomicInteger qos1Count = new AtomicInteger(0);
		AtomicInteger qos2Count = new AtomicInteger(0);
		byte[] payloadQos0 = "Hello QoS0".getBytes(StandardCharsets.UTF_8);
		byte[] payloadQos1 = "Hello QoS1".getBytes(StandardCharsets.UTF_8);
		byte[] payloadQos2 = "Hello QoS2".getBytes(StandardCharsets.UTF_8);

		client.subQos0("/test/qos0", new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				String received = new String(payload, StandardCharsets.UTF_8);
				logger.info("QoS0 received, topic: {}, payload: {}", topic, received);
				assertEquals("Hello QoS0", received);
				assertEquals("/test/qos0", topic);
				qos0Count.incrementAndGet();
				latch.countDown();
			}
		});

		client.subQos1("/test/qos1", new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				String received = new String(payload, StandardCharsets.UTF_8);
				logger.info("QoS1 received, topic: {}, payload: {}", topic, received);
				assertEquals("Hello QoS1", received);
				assertEquals("/test/qos1", topic);
				qos1Count.incrementAndGet();
				latch.countDown();
			}
		});

		client.subQos2("/test/qos2", new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				String received = new String(payload, StandardCharsets.UTF_8);
				logger.info("QoS2 received, topic: {}, payload: {}", topic, received);
				assertEquals("Hello QoS2", received);
				assertEquals("/test/qos2", topic);
				qos2Count.incrementAndGet();
				latch.countDown();
			}
		});

		TimeUnit.MILLISECONDS.sleep(500);

		client.publish("/test/qos0", payloadQos0);
		client.publish("/test/qos1", payloadQos1, MqttQoS.QOS1);
		client.publish("/test/qos2", payloadQos2, MqttQoS.QOS2);

		boolean allReceived = latch.await(10, TimeUnit.SECONDS);
		assertTrue(allReceived, "Not all QoS messages received within timeout");
		assertEquals(1, qos0Count.get(), "QoS0 count mismatch");
		assertEquals(1, qos1Count.get(), "QoS1 count mismatch");
		assertEquals(1, qos2Count.get(), "QoS2 count mismatch");
		logger.info("All QoS 0/1/2 messages received successfully!");
	}

	@Test
	void testRetainedMessage() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		String topic = "/test/retained";
		String payload = "Retained Message Content";

		MqttClient pubClient = MqttClient.create()
			.ip("127.0.0.1")
			.port(PORT)
			.clientId("retainedPub")
			.connectSync();

		MqttClient subClient = MqttClient.create()
			.ip("127.0.0.1")
			.port(PORT)
			.clientId("retainedSub")
			.connectSync();

		// Publish with retain=true
		pubClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), MqttQoS.QOS1, true);
		TimeUnit.MILLISECONDS.sleep(300);

		// Subscribe with a different client — should receive the retained message immediately
		subClient.subQos1(topic, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String t, MqttPublishMessage msg, byte[] bytes) {
				String received = new String(bytes, StandardCharsets.UTF_8);
				logger.info("Retained message received, topic: {}, payload: {}", t, received);
				assertEquals(payload, received);
				latch.countDown();
			}
		});

		boolean received = latch.await(5, TimeUnit.SECONDS);
		assertTrue(received, "Retained message not received on subscribe");

		pubClient.stop();
		subClient.stop();
	}

	@Test
	void testWillMessage() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		String willTopic = "/test/will";
		String willPayload = "unexpected disconnect";

		// Subscribe to will topic BEFORE will client connects
		client.subQos1(willTopic, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				String received = new String(payload, StandardCharsets.UTF_8);
				logger.info("Will message received, topic: {}, payload: {}", topic, received);
				assertEquals(willPayload, received);
				latch.countDown();
			}
		});

		TimeUnit.MILLISECONDS.sleep(200);

		// Create will client
		MqttClient willClient = MqttClient.create()
			.ip("127.0.0.1")
			.port(PORT)
			.clientId("willClient")
			.willMessage(builder -> builder
				.topic(willTopic)
				.messageText(willPayload)
				.qos(MqttQoS.QOS1)
				.retain(false)
			)
			.reconnect(false)
			.connectSync();

		TimeUnit.MILLISECONDS.sleep(200);

		// Force close the client channel without sending DISCONNECT packet
		ClientChannelContext channelContext = willClient.getContext();
		Tio.close(channelContext, null, "Will test force close", false);
		willClient.stop();

		boolean received = latch.await(10, TimeUnit.SECONDS);
		assertTrue(received, "Will message not received");
	}

	@Test
	void testTopicWildcardPlus() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(2);
		AtomicInteger count = new AtomicInteger(0);

		client.subQos0("/test/+/wildcard", new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				count.incrementAndGet();
				logger.info("Wildcard+ received, topic: {}", topic);
				latch.countDown();
			}
		});

		TimeUnit.MILLISECONDS.sleep(300);

		client.publish("/test/foo/wildcard", "match1".getBytes(StandardCharsets.UTF_8));
		client.publish("/test/bar/wildcard", "match2".getBytes(StandardCharsets.UTF_8));

		boolean allReceived = latch.await(5, TimeUnit.SECONDS);
		assertTrue(allReceived, "Not all wildcard+ messages received");
		assertEquals(2, count.get(), "Expected 2 wildcard+ messages");
	}

	@Test
	void testTopicWildcardHash() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(3);
		AtomicInteger count = new AtomicInteger(0);

		client.subQos0("/test/hash/#", new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
				count.incrementAndGet();
				logger.info("Wildcard# received, topic: {}", topic);
				latch.countDown();
			}
		});

		TimeUnit.MILLISECONDS.sleep(300);

		client.publish("/test/hash/one", "hash1".getBytes(StandardCharsets.UTF_8));
		client.publish("/test/hash/two/three", "hash2".getBytes(StandardCharsets.UTF_8));
		client.publish("/test/hash/sub/four/five", "hash3".getBytes(StandardCharsets.UTF_8));

		boolean allReceived = latch.await(5, TimeUnit.SECONDS);
		assertTrue(allReceived, "Not all wildcard# messages received");
		assertEquals(3, count.get(), "Expected 3 wildcard# messages");
	}

	@Test
	void testMultipleSubscribers() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(2);
		AtomicInteger count1 = new AtomicInteger(0);
		AtomicInteger count2 = new AtomicInteger(0);
		String topic = "/test/multi";

		MqttClient client2 = MqttClient.create()
			.ip("127.0.0.1")
			.port(PORT)
			.clientId("multiSubClient")
			.connectSync();

		client.subQos0(topic, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String t, MqttPublishMessage message, byte[] payload) {
				count1.incrementAndGet();
				logger.info("Multi subscriber client1 received: {}", t);
				latch.countDown();
			}
		});

		client2.subQos0(topic, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String t, MqttPublishMessage message, byte[] payload) {
				count2.incrementAndGet();
				logger.info("Multi subscriber client2 received: {}", t);
				latch.countDown();
			}
		});

		TimeUnit.MILLISECONDS.sleep(300);

		client.publish(topic, "multi".getBytes(StandardCharsets.UTF_8));

		boolean allReceived = latch.await(5, TimeUnit.SECONDS);
		assertTrue(allReceived, "Not all multi subscribers received message");
		assertEquals(1, count1.get(), "Client1 should receive 1 message");
		assertEquals(1, count2.get(), "Client2 should receive 1 message");

		client2.stop();
	}

	@Test
	void testUnsubscribe() throws InterruptedException {
		String topic = "/test/unsub";
		AtomicInteger count = new AtomicInteger(0);
		CountDownLatch subLatch = new CountDownLatch(1);

		client.subQos0(topic, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String t, MqttPublishMessage message, byte[] payload) {
				count.incrementAndGet();
				logger.info("Unsub test received (before unsub): {}", t);
				subLatch.countDown();
			}
		});

		TimeUnit.MILLISECONDS.sleep(300);

		// Publish while subscribed — should be received
		client.publish(topic, "before".getBytes(StandardCharsets.UTF_8));
		boolean received = subLatch.await(5, TimeUnit.SECONDS);
		assertTrue(received, "Should receive message before unsubscribe");
		assertEquals(1, count.get(), "Should have received 1 message before unsubscribe");

		// Unsubscribe
		client.unSubscribe(topic);
		TimeUnit.MILLISECONDS.sleep(300);

		// Publish after unsubscribe — should NOT be received
		client.publish(topic, "after".getBytes(StandardCharsets.UTF_8));
		// Wait a bit to ensure no message arrives
		TimeUnit.MILLISECONDS.sleep(1000);

		assertEquals(1, count.get(), "Should not receive more messages after unsubscribe");
		logger.info("Unsubscribe test passed, count remains: {}", count.get());
	}

	@Test
	void testLargePayload() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		String topic = "/test/large";

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			sb.append("Large payload test data, let's make it big enough. ");
		}
		byte[] largePayload = sb.toString().getBytes(StandardCharsets.UTF_8);
		logger.info("Large payload size: {} bytes", largePayload.length);

		client.subQos1(topic, new IMqttClientMessageListener() {
			@Override
			public void onMessage(ChannelContext context, String t, MqttPublishMessage message, byte[] bytes) {
				String received = new String(bytes, StandardCharsets.UTF_8);
				logger.info("Large payload received, size: {} bytes", received.length());
				assertEquals(sb.toString(), received);
				latch.countDown();
			}
		});

		TimeUnit.MILLISECONDS.sleep(300);

		client.publish(topic, largePayload, MqttQoS.QOS1);

		boolean received = latch.await(10, TimeUnit.SECONDS);
		assertTrue(received, "Large payload not received");
		logger.info("Large payload test passed, size: {} bytes", largePayload.length);
	}
}
