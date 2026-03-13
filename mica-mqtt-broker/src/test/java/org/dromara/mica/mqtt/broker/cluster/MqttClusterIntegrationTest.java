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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.Node;
import org.tio.server.cluster.core.ClusterApi;
import org.tio.server.cluster.core.ClusterConfig;
import org.tio.server.cluster.core.ClusterImpl;
import org.tio.server.cluster.message.ClusterDataMessage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集群集成测试 - 基于 t-io cluster API
 * 测试两个节点的集群场景
 *
 * @author mica-mqtt
 */
class MqttClusterIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(MqttClusterIntegrationTest.class);

    private ClusterApi cluster1;
    private ClusterApi cluster2;

    @BeforeEach
    void setUp() throws Exception {
        // 创建第一个节点
        ClusterConfig config1 = new ClusterConfig("127.0.0.1", 9001, message -> {
            logger.info("Node1 received: {}", new String(message.getPayload(), StandardCharsets.UTF_8));
        });
        config1.addSeedMember("127.0.0.1", 9001);
        config1.addSeedMember("127.0.0.1", 9002);

        cluster1 = new ClusterImpl(config1);
        cluster1.start();

        // 创建第二个节点
        ClusterConfig config2 = new ClusterConfig("127.0.0.1", 9002, message -> {
            logger.info("Node2 received: {}", new String(message.getPayload(), StandardCharsets.UTF_8));
        });
        config2.addSeedMember("127.0.0.1", 9001);
        config2.addSeedMember("127.0.0.1", 9002);

        cluster2 = new ClusterImpl(config2);
        cluster2.start();

        // 等待集群连接建立
        Thread.sleep(1500);
        
        logger.info("Cluster setup complete");
    }

    @AfterEach
    void tearDown() {
        if (cluster1 != null) {
            cluster1.stop();
        }
        if (cluster2 != null) {
            cluster2.stop();
        }
    }

    @Test
    void testClusterNodesConnected() {
        // 验证两个节点已经互相连接
        assertNotNull(cluster1.getRemoteMembers());
        assertNotNull(cluster2.getRemoteMembers());
        
        logger.info("Node1 remote members: {}", cluster1.getRemoteMembers());
        logger.info("Node2 remote members: {}", cluster2.getRemoteMembers());
        
        // 验证节点互相发现
        assertTrue(cluster1.getRemoteMembers().size() >= 1, "Node1 should have remote members");
        assertTrue(cluster2.getRemoteMembers().size() >= 1, "Node2 should have remote members");
    }

    @Test
    void testBroadcastMessage() throws Exception {
        AtomicBoolean node2Received = new AtomicBoolean(false);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        // 重新创建 cluster2 带消息处理器
        cluster2.stop();
        ClusterConfig config2 = new ClusterConfig("127.0.0.1", 9002, message -> {
            String msg = new String(message.getPayload(), StandardCharsets.UTF_8);
            logger.info("Node2 received broadcast: {}", msg);
            receivedMessage.set(msg);
            node2Received.set(true);
        });
        config2.addSeedMember("127.0.0.1", 9001);
        config2.addSeedMember("127.0.0.1", 9002);
        cluster2 = new ClusterImpl(config2);
        cluster2.start();
        
        Thread.sleep(1000);
        
        // 从 cluster1 广播消息
        String testMessage = "Hello from Node1 - Broadcast";
        cluster1.broadcast(testMessage.getBytes(StandardCharsets.UTF_8));
        
        // 等待消息传递
        Thread.sleep(500);
        
        assertTrue(node2Received.get(), "Node2 should receive broadcast message");
        assertEquals(testMessage, receivedMessage.get());
    }

    @Test
    void testSendToNode() throws Exception {
        AtomicBoolean node2Received = new AtomicBoolean(false);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        // 重新创建 cluster2 带消息处理器
        cluster2.stop();
        ClusterConfig config2 = new ClusterConfig("127.0.0.1", 9002, message -> {
            String msg = new String(message.getPayload(), StandardCharsets.UTF_8);
            logger.info("Node2 received direct: {}", msg);
            receivedMessage.set(msg);
            node2Received.set(true);
        });
        config2.addSeedMember("127.0.0.1", 9001);
        config2.addSeedMember("127.0.0.1", 9002);
        cluster2 = new ClusterImpl(config2);
        cluster2.start();
        
        Thread.sleep(1000);
        
        // 从 cluster1 直接发送消息到 cluster2
        Node node2 = new Node("127.0.0.1", 9002);
        String testMessage = "Hello from Node1 - Direct";
        cluster1.send(node2, testMessage.getBytes(StandardCharsets.UTF_8));
        
        // 等待消息传递
        Thread.sleep(500);
        
        assertTrue(node2Received.get(), "Node2 should receive direct message");
        assertEquals(testMessage, receivedMessage.get());
    }

    @Test
    void testLocalMemberInfo() {
        Node localNode1 = cluster1.getLocalMember();
        Node localNode2 = cluster2.getLocalMember();
        
        assertNotNull(localNode1);
        assertNotNull(localNode2);
        
        logger.info("Node1 local member: {}:{}", localNode1.getIp(), localNode1.getPort());
        logger.info("Node2 local member: {}:{}", localNode2.getIp(), localNode2.getPort());
        
        assertEquals("127.0.0.1", localNode1.getIp());
        assertEquals(9001, localNode1.getPort());
        assertEquals("127.0.0.1", localNode2.getIp());
        assertEquals(9002, localNode2.getPort());
    }
}
