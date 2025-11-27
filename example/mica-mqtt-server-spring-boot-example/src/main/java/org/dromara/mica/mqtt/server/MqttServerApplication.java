package org.dromara.mica.mqtt.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author wsq
 */
@SpringBootApplication
@EnableScheduling
public class MqttServerApplication {

	/**
	 * 先启动本项目，再启动 mica-mqtt-client-spring-boot-example 进行测试
	 */
	public static void main(String[] args) {
		SpringApplication.run(MqttServerApplication.class, args);
	}

}
