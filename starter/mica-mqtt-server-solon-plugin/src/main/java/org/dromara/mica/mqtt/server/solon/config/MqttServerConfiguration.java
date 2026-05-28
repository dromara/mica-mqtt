/* Copyright (c) 2022 Peigen.info. All rights reserved. */

package org.dromara.mica.mqtt.server.solon.config;

import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.deserialize.MqttJsonDeserializer;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionMessageListener;
import org.dromara.mica.mqtt.server.solon.event.SolonEventMqttConnectStatusListener;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;

/**
 * <b>(MqttServerConfiguration)</b>
 *
 * @author LiHai
 * @version 1.0.0
 * @since 2023/7/20
 */
@Configuration
public class MqttServerConfiguration {

	@Bean
	@Condition(onMissingBean = MqttDeserializer.class)
	public MqttDeserializer mqttDeserializer() {
		return new MqttJsonDeserializer();
	}

	@Bean
	@Condition(onMissingBean = IMqttConnectStatusListener.class)
	public IMqttConnectStatusListener connectStatusListener() {
		return new SolonEventMqttConnectStatusListener();
	}

	@Bean
	@Condition(onMissingBean = MqttFunctionManager.class)
	public MqttFunctionManager mqttFunctionManager() {
		return new MqttFunctionManager();
	}

	@Bean
	@Condition(onMissingBean = IMqttMessageListener.class)
	public IMqttMessageListener mqttFunctionMessageListener(MqttFunctionManager mqttFunctionManager) {
		return new MqttFunctionMessageListener(mqttFunctionManager);
	}

}
