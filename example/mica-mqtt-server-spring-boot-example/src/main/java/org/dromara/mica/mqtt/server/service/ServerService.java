package org.dromara.mica.mqtt.server.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.spring.server.MqttServerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Slf4j
@Service
public class ServerService {
	@Autowired
	private MqttServerTemplate server;

	public boolean publish(String body) {
		boolean result = server.publishAll("/test/123", body.getBytes(StandardCharsets.UTF_8));
		log.info("Mqtt publishAll result:{}", result);
		return result;
	}
}
