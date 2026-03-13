package org.dromara.mica.mqtt.broker.cluster.message;

public class ClientDisconnectMessage extends ClusterMessage {
    private String clientId;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
