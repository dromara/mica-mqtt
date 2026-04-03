package org.dromara.mica.mqtt.server.auth;

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerSubscribeValidator;
import org.springframework.context.annotation.Configuration;

/**
 * 示例自定义订阅校验，请按照自己的需求和业务进行扩展
 *
 * @author L.cm
 */
@Configuration(proxyBeanMethods = false)
public class MqttSubscribeValidator implements IMqttServerSubscribeValidator {

	@Override
	public boolean isValid(ChannelContext context, String clientId, String topicFilter, MqttQoS qoS) {
		// 校验客户端订阅的 topic，校验成功返回 true，失败返回 false
		return true;
	}

}
