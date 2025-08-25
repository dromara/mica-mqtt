package org.dromara.mica.mqtt.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.server.pojo.User;
import org.dromara.mica.mqtt.core.annotation.MqttServerFunction;
import org.springframework.stereotype.Service;
import org.tio.core.ChannelContext;
import org.tio.core.Node;

import java.util.Map;

/**
 * 消息监听器示例2，MqttServerFunction 注解订阅，注意：如果自行实现了 IMqttMessageListener，MqttServerFunction 注解就不生效了。
 *
 * @author wsq
 */
@Slf4j
@Service
public class MqttServerMessageListener2 {

	/**
	 * MQTT消息处理函数
	 *
	 * @param topic mqtt Topic
	 * @param user  订阅消息的负载内容，默认 json 序列化
	 */
	@MqttServerFunction("/test/object")
	public void func1(String topic, User<?> user) {
		log.info("topic:{} user:{}", topic, user);
	}

	@MqttServerFunction("/test/client")
	public void func2(String topic, byte[] message) {
		log.info("topic:{} message:{}", topic, new String(message));
	}

	/**
	 * MQTT消息处理函数，匹配 mqtt Topic /test/+，如何需要匹配所以消息，请使用通配符 #
	 *
	 * @param context        ChannelContext，可选参数
	 * @param topic          实际接收到消息的主题名称，可选参数
	 * @param topicVars      topic 中的  ${xxxx} 变量解析（v2.5.4支持），可选参数，注意：类型必须为 Map<String, String>
	 * @param publishMessage 完整的MQTT发布消息对象，包含消息头和负载，可选参数
	 * @param message        消息负载内容，以字节数组形式提供，可选参数，也可支持对象形式，默认 json 序列化
	 */
	@MqttServerFunction("/test/${xxxx}")
	public void func3(ChannelContext context, String topic, Map<String, String> topicVars, MqttPublishMessage publishMessage, byte[] message) {
		// 获取客户端节点信息
		Node clientNode = context.getClientNode();
		// 记录接收到的MQTT消息信息
		log.info("clientNode:{} topic:{} topicVars:{} publishMessage:{} message:{}", clientNode, topic, topicVars, publishMessage, new String(message));
	}

}
