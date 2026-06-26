package org.dromara.mica.mqtt.server.solon.auth;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.net.http.common.HttpRequest;
import net.dreamlu.mica.net.http.common.HttpResponse;
import net.dreamlu.mica.net.http.common.RequestLine;
import net.dreamlu.mica.net.http.common.router.HttpFilter;
import net.dreamlu.mica.net.http.common.router.HttpFilterChain;
import org.noear.solon.annotation.Configuration;

/**
 * 示例自定义 mqtt http 接口认证，请按照自己的需求和业务进行扩展
 *
 * @author L.cm
 */
@Slf4j
@Configuration
public class MqttHttpAuthFilter implements HttpFilter {

	@Override
	public HttpResponse doFilter(HttpRequest request, HttpFilterChain chain) throws Exception {
		// 注意：这里只是示例，全放行了
		RequestLine requestLine = request.getRequestLine();
		log.warn("示例 http 认证 requestLine:{} AuthFilter", requestLine);
		return chain.doFilter(request);
	}
}
