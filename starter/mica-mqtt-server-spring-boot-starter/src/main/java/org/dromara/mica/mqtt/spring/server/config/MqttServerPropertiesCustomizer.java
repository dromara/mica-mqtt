package org.dromara.mica.mqtt.spring.server.config;

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