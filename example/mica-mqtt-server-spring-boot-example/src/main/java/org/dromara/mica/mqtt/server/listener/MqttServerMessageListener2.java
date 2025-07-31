package org.dromara.mica.mqtt.server.listener;

import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.spring.server.MqttServerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.tio.core.ChannelContext;

import java.nio.charset.StandardCharsets;

/**
 * 消息监听器示例1，直接实现 IMqttMessageListener
 *
 * @author wsq
 */
@Service
public class MqttServerMessageListener2 implements IMqttMessageListener, SmartInitializingSingleton {
	private static final Logger logger = LoggerFactory.getLogger(MqttServerMessageListener2.class);
	@Autowired
	private ApplicationContext applicationContext;
	private MqttServerTemplate mqttServerTemplate;

	@Override
	public void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qos, MqttPublishMessage message) {
		logger.info("context:{} clientId:{} message:{} payload:{}", context, clientId, message, new String(message.payload(), StandardCharsets.UTF_8));
	}

	@Override
	public void afterSingletonsInstantiated() {
		// 单利 bean 初始化完成之后从 ApplicationContext 中获取 bean
		mqttServerTemplate = applicationContext.getBean(MqttServerTemplate.class);
	}
}
