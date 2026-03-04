package org.dromara.mica.mqtt.spring.client.config;

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