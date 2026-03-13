package org.dromara.mica.mqtt.broker.cluster.message;

import org.dromara.mica.mqtt.core.server.model.Subscribe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态同步响应消息，包含节点上的所有订阅信息
 */
public class StateSyncResponseMessage extends ClusterMessage {
    // clientId -> nodeId
    private Map<String, String> clientNodeMap = new HashMap<>();
    // clientId -> subscriptions
    private Map<String, List<Subscribe>> subscriptionMap = new HashMap<>();

    public Map<String, String> getClientNodeMap() {
        return clientNodeMap;
    }

    public void setClientNodeMap(Map<String, String> clientNodeMap) {
        this.clientNodeMap = clientNodeMap;
    }

    public Map<String, List<Subscribe>> getSubscriptionMap() {
        return subscriptionMap;
    }

    public void setSubscriptionMap(Map<String, List<Subscribe>> subscriptionMap) {
        this.subscriptionMap = subscriptionMap;
    }
}
