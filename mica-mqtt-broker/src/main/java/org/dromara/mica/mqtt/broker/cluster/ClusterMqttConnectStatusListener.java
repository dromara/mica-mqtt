package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.MessageType;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.tio.core.ChannelContext;

public class ClusterMqttConnectStatusListener implements IMqttConnectStatusListener {

    private final IMqttConnectStatusListener delegate;
    private final MqttClusterManager clusterManager;

    public ClusterMqttConnectStatusListener(IMqttConnectStatusListener delegate, MqttClusterManager clusterManager) {
        this.delegate = delegate;
        this.clusterManager = clusterManager;
    }

    @Override
    public void online(ChannelContext context, String clientId, String username) {
        if (delegate != null) {
            delegate.online(context, clientId, username);
        }

        ClientConnectMessage msg = new ClientConnectMessage();
        msg.setType(MessageType.CLIENT_CONNECT);
        msg.setClientId(clientId);
        clusterManager.broadcast(msg);
    }

    @Override
    public void offline(ChannelContext context, String clientId, String username, String reason) {
        if (delegate != null) {
            delegate.offline(context, clientId, username, reason);
        }

        ClientDisconnectMessage msg = new ClientDisconnectMessage();
        msg.setType(MessageType.CLIENT_DISCONNECT);
        msg.setClientId(clientId);
        clusterManager.broadcast(msg);
    }
}
