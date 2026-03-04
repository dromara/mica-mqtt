package org.dromara.mica.mqtt.server.solon.config;

/**
 * mqtt server properties customizer
 *
 * @author L.cm
 */
@FunctionalInterface
public interface MqttServerPropertiesCustomizer {

	/**
	 * 自定义 MqttServerProperties
	 *
	 * @param properties MqttServerProperties
	 */
	void customize(MqttServerProperties properties);

}