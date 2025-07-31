package org.dromara.mica.mqtt.client.solon.service;


import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.client.solon.MqttClientTemplate;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Slf4j
@Component
public class ClientService {
	@Inject
	private              MqttClientTemplate client;

	public boolean publish(String body) {
		client.publish("/test/client", body.getBytes(StandardCharsets.UTF_8));
		return true;
	}

	public boolean sub() {
		client.subQos0("/test/#", (context, topic, message, payload) -> {
			log.info("{}\t{}", topic, new String(payload, StandardCharsets.UTF_8));
		});
		return true;
	}

}
