package org.dromara.mica.mqtt.client;

import org.dromara.mica.mqtt.spring.client.annotation.EnableMqttClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author wsq
 */
@SpringBootApplication
@EnableMqttClients
public class MqttClientApplication {

	/**
	 * 先启动 mica-mqtt-server-spring-boot-example 再启动本项目，进行测试
	 */
	public static void main(String[] args) {
		SpringApplication.run(MqttClientApplication.class, args);
	}

}
