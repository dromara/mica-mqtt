package org.dromara.mica.mqtt.client.listener;

import org.dromara.mica.mqtt.client.pojo.User;
import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.spring.client.MqttClientSubscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 客户端消息监听
 *
 * @author L.cm
 * @author ChangJin Wei(魏昌进)
 */
@Service
public class MqttClientSubscribeListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientSubscribeListener.class);

	@MqttClientSubscribe("/test/#")
	public void subQos0(String topic, byte[] payload) {
		logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe(value = "/qos1/#", qos = MqttQoS.QOS1)
	public void subQos1(String topic, byte[] payload) {
		logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe(value = "/test/json")
	public void testJson(String topic, MqttPublishMessage message, Map<String, Object> data) {
		logger.info("topic:{} json data:{}", topic, data);
	}

	@MqttClientSubscribe(value = "/test/object")
	public void testJson(String topic, MqttPublishMessage message, User<User> data) {
		logger.info("topic:{} json data:{}", topic, data);
	}

	@MqttClientSubscribe("/sys/${productKey}/${deviceName}/thing/sub/register")
	public void thingSubRegister(String topic, byte[] payload) {
		// 1.3.8 开始支持，@MqttClientSubscribe 注解支持 ${} 变量替换，会默认替换成 +
		// 注意：mica-mqtt 会先从 Spring boot 配置中替换参数 ${}，如果存在配置会优先被替换。
		logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}


}

