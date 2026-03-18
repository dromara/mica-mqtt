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

import org.dromara.mica.mqtt.broker.cluster.codec.BinaryClusterMessageCodec;
import org.dromara.mica.mqtt.broker.cluster.codec.ClusterMessageCodec;
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集群功能测试
 *
 * @author mica-mqtt
 */
class MqttClusterTest {

    @Test
    void testPublishForwardMessageSerialization() {
        ClusterMessageCodec codec = new BinaryClusterMessageCodec();

        PublishForwardMessage msg = new PublishForwardMessage();
        msg.setSourceNode("192.168.1.1:9000");
        msg.setTimestamp(System.currentTimeMillis());

        Message message = new Message();
        message.setMessageType(org.dromara.mica.mqtt.core.server.enums.MessageType.UP_STREAM);
        message.setTopic("/test/topic");
        message.setPayload("Hello Cluster".getBytes());
        message.setQos(1);
        message.setRetain(false);
        message.setDup(false);
        msg.setMessage(message);

        byte[] data = codec.encode(msg);
        assertNotNull(data);
        assertTrue(data.length > 0);

        ClusterMessage deserialized = codec.decode(data);
        assertTrue(deserialized instanceof PublishForwardMessage);
        PublishForwardMessage pfm = (PublishForwardMessage) deserialized;
        assertEquals("/test/topic", pfm.getMessage().getTopic());
        assertEquals(1, pfm.getMessage().getQos());
        assertEquals("Hello Cluster", new String(pfm.getMessage().getPayload()));
    }

    @Test
    void testSubscribeNotifyMessageSerialization() {
        ClusterMessageCodec codec = new BinaryClusterMessageCodec();

        SubscribeNotifyMessage msg = new SubscribeNotifyMessage();
        msg.setClientId("client-001");
        msg.setNodeId("192.168.1.1:9000");
        Subscribe subscribe = new Subscribe("/test/#", "client-001", 1, false);
        msg.setSubscriptions(Collections.singletonList(subscribe));

        byte[] data = codec.encode(msg);
        assertNotNull(data);

        ClusterMessage deserialized = codec.decode(data);
        assertTrue(deserialized instanceof SubscribeNotifyMessage);
        SubscribeNotifyMessage snm = (SubscribeNotifyMessage) deserialized;
        assertEquals("client-001", snm.getClientId());
        assertEquals("192.168.1.1:9000", snm.getNodeId());
        assertEquals(1, snm.getSubscriptions().size());
        assertEquals("/test/#", snm.getSubscriptions().get(0).getTopicFilter());
    }
}
