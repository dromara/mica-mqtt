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

    private final AtomicBoolean node1Received = new AtomicBoolean(false);
    private final AtomicReference<String> node1ReceivedMessage = new AtomicReference<>();
    
    private final AtomicBoolean node2Received = new AtomicBoolean(false);
    private final AtomicReference<String> node2ReceivedMessage = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        node1Received.set(false);
        node1ReceivedMessage.set(null);
        node2Received.set(false);
        node2ReceivedMessage.set(null);

        // 创建第一个节点
        ClusterConfig config1 = new ClusterConfig("127.0.0.1", 9001, message -> {
            String msg = new String(message.getPayload(), StandardCharsets.UTF_8);
            logger.info("Node1 received: {}", msg);
            node1ReceivedMessage.set(msg);
            node1Received.set(true);
        });
        config1.addSeedMember("127.0.0.1", 9001);
        config1.addSeedMember("127.0.0.1", 9002);

        cluster1 = new ClusterImpl(config1);
        cluster1.start();

        // 创建第二个节点
        ClusterConfig config2 = new ClusterConfig("127.0.0.1", 9002, message -> {
            String msg = new String(message.getPayload(), StandardCharsets.UTF_8);
            logger.info("Node2 received: {}", msg);
            node2ReceivedMessage.set(msg);
            node2Received.set(true);
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
        // 重置状态
        node2Received.set(false);
        node2ReceivedMessage.set(null);
        
        // 从 cluster1 广播消息
        String testMessage = "Hello from Node1 - Broadcast";
        cluster1.broadcast(testMessage.getBytes(StandardCharsets.UTF_8));
        
        // 等待消息传递
        Thread.sleep(1000);
        
        assertTrue(node2Received.get(), "Node2 should receive broadcast message");
        assertEquals(testMessage, node2ReceivedMessage.get());
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
