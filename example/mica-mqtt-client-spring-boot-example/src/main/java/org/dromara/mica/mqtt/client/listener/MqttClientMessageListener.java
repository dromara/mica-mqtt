package org.dromara.mica.mqtt.client.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribe;
import org.springframework.stereotype.Service;
import org.tio.core.ChannelContext;

import java.nio.charset.StandardCharsets;

/**
 * 客户端消息监听的另一种方式
 *
 * @author L.cm
 */
@Slf4j
@Service
@MqttClientSubscribe("${topic1}")
public class MqttClientMessageListener implements IMqttClientMessageListener {

	@Override
	public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		log.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}
}

