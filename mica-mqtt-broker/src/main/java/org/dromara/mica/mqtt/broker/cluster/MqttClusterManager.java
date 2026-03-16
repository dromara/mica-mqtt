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
import org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.GenericClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.MessageType;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.broker.cluster.message.StateSyncResponseMessage;
import org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.message.UnsubscribeNotifyMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.Node;
import org.tio.server.cluster.core.ClusterApi;
import org.tio.server.cluster.core.ClusterConfig;
import org.tio.server.cluster.core.ClusterImpl;
import org.tio.server.cluster.message.ClusterDataMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MqttClusterManager {
    private static final Logger logger = LoggerFactory.getLogger(MqttClusterManager.class);

    private ClusterApi cluster;
    private MqttServer mqttServer;
    private final MqttClusterConfig config;
    private final String localNodeId;

    public MqttClusterManager(MqttClusterConfig config, String localNodeId) {
        this.config = config;
        this.localNodeId = localNodeId;
    }

    public void setMqttServer(MqttServer mqttServer) {
        this.mqttServer = mqttServer;
    }

    public void start() throws Exception {
        // 先启动 MQTT 服务
        if (mqttServer != null) {
            mqttServer.start();
            logger.info("MQTT Server started");
        }

        if (!config.isEnabled()) {
            return;
        }

        ClusterConfig clusterConfig = new ClusterConfig(
            config.getClusterHost(),
            config.getClusterPort(),
            this::handleClusterMessage
        );

        // 添加种子节点
        for (String seed : config.getSeedMembers()) {
            String[] parts = seed.split(":");
            clusterConfig.addSeedMember(parts[0], Integer.parseInt(parts[1]));
        }

        cluster = new ClusterImpl(clusterConfig);
        cluster.start();

        logger.info("Mqtt cluster manager started on {}:{} with nodeName {}", config.getClusterHost(), config.getClusterPort(), localNodeId);

        // 如果是晚加入的节点，向种子节点请求状态同步
        if (cluster.isLateJoinMember()) {
            requestStateSync();
        }
    }

    /**
     * 请求状态同步
     */
    private void requestStateSync() {
        Collection<Node> seedMembers = cluster.getSeedMembers();
        for (Node seed : seedMembers) {
            if (!nodeToString(seed).equals(localNodeId)) {
                try {
                    GenericClusterMessage syncRequest = new GenericClusterMessage();
                    syncRequest.setType(MessageType.STATE_SYNC_REQUEST);
                    syncRequest.setSourceNode(localNodeId);
                    cluster.send(seed, serialize(syncRequest));
                    logger.info("Sent state sync request to seed node: {}", seed);
                    break; // 只需向一个节点请求
                } catch (Exception e) {
                    logger.error("Failed to send state sync request to: {}", seed, e);
                }
            }
        }
    }

    private void handleClusterMessage(ClusterDataMessage message) {
        try {
            byte[] payload = message.getPayload();
            if (payload == null || payload.length == 0) {
                return;
            }
            ClusterMessage clusterMsg = deserialize(payload);
            if (clusterMsg != null) {
                logger.debug("Received cluster message of type: {}", clusterMsg.getType());
                handleClusterMessageInternal(clusterMsg, message);
            }
        } catch (Exception e) {
            logger.error("Error handling cluster message", e);
        }
    }

    private void handleClusterMessageInternal(ClusterMessage clusterMsg, ClusterDataMessage rawMessage) {
        ClusterMqttSessionManager sessionManager = (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
        switch (clusterMsg.getType()) {
            case PUBLISH_FORWARD: {
                PublishForwardMessage pfm = (PublishForwardMessage) clusterMsg;
                mqttServer.publishAll(pfm.getTopic(), pfm.getPayload(), MqttQoS.valueOf(pfm.getQos()), pfm.isRetain());
                break;
            }
            case SUBSCRIBE_NOTIFY: {
                SubscribeNotifyMessage snm = (SubscribeNotifyMessage) clusterMsg;
                sessionManager.syncRemoteSubscriptions(snm.getClientId(), snm.getNodeId(), snm.getSubscriptions());
                break;
            }
            case UNSUBSCRIBE_NOTIFY: {
                UnsubscribeNotifyMessage unm = (UnsubscribeNotifyMessage) clusterMsg;
                sessionManager.removeRemoteSubscriptions(unm.getClientId(), unm.getTopics());
                break;
            }
            case CLIENT_CONNECT: {
                ClientConnectMessage ccm = (ClientConnectMessage) clusterMsg;
                sessionManager.registerRemoteClient(ccm.getClientId(), ccm.getSourceNode());
                break;
            }
            case CLIENT_DISCONNECT: {
                ClientDisconnectMessage cdm = (ClientDisconnectMessage) clusterMsg;
                sessionManager.removeRemoteClient(cdm.getClientId());
                break;
            }
            case STATE_SYNC_REQUEST:
                handleStateSyncRequest(clusterMsg.getSourceNode());
                break;
            case STATE_SYNC_RESPONSE: {
                StateSyncResponseMessage ssm = (StateSyncResponseMessage) clusterMsg;
                sessionManager.syncFullState(ssm.getClientNodeMap(), ssm.getSubscriptionMap());
                logger.info("State sync completed, received {} client mappings", ssm.getClientNodeMap().size());
                break;
            }
            case NODE_LEAVE: {
                String leavingNodeId = clusterMsg.getSourceNode();
                sessionManager.clearNodeClientsAndSubscriptions(leavingNodeId);
                logger.info("Node {} left cluster, cleaned up its clients and subscriptions", leavingNodeId);
                break;
            }
            default:
                logger.warn("Unknown cluster message type: {}", clusterMsg.getType());
                break;
        }
    }

    /**
     * 处理状态同步请求
     */
    private void handleStateSyncRequest(String requestNodeId) {
        ClusterMqttSessionManager sessionManager = (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
        
        StateSyncResponseMessage response = new StateSyncResponseMessage();
        response.setType(MessageType.STATE_SYNC_RESPONSE);
        response.setSourceNode(localNodeId);
        
        // 获取远程客户端映射
        Map<String, String> clientNodeMap = sessionManager.getRemoteClientNodeMap();
        response.setClientNodeMap(clientNodeMap);
        
        // 获取所有订阅
        Map<String, List<Subscribe>> subscriptionMap = new HashMap<>();
        for (Map.Entry<String, String> entry : clientNodeMap.entrySet()) {
            String clientId = entry.getKey();
            List<Subscribe> subs = sessionManager.getClientSubscriptions(clientId);
            if (subs != null && !subs.isEmpty()) {
                subscriptionMap.put(clientId, subs);
            }
        }
        response.setSubscriptionMap(subscriptionMap);
        
        // 发送响应
        try {
            byte[] data = serialize(response);
            String[] parts = requestNodeId.split(":");
            Node node = new Node(parts[0], Integer.parseInt(parts[1]));
            cluster.send(node, data);
            logger.info("Sent state sync response to node: {}", requestNodeId);
        } catch (Exception e) {
            logger.error("Failed to send state sync response to: {}", requestNodeId, e);
        }
    }

    public void sendToNode(String nodeId, ClusterMessage clusterMsg) {
        if (!config.isEnabled() || cluster == null) {
            return;
        }
        try {
            String[] parts = nodeId.split(":");
            if (parts.length == 2) {
                Node node = new Node(parts[0], Integer.parseInt(parts[1]));
                fillMessageMeta(clusterMsg);
                byte[] data = serialize(clusterMsg);
                cluster.send(node, data);
            }
        } catch (Exception e) {
            logger.error("Failed to send message to node: {}", nodeId, e);
        }
    }

    public void broadcast(ClusterMessage clusterMsg) {
        if (!config.isEnabled() || cluster == null) {
            return;
        }
        try {
            fillMessageMeta(clusterMsg);
            byte[] data = serialize(clusterMsg);
            cluster.broadcast(data);
        } catch (Exception e) {
            logger.error("Failed to broadcast message", e);
        }
    }

    private void fillMessageMeta(ClusterMessage clusterMsg) {
        clusterMsg.setSourceNode(localNodeId);
        clusterMsg.setTimestamp(System.currentTimeMillis());
    }

    public void stop() {
        if (cluster != null) {
            // 广播节点离开消息
            GenericClusterMessage leaveMsg = new GenericClusterMessage();
            leaveMsg.setType(MessageType.NODE_LEAVE);
            leaveMsg.setSourceNode(localNodeId);
            broadcast(leaveMsg);
            
            cluster.stop();
            logger.info("Mqtt cluster manager stopped");
        }
        
        // 停止 MQTT 服务
        if (mqttServer != null) {
            mqttServer.stop();
            logger.info("MQTT Server stopped");
        }
    }

    /**
     * 集群级别的下发消息：发布消息到集群中的所有匹配订阅者
     *
     * @param topic   主题
     * @param payload 消息体
     * @param qos     QoS
     * @param retain  是否保留消息
     */
    public void publish(String topic, byte[] payload, int qos, boolean retain) {
        if (mqttServer == null) {
            return;
        }
        
        // 1. 先在本地节点下发
        mqttServer.publishAll(topic, payload, MqttQoS.valueOf(qos), retain);
        
        // 2. 查找是否有其他节点存在该 topic 的订阅者，按需转发以节省网络开销
        if (config.isEnabled() && cluster != null) {
            ClusterMqttSessionManager sessionManager = (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
            List<Subscribe> allSubs = sessionManager.searchAllSubscribe(topic);
            
            if (allSubs != null && !allSubs.isEmpty()) {
                Set<String> targetNodes = new HashSet<>();
                for (Subscribe sub : allSubs) {
                    String node = sessionManager.getClientNode(sub.getClientId());
                    if (node != null && !node.equals(localNodeId)) {
                        targetNodes.add(node);
                    }
                }
                
                if (!targetNodes.isEmpty()) {
                    PublishForwardMessage clusterMsg = new PublishForwardMessage();
                    clusterMsg.setType(MessageType.PUBLISH_FORWARD);
                    clusterMsg.setTopic(topic);
                    clusterMsg.setPayload(payload);
                    clusterMsg.setQos(qos);
                    clusterMsg.setRetain(retain);
                    
                    for (String node : targetNodes) {
                        sendToNode(node, clusterMsg);
                    }
                }
            }
        }
    }

    public Collection<Node> getRemoteMembers() {
        if (cluster == null) {
            return Collections.emptyList();
        }
        return cluster.getRemoteMembers();
    }

    private String nodeToString(Node node) {
        return node.getIp() + ":" + node.getPort();
    }

    private byte[] serialize(ClusterMessage msg) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(msg);
            return bos.toByteArray();
        }
    }

    private ClusterMessage deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return (ClusterMessage) in.readObject();
        }
    }

    public String getLocalNodeId() {
        return localNodeId;
    }
}
