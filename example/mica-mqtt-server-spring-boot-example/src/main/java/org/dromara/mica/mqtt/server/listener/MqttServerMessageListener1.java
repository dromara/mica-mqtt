package org.dromara.mica.mqtt.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.spring.server.MqttServerTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.tio.core.ChannelContext;

import java.nio.charset.StandardCharsets;

/**
 * 消息监听器示例1，直接实现 IMqttMessageListener，注意：如果实现了 IMqttMessageListener，MqttServerFunction 注解就不生效了。
 *
 * @author wsq
 */
@Slf4j
//@Service
public class MqttServerMessageListener1 implements IMqttMessageListener, SmartInitializingSingleton {
	@Autowired
	private ApplicationContext applicationContext;
	private MqttServerTemplate mqttServerTemplate;

	@Override
	public void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qos, MqttPublishMessage message) {
		log.info("context:{} clientId:{} message:{} payload:{}", context, clientId, message, new String(message.payload(), StandardCharsets.UTF_8));
	}

	@Override
	public void afterSingletonsInstantiated() {
		// 单利 bean 初始化完成之后从 ApplicationContext 中获取 bean
		mqttServerTemplate = applicationContext.getBean(MqttServerTemplate.class);
	}
}
