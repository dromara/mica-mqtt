package org.dromara.mica.mqtt.core.server.listener;

import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.tio.server.TioServer;
import org.tio.server.TioServerConfig;

import java.util.ArrayList;
import java.util.List;

public class MqttProtocolListeners {
	private final List<TioServer> servers;

	MqttProtocolListeners(List<TioServer> servers) {
		this.servers = servers;
	}

	private static List<TioServer> buildListeners(MqttServerCreator serverCreator,
												  TioServerConfig mqttServerConfig,
												  List<IMqttProtocolListener> listeners) {
		List<TioServer> servers = new ArrayList<>();


		return servers;
	}

}
