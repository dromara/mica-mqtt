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

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broker 生命周期回归测试。
 * <p>
 * 覆盖三类曾经出过问题的场景：
 * <ol>
 *   <li>集群模式下 {@code build()} 只装配不启动 —— 静默退化成单机，必须改为显式报错</li>
 *   <li>{@code build()} 重复调用会重复注册流水线处理器 —— 导致消息双投、规则双跑</li>
 *   <li>{@code MqttServer#stop()} 不停集群管理器 —— 集群端口不释放、留下僵尸节点</li>
 * </ol>
 */
class MqttClusterBrokerLifecycleTest {

	/**
	 * 集群模式下 build() 必须报错：build() 只完成装配，不建立集群连接。
	 */
	@Test
	void build_inClusterMode_throwsIllegalStateException() throws IOException {
		MqttClusterBrokerCreator creator = MqttBroker.create()
			.clusterConfig(new MqttClusterConfig().enabled(true).clusterPort(freePort()));

		IllegalStateException ex = assertThrows(IllegalStateException.class, creator::build);
		assertTrue(ex.getMessage().contains("start()"),
			"异常信息应引导用户改用 start()，实际: " + ex.getMessage());
	}

	/**
	 * 非集群模式下 build() 正常返回（不启动服务，仅装配）。
	 */
	@Test
	void build_inNonClusterMode_returnsServer() {
		MqttClusterBrokerCreator creator = MqttBroker.create();
		MqttServer server = creator.build();
		assertNotNull(server);
	}

	/**
	 * build() 幂等：重复调用返回同一实例，流水线处理器不被重复注册。
	 */
	@Test
	void build_calledTwice_doesNotDuplicatePipelineHandlers() throws Exception {
		MqttClusterBrokerCreator creator = MqttBroker.create();
		MqttServer first = creator.build();
		int handlerCount = publishHandlerCount(creator.getServerCreator());

		MqttServer second = creator.build();

		assertSame(first, second, "重复 build() 应返回同一个实例");
		assertEquals(handlerCount, publishHandlerCount(creator.getServerCreator()),
			"重复 build() 不应重复注册发布管线处理器");
	}

	/**
	 * start() 幂等：重复调用返回同一实例，不会重复绑定集群端口。
	 */
	@Test
	void start_calledTwice_keepsSingleInstance() throws Exception {
		int clusterPort = freePort();
		MqttClusterBrokerCreator creator = clusterCreator(clusterPort, freePort());
		try {
			MqttServer first = creator.start();
			MqttServer second = creator.start();
			assertSame(first, second, "重复 start() 应返回同一个实例");
			assertTrue(awaitPortTaken(clusterPort, 5_000L),
				"集群端口应处于监听状态");
		} finally {
			creator.getClusterManager().stop();
		}
	}

	/**
	 * 关闭 MQTT 服务端后集群端口必须被释放：否则同端口重启绑不上，
	 * 且节点会作为僵尸节点继续被路由命中。
	 */
	@Test
	void stop_releasesClusterPort() throws Exception {
		int clusterPort = freePort();
		MqttClusterBrokerCreator creator = clusterCreator(clusterPort, freePort());
		MqttServer server = creator.start();
		try {
			assertTrue(awaitPortTaken(clusterPort, 5_000L), "集群端口应处于监听状态");
		} finally {
			server.stop();
		}
		assertTrue(awaitPortFree(clusterPort, 5_000L),
			"关闭后集群端口 " + clusterPort + " 应被释放");
	}

	/**
	 * 关闭 MQTT 服务端后集群管理器也应停止（不再广播心跳）。
	 */
	@Test
	void stop_stopsClusterManager() throws Exception {
		int clusterPort = freePort();
		MqttClusterBrokerCreator creator = clusterCreator(clusterPort, freePort());
		MqttServer server = creator.start();
		server.stop();
		// 关闭后再调一次不应抛异常（幂等）
		server.stop();
		assertTrue(awaitPortFree(clusterPort, 5_000L),
			"重复关闭后集群端口 " + clusterPort + " 应仍处于释放状态");
	}

	private static MqttClusterBrokerCreator clusterCreator(int clusterPort, int mqttPort) {
		MqttServerCreator serverCreator = MqttServer.create()
			.name("lifecycle-test")
			.nodeName("127.0.0.1:" + clusterPort)
			.enableMqtt(mqttPort);
		return MqttBroker.create(serverCreator)
			.clusterConfig(new MqttClusterConfig().enabled(true)
				.clusterHost("127.0.0.1")
				.clusterPort(clusterPort));
	}

	@SuppressWarnings("unchecked")
	private static int publishHandlerCount(MqttServerCreator creator) throws Exception {
		Field pipelineField = MqttServerCreator.class.getDeclaredField("publishPipeline");
		pipelineField.setAccessible(true);
		Object pipeline = pipelineField.get(creator);
		Field handlersField = pipeline.getClass().getDeclaredField("handlers");
		handlersField.setAccessible(true);
		return ((List<Object>) handlersField.get(pipeline)).size();
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static boolean canBind(int port) {
		try (ServerSocket socket = new ServerSocket(port)) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean awaitPortTaken(int port, long timeoutMs) throws InterruptedException {
		return await(() -> !canBind(port), timeoutMs);
	}

	private static boolean awaitPortFree(int port, long timeoutMs) throws InterruptedException {
		return await(() -> canBind(port), timeoutMs);
	}

	private static boolean await(Check check, long timeoutMs) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (check.done()) {
				return true;
			}
			Thread.sleep(100L);
		}
		return check.done();
	}

	@FunctionalInterface
	private interface Check {
		boolean done();
	}
}
