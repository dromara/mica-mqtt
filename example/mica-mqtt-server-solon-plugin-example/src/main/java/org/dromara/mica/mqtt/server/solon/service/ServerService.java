package org.dromara.mica.mqtt.server.solon.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.server.solon.MqttServerTemplate;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Slf4j
@Component
public class ServerService {
	@Inject
	private MqttServerTemplate server;

	public boolean publish(String body) {
		boolean result = server.publishAll("/test/123", body.getBytes(StandardCharsets.UTF_8));
		log.info("Mqtt publishAll result:{}", result);
		return result;
	}
}
