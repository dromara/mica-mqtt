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
import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessageSerializer;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cluster functionality tests for message serialization and deserialization.
 */
class MqttClusterTest {

    @Test
    void testPublishForwardMessageSerialization() {
        PublishForwardMessage msg = new PublishForwardMessage();
        Message mqttMessage = new Message();
        mqttMessage.setMessageType(org.dromara.mica.mqtt.core.server.enums.MessageType.UP_STREAM);
        mqttMessage.setTopic("/test/topic");
        mqttMessage.setPayload("Hello Cluster".getBytes());
        mqttMessage.setQos(1);
        mqttMessage.setRetain(false);
        mqttMessage.setDup(false);
        msg.setMessage(mqttMessage);

        ClusterMessage deserialized = ClusterMessageSerializer.fromClusterData(ClusterMessageSerializer.toClusterData(msg, "192.168.1.1:9000"));
        assertTrue(deserialized instanceof PublishForwardMessage);
        PublishForwardMessage pfm = (PublishForwardMessage) deserialized;
        assertEquals("/test/topic", pfm.getMessage().getTopic());
        assertEquals(1, pfm.getMessage().getQos());
        assertEquals("Hello Cluster", new String(pfm.getMessage().getPayload()));
    }

    @Test
    void testSubscribeNotifyMessageSerialization() {
        SubscribeNotifyMessage msg = new SubscribeNotifyMessage();
        msg.setClientId("client-001");
        msg.setNodeId("192.168.1.1:9000");
        Subscribe subscribe = new Subscribe("/test/#", "client-001", 1, false);
        msg.setSubscriptions(Collections.singletonList(subscribe));

        ClusterMessage deserialized = ClusterMessageSerializer.fromClusterData(ClusterMessageSerializer.toClusterData(msg, "192.168.1.1:9000"));
        assertTrue(deserialized instanceof SubscribeNotifyMessage);
        SubscribeNotifyMessage snm = (SubscribeNotifyMessage) deserialized;
        assertEquals("client-001", snm.getClientId());
        assertEquals("192.168.1.1:9000", snm.getNodeId());
        assertEquals(1, snm.getSubscriptions().size());
        assertEquals("/test/#", snm.getSubscriptions().get(0).getTopicFilter());
    }
}
