package org.dromara.mica.mqtt.client.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribe;
import org.springframework.stereotype.Service;

/**
 * 客户端消息监听，注解在方法上
 *
 * @author L.cm
 */
@Slf4j
@Service
public class OtherMqttClientSubscribeListener {

	@MqttClientSubscribe(
		value = {
			"$share/iothub/test/${a}",
			"/test/${arg1}/${arg2}/${arg3}/${arg4}"
		},
		clientTemplateBean = "mqttClientTemplate1"
	)
	public void sub(String topic, MqttPublishMessage message, byte[] payload) {
		log.info("topic:{} payload:{}", topic, new String(payload));
	}

}

