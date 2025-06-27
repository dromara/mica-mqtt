package org.dromara.mica.mqtt.core.server.listener;

import org.dromara.mica.mqtt.core.server.protocol.MqttProtocol;
import org.tio.core.Node;
import org.tio.core.ssl.SslConfig;

/**
 * mqtt 监听器
 *
 * @author L.cm
 */
public interface IMqttProtocolListener {
	/**
	 * 获取协议
	 *
	 * @return MqttProtocol
	 */
	MqttProtocol getProtocol();

	/**
	 * 获取 ip 断开
	 *
	 * @return ServerNode
	 */
	Node getServerNode();

	/**
	 * ssl 配置
	 *
	 * @return SslConfig
	 */
	SslConfig getSslConfig();
}
