package org.dromara.mica.mqtt.broker;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientPublish;
import org.dromara.mica.mqtt.core.client.MqttPayload;

/**
 * @author ChangJin Wei (魏昌进)
 */
public class DeviceD {

    public static void main(String[] args) {
        // 初始化 mqtt 客户端
        MqttClient client = MqttClient.create()
                                      .ip("127.0.0.1")
                                      .port(1883)
                                      .username("admin")
                                      .password("123456")
                                      .connectSync();


        DoorClient doorClient = client.getInterface(DoorClient.class);

        client.schedule(() -> {
            doorClient.sendMessage("open");
        }, 1000);
    }

    public interface DoorClient {

        @MqttClientPublish(value = "/a/door/open", qos = MqttQoS.QOS0, retain = true)
        void sendMessage(@MqttPayload String message);
    }
}
