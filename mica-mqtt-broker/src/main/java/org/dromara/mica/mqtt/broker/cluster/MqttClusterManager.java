package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.message.ClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.GenericClusterMessage;
import org.dromara.mica.mqtt.broker.cluster.message.StateSyncResponseMessage;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.Node;
import org.tio.server.cluster.core.ClusterApi;
import org.tio.server.cluster.core.ClusterConfig;
import org.tio.server.cluster.core.ClusterImpl;
import org.tio.server.cluster.message.ClusterDataMessage;
import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                    syncRequest.setType(org.dromara.mica.mqtt.broker.cluster.message.MessageType.STATE_SYNC_REQUEST);
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
        
        if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.PUBLISH_FORWARD) {
            org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage pfm = 
                (org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage) clusterMsg;
            mqttServer.publishAll(pfm.getTopic(), pfm.getPayload(), org.dromara.mica.mqtt.codec.MqttQoS.valueOf(pfm.getQos()), pfm.isRetain());
        } else if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.SUBSCRIBE_NOTIFY) {
            org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage snm = 
                (org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage) clusterMsg;
            sessionManager.syncRemoteSubscriptions(snm.getClientId(), snm.getNodeId(), snm.getSubscriptions());
        } else if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.UNSUBSCRIBE_NOTIFY) {
            org.dromara.mica.mqtt.broker.cluster.message.UnsubscribeNotifyMessage unm = 
                (org.dromara.mica.mqtt.broker.cluster.message.UnsubscribeNotifyMessage) clusterMsg;
            sessionManager.removeRemoteSubscriptions(unm.getClientId(), unm.getTopics());
        } else if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.CLIENT_CONNECT) {
            org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage ccm = 
                (org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage) clusterMsg;
            sessionManager.registerRemoteClient(ccm.getClientId(), ccm.getSourceNode());
        } else if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.CLIENT_DISCONNECT) {
            org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage cdm = 
                (org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage) clusterMsg;
            sessionManager.removeRemoteClient(cdm.getClientId());
        } else if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.STATE_SYNC_REQUEST) {
            handleStateSyncRequest(clusterMsg.getSourceNode());
        } else if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.STATE_SYNC_RESPONSE) {
            StateSyncResponseMessage ssm = (StateSyncResponseMessage) clusterMsg;
            sessionManager.syncFullState(ssm.getClientNodeMap(), ssm.getSubscriptionMap());
            logger.info("State sync completed, received {} client mappings", ssm.getClientNodeMap().size());
        } else if (clusterMsg.getType() == org.dromara.mica.mqtt.broker.cluster.message.MessageType.NODE_LEAVE) {
            // 节点离开消息，需要清理该节点的所有订阅
            String leavingNodeId = clusterMsg.getSourceNode();
            sessionManager.clearNodeClientsAndSubscriptions(leavingNodeId);
            logger.info("Node {} left cluster, cleaned up its clients and subscriptions", leavingNodeId);
        }
    }

    /**
     * 处理状态同步请求
     */
    private void handleStateSyncRequest(String requestNodeId) {
        ClusterMqttSessionManager sessionManager = (ClusterMqttSessionManager) mqttServer.getServerCreator().getSessionManager();
        
        StateSyncResponseMessage response = new StateSyncResponseMessage();
        response.setType(org.dromara.mica.mqtt.broker.cluster.message.MessageType.STATE_SYNC_RESPONSE);
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
                clusterMsg.setSourceNode(localNodeId);
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
            clusterMsg.setSourceNode(localNodeId);
            byte[] data = serialize(clusterMsg);
            cluster.broadcast(data);
        } catch (Exception e) {
            logger.error("Failed to broadcast message", e);
        }
    }

    public void stop() {
        if (cluster != null) {
            // 广播节点离开消息
            GenericClusterMessage leaveMsg = new GenericClusterMessage();
            leaveMsg.setType(org.dromara.mica.mqtt.broker.cluster.message.MessageType.NODE_LEAVE);
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

    public Collection<Node> getRemoteMembers() {
        if (cluster == null) {
            return java.util.Collections.emptyList();
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
