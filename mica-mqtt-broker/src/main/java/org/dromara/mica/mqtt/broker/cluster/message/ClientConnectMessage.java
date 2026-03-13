package org.dromara.mica.mqtt.broker.cluster.message;

public class ClientConnectMessage extends ClusterMessage {
    private String clientId;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
