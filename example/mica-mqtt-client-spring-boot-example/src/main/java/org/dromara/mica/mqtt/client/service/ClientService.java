package org.dromara.mica.mqtt.client.service;

import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Service
public class ClientService {
	private static final Logger logger = LoggerFactory.getLogger(ClientService.class);
	/**
	 * 使用 默认的 mqtt client
	 */
	@Autowired
	@Qualifier(MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN)
	private MqttClientTemplate client;

	public boolean publish(String body) {
		client.publish("/test/client", body.getBytes(StandardCharsets.UTF_8));
		return true;
	}

	public boolean sub() {
		client.subQos0("/test/#", (context, topic, message, payload) -> {
			logger.info("{}\t{}", topic, new String(payload, StandardCharsets.UTF_8));
		});
		return true;
	}

}
