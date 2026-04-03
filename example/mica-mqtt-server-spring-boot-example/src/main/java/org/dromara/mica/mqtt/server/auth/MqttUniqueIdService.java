package org.dromara.mica.mqtt.server.auth;

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.springframework.context.annotation.Configuration;

/**
 * 示例自定义 clientId，请按照自己的需求和业务进行扩展
 *
 * @author L.cm
 */
@Configuration(proxyBeanMethods = false)
public class MqttUniqueIdService implements IMqttServerUniqueIdService {

	@Override
	public String getUniqueId(ChannelContext context, String clientId, String userName, String password) {
		// 返回的 uniqueId 会替代 mqtt client 传过来的 clientId，请保证返回的 uniqueId 唯一。
		return clientId;
	}

}
