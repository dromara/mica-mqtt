package org.dromara.mica.mqtt.broker.cluster.message;

import java.util.List;

public class UnsubscribeNotifyMessage extends ClusterMessage {
    private String clientId;
    private String nodeId;
    private List<String> topics;

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

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }
}
