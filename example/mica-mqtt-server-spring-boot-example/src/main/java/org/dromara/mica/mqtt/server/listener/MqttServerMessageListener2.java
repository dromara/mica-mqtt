package org.dromara.mica.mqtt.server.listener;

import org.dromara.mica.mqtt.server.pojo.User;
import org.dromara.mica.mqtt.spring.server.MqttServerFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 消息监听器示例2，MqttServerFunction 注解订阅，注意：如果自行实现了 IMqttMessageListener，MqttServerFunction 注解就不生效了。
 *
 * @author wsq
 */
@Service
public class MqttServerMessageListener2 {
	private static final Logger logger = LoggerFactory.getLogger(MqttServerMessageListener2.class);

	@MqttServerFunction("/test/object")
	public void func1(String topic, User<?> user) {
		logger.info("topic:{} user:{}", topic, user);
	}

	@MqttServerFunction("/test/client")
	public void func1(String topic, byte[] message) {
		logger.info("topic:{} message:{}", topic, new String(message));
	}

}
