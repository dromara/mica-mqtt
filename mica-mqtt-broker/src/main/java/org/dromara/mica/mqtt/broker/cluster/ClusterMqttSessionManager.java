package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.message.SubscribeNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.message.UnsubscribeNotifyMessage;
import org.dromara.mica.mqtt.broker.cluster.message.MessageType;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 集群 Session 管理器，装饰已有的 IMqttSessionManager
 */
public class ClusterMqttSessionManager implements IMqttSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(ClusterMqttSessionManager.class);
    private final IMqttSessionManager delegate;
    private final MqttClusterManager clusterManager;
    private final ConcurrentHashMap<String, String> clientNodeMap = new ConcurrentHashMap<>();

    public ClusterMqttSessionManager(IMqttSessionManager delegate, MqttClusterManager clusterManager) {
        this.delegate = delegate;
        this.clusterManager = clusterManager;
    }

    public String getClientNode(String clientId) {
        return clientNodeMap.get(clientId);
    }

    public void registerRemoteClient(String clientId, String nodeId) {
        clientNodeMap.put(clientId, nodeId);
        logger.info("[Cluster] Registered remote client: {} -> node: {}", clientId, nodeId);
    }

    public void removeRemoteClient(String clientId) {
        String node = clientNodeMap.remove(clientId);
        delegate.remove(clientId);
        logger.info("[Cluster] Removed remote client: {} from node: {}", clientId, node);
    }

    public void clearNodeClientsAndSubscriptions(String nodeId) {
        clientNodeMap.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(nodeId)) {
                delegate.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void syncRemoteSubscriptions(String clientId, String nodeId, List<Subscribe> subscriptions) {
        registerRemoteClient(clientId, nodeId);
        for (Subscribe sub : subscriptions) {
            delegate.addSubscribe(new TopicFilter(sub.getTopicFilter()), clientId, sub.getMqttQoS(), sub.isNoLocal());
            logger.info("[Cluster] Synced remote subscription: client={}, topic={}, node={}", 
                clientId, sub.getTopicFilter(), nodeId);
        }
    }

    public void removeRemoteSubscriptions(String clientId, List<String> topics) {
        for (String topic : topics) {
            delegate.removeSubscribe(topic, clientId);
            logger.info("[Cluster] Removed remote subscription: client={}, topic={}", clientId, topic);
        }
    }

    @Override
    public void addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal) {
        delegate.addSubscribe(topicFilter, clientId, mqttQoS, noLocal);

        Subscribe subscribe = new Subscribe(topicFilter.getTopic(), clientId, mqttQoS, noLocal);
        SubscribeNotifyMessage notifyMessage = new SubscribeNotifyMessage();
        notifyMessage.setType(MessageType.SUBSCRIBE_NOTIFY);
        notifyMessage.setClientId(clientId);
        notifyMessage.setNodeId(clusterManager.getLocalNodeId());
        notifyMessage.setSubscriptions(java.util.Collections.singletonList(subscribe));
        
        logger.info("[Cluster] Broadcasting subscription: client={}, topic={}, node={}", 
            clientId, topicFilter.getTopic(), clusterManager.getLocalNodeId());
        
        clusterManager.broadcast(notifyMessage);
    }

    @Override
    public void removeSubscribe(String topicFilter, String clientId) {
        delegate.removeSubscribe(topicFilter, clientId);
        UnsubscribeNotifyMessage notifyMessage = new UnsubscribeNotifyMessage();
        notifyMessage.setType(MessageType.UNSUBSCRIBE_NOTIFY);
        notifyMessage.setClientId(clientId);
        notifyMessage.setNodeId(clusterManager.getLocalNodeId());
        notifyMessage.setTopics(java.util.Collections.singletonList(topicFilter));
        clusterManager.broadcast(notifyMessage);
    }

    @Override
    public Byte searchSubscribe(String topicName, String clientId) {
        return delegate.searchSubscribe(topicName, clientId);
    }

    @Override
    public List<Subscribe> searchSubscribe(String topic) {
        // 仅返回本地订阅
        List<Subscribe> allSubscribers = delegate.searchSubscribe(topic);
        if (allSubscribers == null || allSubscribers.isEmpty()) {
            return allSubscribers;
        }
        return allSubscribers.stream().filter(sub -> {
            String node = clientNodeMap.get(sub.getClientId());
            return node == null || node.equals(clusterManager.getLocalNodeId());
        }).collect(Collectors.toList());
    }

    /**
     * 获取包括远程节点在内的所有订阅
     */
    public List<Subscribe> searchAllSubscribe(String topic) {
        return delegate.searchSubscribe(topic);
    }

    /**
     * 获取客户端的订阅（从原始 sessionManager）
     */
    public List<Subscribe> getClientSubscriptions(String clientId) {
        return delegate.getSubscriptions(clientId);
    }

    /**
     * 获取所有远程客户端映射（用于状态同步）
     */
    public Map<String, String> getRemoteClientNodeMap() {
        return new java.util.HashMap<>(clientNodeMap);
    }

    /**
     * 全量同步状态（用于新节点加入）
     */
    public void syncFullState(Map<String, String> clientNodeMap, Map<String, List<Subscribe>> subscriptionMap) {
        for (Map.Entry<String, String> entry : clientNodeMap.entrySet()) {
            this.clientNodeMap.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<Subscribe>> entry : subscriptionMap.entrySet()) {
            for (Subscribe sub : entry.getValue()) {
                delegate.addSubscribe(new TopicFilter(sub.getTopicFilter()), entry.getKey(), sub.getMqttQoS(), sub.isNoLocal());
            }
        }
    }

    @Override
    public List<Subscribe> getSubscriptions(String clientId) {
        return delegate.getSubscriptions(clientId);
    }

    @Override
    public void addPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish) {
        delegate.addPendingPublish(clientId, messageId, pendingPublish);
    }

    @Override
    public MqttPendingPublish getPendingPublish(String clientId, int messageId) {
        return delegate.getPendingPublish(clientId, messageId);
    }

    @Override
    public void removePendingPublish(String clientId, int messageId) {
        delegate.removePendingPublish(clientId, messageId);
    }

    @Override
    public void addPendingQos2Publish(String clientId, int messageId, MqttPendingQos2Publish pendingQos2Publish) {
        delegate.addPendingQos2Publish(clientId, messageId, pendingQos2Publish);
    }

    @Override
    public MqttPendingQos2Publish getPendingQos2Publish(String clientId, int messageId) {
        return delegate.getPendingQos2Publish(clientId, messageId);
    }

    @Override
    public void removePendingQos2Publish(String clientId, int messageId) {
        delegate.removePendingQos2Publish(clientId, messageId);
    }

    @Override
    public int getPacketId(String clientId) {
        return delegate.getPacketId(clientId);
    }

    @Override
    public boolean hasSession(String clientId) {
        return delegate.hasSession(clientId) || clientNodeMap.containsKey(clientId);
    }

    @Override
    public boolean expire(String clientId, int sessionExpirySeconds) {
        return delegate.expire(clientId, sessionExpirySeconds);
    }

    @Override
    public boolean active(String clientId) {
        return delegate.active(clientId);
    }

    @Override
    public void remove(String clientId) {
        delegate.remove(clientId);
        clientNodeMap.remove(clientId);
    }

    @Override
    public void clean() {
        delegate.clean();
        clientNodeMap.clear();
    }
}
