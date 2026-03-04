package org.dromara.mica.mqtt.client.solon.config;

/**
 * mqtt client properties customizer
 *
 * @author L.cm
 */
@FunctionalInterface
public interface MqttClientPropertiesCustomizer {

	/**
	 * 自定义 MqttClientProperties
	 *
	 * @param properties MqttClientProperties
	 */
	void customize(MqttClientProperties properties);

}