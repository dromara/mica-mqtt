package org.dromara.mica.mqtt.client.config;

import org.dromara.mica.mqtt.core.client.DefaultMqttClientSession;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 示例多个 mqtt client
 *
 * @author L.cm
 */
@Configuration
public class OtherMqttClientConfiguration {

	@Bean("mqttClientTemplate1")
	public MqttClientTemplate mqttClientTemplate1() {
		// 基于 clientCreator 的配置构建一个新的
		MqttClientCreator mqttClientCreator1 = new MqttClientCreator()
			// 修改不同的配置
//			.ip("mqtt.dreamlu.net")
			.port(1884)
			.username("mica")
			.password("mica")
			// 避免 client session 冲突
			.clientSession(new DefaultMqttClientSession());
		return new MqttClientTemplate(mqttClientCreator1);
	}

}
