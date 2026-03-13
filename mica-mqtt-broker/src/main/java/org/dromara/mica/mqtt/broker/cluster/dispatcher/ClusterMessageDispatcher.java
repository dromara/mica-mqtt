package org.dromara.mica.mqtt.broker.cluster.dispatcher;

import org.dromara.mica.mqtt.broker.cluster.ClusterMqttSessionManager;
import org.dromara.mica.mqtt.broker.cluster.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.message.PublishForwardMessage;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.message.BaseMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClusterMessageDispatcher extends BaseMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClusterMessageDispatcher.class);
    private final MqttClusterManager clusterManager;
    private final ClusterMqttSessionManager clusterSessionManager;

    public ClusterMessageDispatcher(MqttServer mqttServer, MqttClusterManager clusterManager, ClusterMqttSessionManager clusterSessionManager) {
        super(mqttServer);
        this.clusterManager = clusterManager;
        this.clusterSessionManager = clusterSessionManager;
    }

    @Override
    public boolean handle(Message message) {
        if (org.dromara.mica.mqtt.core.server.enums.MessageType.UP_STREAM != message.getMessageType()) {
            return true;
        }

        String topic = message.getTopic();
        String localNodeId = clusterManager.getLocalNodeId();
        
        // 获取所有订阅者（包括远程）
        List<Subscribe> subscribers = clusterSessionManager.searchAllSubscribe(topic);
        
        logger.info("[Cluster] Received publish on topic: {}, subscribers count: {}", topic, 
            subscribers == null ? 0 : subscribers.size());

        if (subscribers == null || subscribers.isEmpty()) {
            logger.info("[Cluster] No subscribers for topic: {}, skip forwarding", topic);
            return true;
        }

        // 找出需要转发的远程节点
        Set<String> remoteNodes = new HashSet<>();
        for (Subscribe sub : subscribers) {
            String clientId = sub.getClientId();
            String node = clusterSessionManager.getClientNode(clientId);
            logger.debug("[Cluster] Client: {}, Node: {}", clientId, node);
            
            if (node != null && !node.equals(localNodeId)) {
                remoteNodes.add(node);
                logger.info("[Cluster] Will forward to node: {} for client: {}", node, clientId);
            }
        }

        // 向每个远程节点转发消息（O(1) 网络开销）
        if (!remoteNodes.isEmpty()) {
            logger.info("[Cluster] Forwarding message on topic: {} to remote nodes: {}", topic, remoteNodes);
            for (String nodeId : remoteNodes) {
                forwardToNode(nodeId, message);
            }
        } else {
            logger.info("[Cluster] No remote nodes need forwarding for topic: {}", topic);
        }

        return true; // 继续执行 UpStreamMessageHandler 进行本地分发
    }

    private void forwardToNode(String nodeId, Message msg) {
        logger.info("[Cluster] Forwarding to node: {}, topic: {}", nodeId, msg.getTopic());
        
        PublishForwardMessage clusterMsg = new PublishForwardMessage();
        clusterMsg.setType(org.dromara.mica.mqtt.broker.cluster.message.MessageType.PUBLISH_FORWARD);
        clusterMsg.setTopic(msg.getTopic());
        clusterMsg.setPayload(msg.getPayload());
        clusterMsg.setQos(msg.getQos());
        clusterMsg.setRetain(msg.isRetain());

        clusterManager.sendToNode(nodeId, clusterMsg);
    }

    @Override
    public int getOrder() {
        return 90; // 在 UpStreamMessageHandler (100) 之前执行
    }
}
