package org.dromara.mica.mqtt.server.auth;

import net.dreamlu.mica.net.http.common.HttpRequest;
import net.dreamlu.mica.net.http.common.HttpResponse;
import org.dromara.mica.mqtt.core.server.http.api.code.ResultCode;
import org.dromara.mica.mqtt.core.server.http.api.result.Result;
import org.dromara.mica.mqtt.core.server.http.handler.HttpFilter;
import org.dromara.mica.mqtt.core.server.http.handler.MqttHttpRoutes;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

/**
 * 示例自定义 mqtt http 接口认证，请按照自己的需求和业务进行扩展
 *
 * @author L.cm
 */
@Configuration(proxyBeanMethods = false)
public class MqttHttpAuthFilter implements HttpFilter, InitializingBean {

	@Override
	public boolean filter(HttpRequest request) throws Exception {
		// 自行实现逻辑
		return true;
	}

	@Override
	public HttpResponse response(HttpRequest request) {
		// 认证不通过时的响应
		return Result.fail(request, ResultCode.E103);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		MqttHttpRoutes.addFilter(this);
	}
}
