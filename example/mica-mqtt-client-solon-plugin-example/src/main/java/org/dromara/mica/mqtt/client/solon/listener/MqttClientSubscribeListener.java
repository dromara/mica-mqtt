package org.dromara.mica.mqtt.client.solon.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.client.solon.MqttClientSubscribe;
import org.dromara.mica.mqtt.client.solon.pojo.User;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.deserialize.MqttJsonDeserializer;
import org.noear.solon.annotation.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 客户端消息监听
 *
 * @author L.cm
 */
@Slf4j
@Component
public class MqttClientSubscribeListener {

	@MqttClientSubscribe("/test/#")
	public void subQos0(String topic, byte[] payload) {
		log.info("MqttClientSubscribeListener.subQos0,topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe(value = "/qos1/#", qos = MqttQoS.QOS1)
	public void subQos1(String topic, byte[] payload) {
		log.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
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
		log.info("topic:{} json data:{}", topic, data);
	}

	@MqttClientSubscribe(value = "/test/object")
	public void testJson(String topic, MqttPublishMessage message, User<User> data) {
		log.info("topic:{} json data:{}", topic, data);
	}

	/**
	 * 订阅，参数为可选，但是参数数量必须大于 2，
	 *
	 * @param topic topic 参数，可选参数
	 * @param topicVars 订阅 topic 模板 ${productKey} 中的变量解析（v2.5.4支持），可选参数，注意：类型必须为 Map<String, String>
	 * @param payload 消息内容
	 */
	@MqttClientSubscribe("/sys/${productKey}/${deviceName}/thing/sub/register")
	public void thingSubRegister(String topic, Map<String, String> topicVars, byte[] payload) {
		// 1.3.8 开始支持，@MqttClientSubscribe 注解支持 ${} 变量替换，会默认替换成 +
		// 注意：mica-mqtt 会先从 Spring boot 配置中替换参数 ${}，如果存在配置会优先被替换。
		log.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

}

