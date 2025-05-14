package org.dromara.mica.mqtt.client.listener;

import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 客户端消息监听，注解在方法上
 *
 * @author L.cm
 */
@Service
public class OtherMqttClientSubscribeListener {
	private static final Logger logger = LoggerFactory.getLogger(OtherMqttClientSubscribeListener.class);

	@MqttClientSubscribe(
		value = {
			"$share/iothub/test/${a}",
			"/test/${arg1}/${arg2}/${arg3}/${arg4}"
		},
		clientTemplateBean = "mqttClientTemplate1"
	)
	public void sub(String topic, MqttPublishMessage message, byte[] payload) {
		logger.info("topic:{} payload:{}", topic, new String(payload));
	}

}

