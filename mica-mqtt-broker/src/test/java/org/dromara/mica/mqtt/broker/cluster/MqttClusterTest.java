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

import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.MessageType;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.junit.jupiter.api.Test;
import org.tio.core.Node;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集群功能测试
 *
 * @author mica-mqtt
 */
class MqttClusterTest {

    @Test
    void testClusterMessageSerialization() throws Exception {
        // 测试消息序列化和反序列化
        PublishForwardMessage msg = new PublishForwardMessage();
        msg.setType(MessageType.PUBLISH_FORWARD);
        msg.setSourceNode("192.168.1.1:9000");
        msg.setTopic("/test/topic");
        msg.setPayload("Hello Cluster".getBytes());
        msg.setQos(1);
        msg.setRetain(false);
        msg.setMessageId("msg-001");
        msg.setTimestamp(System.currentTimeMillis());

        // 序列化
        MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig(), "test-node");
        java.lang.reflect.Method serializeMethod = MqttClusterManager.class.getDeclaredMethod("serialize", ClusterMessage.class);
        serializeMethod.setAccessible(true);
        byte[] data = (byte[]) serializeMethod.invoke(manager, msg);

        assertNotNull(data);
        assertTrue(data.length > 0);

        // 反序列化
        java.lang.reflect.Method deserializeMethod = MqttClusterManager.class.getDeclaredMethod("deserialize", byte[].class);
        deserializeMethod.setAccessible(true);
        ClusterMessage deserialized = (ClusterMessage) deserializeMethod.invoke(manager, data);

        assertTrue(deserialized instanceof PublishForwardMessage);
        PublishForwardMessage pfm = (PublishForwardMessage) deserialized;
        assertEquals("/test/topic", pfm.getTopic());
        assertEquals(1, pfm.getQos());
        assertEquals("Hello Cluster", new String(pfm.getPayload()));
    }

    @Test
    void testSubscribeNotifyMessageSerialization() throws Exception {
        SubscribeNotifyMessage msg = new SubscribeNotifyMessage();
        msg.setType(MessageType.SUBSCRIBE_NOTIFY);
        msg.setClientId("client-001");
        msg.setNodeId("192.168.1.1:9000");
        Subscribe subscribe = new Subscribe("/test/#", "client-001", 1, false);
        msg.setSubscriptions(Collections.singletonList(subscribe));

        MqttClusterManager manager = new MqttClusterManager(new MqttClusterConfig(), "test-node");
        java.lang.reflect.Method serializeMethod = MqttClusterManager.class.getDeclaredMethod("serialize", ClusterMessage.class);
        serializeMethod.setAccessible(true);
        byte[] data = (byte[]) serializeMethod.invoke(manager, msg);

        java.lang.reflect.Method deserializeMethod = MqttClusterManager.class.getDeclaredMethod("deserialize", byte[].class);
        deserializeMethod.setAccessible(true);
        ClusterMessage deserialized = (ClusterMessage) deserializeMethod.invoke(manager, data);

        assertTrue(deserialized instanceof SubscribeNotifyMessage);
        SubscribeNotifyMessage snm = (SubscribeNotifyMessage) deserialized;
        assertEquals("client-001", snm.getClientId());
        assertEquals("192.168.1.1:9000", snm.getNodeId());
        assertEquals(1, snm.getSubscriptions().size());
        assertEquals("/test/#", snm.getSubscriptions().get(0).getTopicFilter());
    }
}
