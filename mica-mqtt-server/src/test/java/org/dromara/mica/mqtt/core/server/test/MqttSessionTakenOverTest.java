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
import org.dromara.mica.mqtt.core.client.IMqttClientConnectListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一 clientId 互踢测试。
 * <p>
 * MQTT 5.0 规定：服务端因 clientId 重复而踢掉旧连接时，必须下发
 * DISCONNECT(0x8E Session taken over)，让客户端知道被踢的真实原因。
 * MQTT 3.x 没有服务端到客户端的 DISCONNECT 报文，不做要求。
 * </p>
 *
 * @author L.cm
 */
class MqttSessionTakenOverTest {
	private static final Logger logger = LoggerFactory.getLogger(MqttSessionTakenOverTest.class);
	private static final int PORT = 1997;
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
	void kickedClientShouldReceiveSessionTakenOver() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> remarkRef = new AtomicReference<>();
		// 客户端默认为 MQTT 5.0
		MqttClient clientA = MqttClient.create()
			.ip("127.0.0.1")
			.port(PORT)
			.clientId("dupClientId")
			.debug()
			// 关闭自动重连，避免被踢后立刻重连再次互踢，干扰断言
			.reconnect(false)
			.connectListener(new IMqttClientConnectListener() {
				@Override
				public void onConnected(ChannelContext context, boolean isReconnect) {
				}

				@Override
				public void onDisconnect(ChannelContext context, Throwable throwable, String remark, boolean isRemove) {
					remarkRef.set(remark);
					latch.countDown();
				}
			})
			.connectSync();

		// 等待第一个连接的 CONNECT 处理完成，避免两个 CONNECT 并发到达
		TimeUnit.MILLISECONDS.sleep(300);

		MqttClient clientB = MqttClient.create()
			.ip("127.0.0.1")
			.port(PORT)
			.clientId("dupClientId")
			.reconnect(false)
			.connectSync();

		try {
			assertTrue(latch.await(10, TimeUnit.SECONDS), "被踢客户端应在超时前收到断开通知");
			String remark = remarkRef.get();
			logger.info("kicked client remark: {}", remark);
			assertNotNull(remark, "断开原因不应为空");
			assertTrue(remark.contains("0x8E"),
				"断开原因应包含 Session taken over(0x8E)，实际: " + remark);
		} finally {
			clientA.stop();
			clientB.stop();
		}
	}
}
