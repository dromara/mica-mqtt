package org.dromara.mica.mqtt.client.solon.test;

import org.dromara.mica.mqtt.client.solon.MqttClientTemplate;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.event.AppLoadEndEvent;
import org.noear.solon.core.event.EventListener;

import java.nio.charset.StandardCharsets;

/**
 * <b>(ClientTest)</b>
 *
 * @author Peigen
 * @version 1.0.0
 * @since 2023/7/15
 */
@Component
public class ClientTest implements EventListener<AppLoadEndEvent> {
	public static void main(String[] args) {
		Solon.start(ClientTest.class, args);
	}

	@Inject
	MqttClientTemplate client;

	@Override
	public void onEvent(AppLoadEndEvent event) throws Throwable {
		client.publish("mica", "hello".getBytes(StandardCharsets.UTF_8));
	}
}
