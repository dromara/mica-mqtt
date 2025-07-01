package org.dromara.mica.mqtt.server.auth;

import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.springframework.context.annotation.Configuration;
import org.tio.core.ChannelContext;

/**
 * 示例 mqtt tcp、websocket 认证，请按照自己的需求和业务进行扩展
 *
 * @author L.cm
 */
@Configuration(proxyBeanMethods = false)
public class MqttAuthHandler implements IMqttServerAuthHandler {

	@Override
	public boolean authenticate(ChannelContext context, String uniqueId, String clientId, String username, String password) {
		// 客户端认证逻辑实现
		return true;
	}

}
