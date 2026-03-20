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

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;

import java.util.Arrays;

/**
 * 集群测试 - 节点2
 * 启动命令: java -cp ... org.dromara.mica.mqtt.broker.cluster.ClusterTestNode2
 *
 * @author mica-mqtt
 */
public class ClusterTestNode2 {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Mica MQTT Cluster - Node 2");
        System.out.println("  MQTT Port: 1884, Cluster Port: 9002");
        System.out.println("========================================");

        // 1. 集群配置
        MqttClusterConfig clusterConfig = new MqttClusterConfig()
            .enabled(true)
            .clusterHost("127.0.0.1")
            .clusterPort(9002)
            .seedMembers(Arrays.asList("127.0.0.1:9001", "127.0.0.1:9002", "127.0.0.1:9003"));

        // 2. 创建 MQTT Server
        MqttServerCreator creator = MqttServer.create()
            .name("mqtt-cluster-node-2")
            .nodeName("127.0.0.1:9002")
            .enableMqtt(1884);

        // 3. 使用集群创建器构建并启动
        MqttServer mqttServer = MqttBroker.create(creator)
            .clusterConfig(clusterConfig)
            .start();

        System.out.println("Node 2 started successfully!");
        System.out.println("MQTT Server listening on port 1884");

        // 4. 等待关闭
        Thread.sleep(Long.MAX_VALUE);
    }
}
