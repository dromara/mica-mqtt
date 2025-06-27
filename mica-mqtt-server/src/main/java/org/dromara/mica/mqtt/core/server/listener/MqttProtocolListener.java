package org.dromara.mica.mqtt.core.server.listener;

import org.dromara.mica.mqtt.core.server.protocol.MqttProtocol;
import org.tio.core.Node;
import org.tio.core.ssl.SslConfig;

/**
 * mqtt 协议监听器
 *
 * @author L.cm
 */
public class MqttProtocolListener implements IMqttProtocolListener {
	private final MqttProtocol protocol;
	private final Node serverNode;
	private final SslConfig sslConfig;

	public MqttProtocolListener(MqttProtocol protocol, Node serverNode) {
		this(protocol, serverNode, null);
	}

	public MqttProtocolListener(MqttProtocol protocol, Node serverNode, SslConfig sslConfig) {
		this.protocol = checkSSl(protocol, sslConfig);
		this.serverNode = serverNode;
		this.sslConfig = sslConfig;
	}

	private static MqttProtocol checkSSl(MqttProtocol mqttProtocol, SslConfig sslConfig) {
		if ((MqttProtocol.MQTT_SSL == mqttProtocol || MqttProtocol.MQTT_WSS == mqttProtocol) && sslConfig == null) {
			throw new NullPointerException(mqttProtocol + " 需要配置 SSLContext");
		} else {
			return mqttProtocol;
		}
	}

	@Override
	public MqttProtocol getProtocol() {
		return this.protocol;
	}

	@Override
	public Node getServerNode() {
		return this.serverNode;
	}

	@Override
	public SslConfig getSslConfig() {
		return this.sslConfig;
	}
}
