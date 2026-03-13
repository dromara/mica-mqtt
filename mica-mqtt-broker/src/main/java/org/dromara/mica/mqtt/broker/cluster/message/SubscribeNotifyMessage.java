package org.dromara.mica.mqtt.broker.cluster.message;

import org.dromara.mica.mqtt.core.server.model.Subscribe;

import java.util.List;

public class SubscribeNotifyMessage extends ClusterMessage {
    private String clientId;        // 客户端ID
    private String nodeId;          // 节点ID
    private List<Subscribe> subscriptions; // 订阅主题列表

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public List<Subscribe> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<Subscribe> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
