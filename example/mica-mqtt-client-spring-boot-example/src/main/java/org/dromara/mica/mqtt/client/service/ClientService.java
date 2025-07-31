package org.dromara.mica.mqtt.client.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Slf4j
@Service
public class ClientService {
	/**
	 * 使用 默认的 mqtt client
	 */
	@Autowired
	@Qualifier(MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN)
	private MqttClientTemplate client;

	@Autowired
	private HelloInterfaceA helloInterfaceA;

	@Autowired
	private HelloInterfaceB helloInterfaceB;

	public boolean publish(String body) {
		client.publish("/test/client", body.getBytes(StandardCharsets.UTF_8));
		return true;
	}

	public boolean publishHelloInterfaceA(String body) {
		helloInterfaceA.sayHello(body.getBytes(StandardCharsets.UTF_8));
		return true;
	}

	public boolean publishHelloInterfaceB(String body) {
		helloInterfaceB.sayHello(body.getBytes(StandardCharsets.UTF_8));
		return true;
	}

	public boolean sub() {
		client.subQos0("/test/#", (context, topic, message, payload) -> {
			log.info("{}\t{}", topic, new String(payload, StandardCharsets.UTF_8));
		});
		return true;
	}

}
