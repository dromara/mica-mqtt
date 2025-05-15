package org.dromara.mica.mqtt.client.solon.listener;

import org.dromara.mica.mqtt.client.solon.MqttClientSubscribe;
import org.dromara.mica.mqtt.client.solon.pojo.User;
import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.deserialize.MqttJsonDeserializer;
import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 客户端消息监听
 *
 * @author L.cm
 */
@Component
public class MqttClientSubscribeListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientSubscribeListener.class);

	@MqttClientSubscribe("/test/#")
	public void subQos0(String topic, byte[] payload) {
		logger.info("MqttClientSubscribeListener.subQos0,topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe(value = "/qos1/#", qos = MqttQoS.QOS1)
	public void subQos1(String topic, byte[] payload) {
		logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe(
		value = "/test/json",
		deserialize = MqttJsonDeserializer.class // 2.4.5 开始支持 自定义序列化，默认 json 序列化
	)
	public void testJson(String topic, MqttPublishMessage message, Map<String, Object> data) {
		// solon 插件为 2.4.6 开始支持，支持 2 到 3 个参数，字段类型映射规则如下
		// String 字符串会默认映射到 topic，
		// MqttPublishMessage 会默认映射到 原始的消息，可以拿到 mqtt5 的 props 参数
		// byte[] 会映射到 mqtt 消息内容 payload
		// ByteBuffer 会映射到 mqtt 消息内容 payload
		// 其他类型会走序列化，确保消息能够序列化，默认为 json 序列化
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

