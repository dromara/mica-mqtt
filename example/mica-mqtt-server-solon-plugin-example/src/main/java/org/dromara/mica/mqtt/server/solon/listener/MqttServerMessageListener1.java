package org.dromara.mica.mqtt.server.solon.listener;

import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;

import java.nio.charset.StandardCharsets;

/**
 * 消息监听器示例1，直接实现 IMqttMessageListener
 *
 * @author wsq
 */
@Component
public class MqttServerMessageListener1 implements IMqttMessageListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttServerMessageListener1.class);

	@Override
	public void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qos, MqttPublishMessage message) {
		logger.info("context:{} clientId:{} message:{} payload:{}", context, clientId, message, new String(message.payload(), StandardCharsets.UTF_8));
	}

}
