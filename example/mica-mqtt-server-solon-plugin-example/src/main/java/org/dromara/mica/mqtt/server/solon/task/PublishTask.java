package org.dromara.mica.mqtt.server.solon.task;

import org.dromara.mica.mqtt.server.solon.MqttServerTemplate;
import org.dromara.mica.mqtt.server.solon.pojo.User;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Component
public class PublishTask {
	@Inject
	private MqttServerTemplate mqttServerTemplate;

	@Scheduled(fixedDelay = 1000)
	public void publish() {
		mqttServerTemplate.publishAll("/test/123", "mica最牛皮".getBytes(StandardCharsets.UTF_8));
		mqttServerTemplate.publishAll("/test/object", User.newUser());
	}

}
