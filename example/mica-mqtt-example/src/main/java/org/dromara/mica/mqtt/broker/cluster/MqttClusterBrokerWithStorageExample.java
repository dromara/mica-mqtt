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
import org.dromara.mica.mqtt.broker.cluster.config.MqttStorageConfig;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;

import java.net.InetAddress;

/**
 * Two-node cluster broker with V3 H2 persistence.
 * <p>
 * Run this class twice on different ports:
 * </p>
 * <pre>
 *   # Node A
 *   java -cp ... org.dromara.mica.mqtt.broker.cluster.MqttClusterBrokerWithStorageExample 1883 9001 node-a
 *
 *   # Node B
 *   java -cp ... org.dromara.mica.mqtt.broker.cluster.MqttClusterBrokerWithStorageExample 1884 9002 node-b
 * </pre>
 * <p>
 * Then connect a client to either node — the session, retain messages, shared
 * subscriptions, and QoS 1/2 inflight messages are mirrored to the other node
 * via the cluster bus and persisted to local H2 files.
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class MqttClusterBrokerWithStorageExample {

	public static void main(String[] args) {
		// 1st arg: MQTT listener port (default 1883).
		// 2nd arg: cluster port (default 9001).
		// 3rd arg: data-dir suffix to disambiguate per-node H2 file (default "node-a").
		int mqttPort = args.length > 0 ? Integer.parseInt(args[0]) : 1883;
		int clusterPort = args.length > 1 ? Integer.parseInt(args[1]) : 9001;
		String suffix = args.length > 2 ? args[2] : "node-a";

		String localIp;
		try {
			localIp = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			localIp = "127.0.0.1";
		}

		MqttClusterConfig clusterConfig = new MqttClusterConfig()
			.enabled(true)
			.clusterHost(localIp)
			.clusterPort(clusterPort)
			// Use the local-first strategy so messages stay on-node whenever possible.
			.sharedSubStrategy("local_first")
			// V3 persistence: enable H2 MVStore, write per-node to its own directory.
			.storageConfig(new MqttStorageConfig()
				.enabled(true)
				.dataDir("data/mica-mqtt-cluster/" + suffix)
				.inflightTtlMs(60_000L)
				.inflightCleanPeriodMs(30_000L)
				.persistSession(true)
				.persistSharedSub(true)
				.persistRetain(true));

		MqttServerCreator serverCreator = MqttServer.create()
			.name("mqtt-cluster-" + suffix)
			.nodeName(localIp + ":" + clusterPort)
			.enableMqtt(mqttPort);

		MqttClusterBrokerCreator creator = MqttBroker.create(serverCreator)
			.clusterConfig(clusterConfig);

		MqttServer mqttServer = creator.start();
		System.out.println("[Broker:" + suffix + "] MQTT port=" + mqttPort
			+ " cluster port=" + clusterPort
			+ " data dir=" + clusterConfig.getStorageConfig().getDataDir());
		// keep main thread alive
		try {
			Thread.currentThread().join();
		} catch (InterruptedException e) {
			mqttServer.stop();
			Thread.currentThread().interrupt();
		}
	}
}