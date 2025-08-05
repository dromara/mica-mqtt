package org.dromara.mica.mqtt.server.solon.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.tio.core.ChannelContext;

import java.nio.charset.StandardCharsets;

/**
 * 消息监听器示例1，直接实现 IMqttMessageListener，注意：如果实现了 IMqttMessageListener，MqttServerFunction 注解就不生效了。
 *
 * @author wsq
 */
@Slf4j
//@Component
public class MqttServerMessageListener1 implements IMqttMessageListener {

	@Override
	public void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qos, MqttPublishMessage message) {
		log.info("context:{} clientId:{} message:{} payload:{}", context, clientId, message, new String(message.payload(), StandardCharsets.UTF_8));
	}

}
