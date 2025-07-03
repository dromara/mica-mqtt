package org.dromara.mica.mqtt.jfinal.server;

/**
 * mica mqtt server 插件测试
 *
 * @author L.cm
 */
public class MqttServerPluginTest {

	public static void main(String[] args) {
		MqttServerPlugin plugin = new MqttServerPlugin();
		plugin.config(mqttServerCreator -> {
			// mqttServerCreator 上有很多方法，详见 mica-mqtt-core
			mqttServerCreator
				.enableMqtt()
				.enableMqttWs()
				.enableMqttHttpApi()
			;
		});
		plugin.start();
	}

}
