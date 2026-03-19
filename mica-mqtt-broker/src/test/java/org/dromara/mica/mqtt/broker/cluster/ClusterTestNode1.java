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

import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;

import java.util.Arrays;

/**
 * 集群测试 - 节点1
 * 启动命令: java -cp ... org.dromara.mica.mqtt.broker.cluster.ClusterTestNode1
 *
 * @author mica-mqtt
 */
public class ClusterTestNode1 {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Mica MQTT Cluster - Node 1");
        System.out.println("  MQTT Port: 1883, Cluster Port: 9001");
        System.out.println("========================================");

        // 1. 集群配置
        MqttClusterConfig clusterConfig = new MqttClusterConfig()
            .enabled(true)
            .clusterHost("127.0.0.1")
            .clusterPort(9001)
            .seedMembers(Arrays.asList("127.0.0.1:9001", "127.0.0.1:9002", "127.0.0.1:9003"));

        // 2. 创建 MQTT Server
        MqttServerCreator creator = MqttServer.create()
            .name("mqtt-cluster-node-1")
            .nodeName("127.0.0.1:9001")
            .enableMqtt(1883);

        // 3. 使用集群创建器构建并启动
        MqttClusterBrokerCreator brokerCreator = MqttBroker.create(creator)
            .clusterConfig(clusterConfig);
        MqttServer mqttServer = brokerCreator.start();
        MqttClusterManager clusterManager = brokerCreator.getClusterManager();

        System.out.println("Node 1 started successfully!");
        System.out.println("MQTT Server listening on port 1883");

        // 4. 定时下发测试消息（每隔5秒下发一次集群广播消息）
        new Thread(() -> {
            int count = 1;
            while (true) {
                try {
                    Thread.sleep(5000);

                    // 集群广播下发测试（所有订阅了 /test/cluster/topic 的设备都会收到）
                    String broadcastMsg = "Broadcast message from Node 1, count: " + count;
                    clusterManager.publish("/test/cluster/topic", broadcastMsg.getBytes(), 0, false);
                    System.out.println("[Node 1] Published broadcast: " + broadcastMsg);

                    count++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        // 5. 等待关闭
        Thread.sleep(Long.MAX_VALUE);
    }
}
