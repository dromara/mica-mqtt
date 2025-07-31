package org.dromara.mica.mqtt.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.server.pojo.User;
import org.dromara.mica.mqtt.spring.server.MqttServerFunction;
import org.springframework.stereotype.Service;

/**
 * 消息监听器示例2，MqttServerFunction 注解订阅，注意：如果自行实现了 IMqttMessageListener，MqttServerFunction 注解就不生效了。
 *
 * @author wsq
 */
@Slf4j
@Service
public class MqttServerMessageListener2 {

	@MqttServerFunction("/test/object")
	public void func1(String topic, User<?> user) {
		log.info("topic:{} user:{}", topic, user);
	}

	@MqttServerFunction("/test/client")
	public void func1(String topic, byte[] message) {
		log.info("topic:{} message:{}", topic, new String(message));
	}

}
