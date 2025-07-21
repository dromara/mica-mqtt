# mica-mqtt-client-solon-plugin 使用文档

本插件基于 https://gitee.com/peigenlpy/mica-mqtt-solon-plugin 调整合并到官方（已经过作者同意）。

## 版本兼容
| 要求  | Solon 版本 |
|-----|-------|
| 最高  | 3.x   |
| 最低  | 2.8.0 |

## 一、添加依赖

```xml
<dependency>
    <groupId>org.dromara.mica-mqtt</groupId>
    <artifactId>mica-mqtt-client-solon-plugin</artifactId>
    <version>${version}</version>
</dependency>
```

## 二、mqtt 客户端

### 2.1 配置项示例
```yaml
mqtt:
  client:
    enabled: true               # 是否开启客户端，默认：true
    ip: 127.0.0.1               # 连接的服务端 ip ，默认：127.0.0.1
    port: 1883                  # 端口：默认：1883
    name: Mica-Mqtt-Client      # 名称，默认：Mica-Mqtt-Client
    clientId: 000001            # 客户端Id（非常重要，一般为设备 sn，不可重复）
    username: mica              # 认证的用户名，注意：2.5.x 开始将 user-name 改成了 username
    password: 123456            # 认证的密码
    timeout: 5                  # 超时时间，单位：秒，默认：5秒
    reconnect: true             # 是否重连，默认：true
    re-interval: 5000           # 重连时间，默认 5000 毫秒
    version: mqtt_3_1_1         # mqtt 协议版本，可选 MQTT_3_1、mqtt_3_1_1、mqtt_5，默认：mqtt_3_1_1
    read-buffer-size: 8KB       # 接收数据的 buffer size，默认：8k
    max-bytes-in-message: 10MB  # 消息解析最大 bytes 长度，默认：10M
    keep-alive-secs: 60         # keep-alive 时间，单位：秒
    heartbeat-mode: LAST_REQ    # 心跳模式，支持最后发送或接收心跳时间来计算心跳，默认：最后发送心跳的时间。（2.4.3 开始支持）
    heartbeat-timeout-strategy: PING # 心跳超时策略，支持发送 PING 和 CLOSE 断开连接，默认：最大努力发送 PING。（2.4.3 开始支持）
    clean-start: true           # session 保留 2.5.x 使用 clean-start，老版本用 clean-session，默认：true
    session-expiry-interval-secs: 0 # 开启保留 session 时，session 的有效期，默认：0（2.4.2 开始支持）
    biz-thread-pool-size: 2     # mqtt 工作线程数，默认：2，如果消息量比较大，处理较慢，例如做 emqx 的转发消息处理，可以调大此参数（2.4.2 开始支持）
    ssl:
      enabled: false            # 是否开启 ssl 认证，2.1.0 开始支持双向认证
      keystore-path:            # 可选参数：ssl 双向认证 keystore 目录，支持 classpath:/ 路径。
      keystore-pass:            # 可选参数：ssl 双向认证 keystore 密码
      truststore-path:          # 可选参数：ssl 双向认证 truststore 目录，支持 classpath:/ 路径。
      truststore-pass:          # 可选参数：ssl 双向认证 truststore 密码
```

注意：**ssl** 存在三种情况

| 服务端开启ssl                            | 客户端                                        |
| ---------------------------------------- | --------------------------------------------- |
| ClientAuth 为 NONE（不需要客户端验证）   | 仅仅需要开启 ssl 即可不用配置证书             |
| ClientAuth 为 OPTIONAL（与客户端协商）   | 需开启 ssl 并且配置 truststore 证书           |
| ClientAuth 为 REQUIRE (必须的客户端验证) | 需开启 ssl 并且配置 truststore、 keystore证书 |


### 2.2 可实现接口（注册成 Solon Bean 即可）

| 接口                           | 是否必须 | 说明                        |
| ---------------------------   |------| ------------------------- |
| IMqttClientConnectListener    | 否    | 客户端连接成功监听            |

### 2.3 客户端上下线监听
使用 Solon event 解耦客户端上下线监听，注意： 会跟自定义的 `IMqttClientConnectListener` 实现冲突，取一即可。

```java
@Component
public class MqttClientConnectedListener implements EventListener<MqttConnectedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(MqttClientConnectedListener.class);

    @Inject
    private MqttClientCreator mqttClientCreator;

    @Override
    public void onEvent(MqttConnectedEvent mqttConnectedEvent) throws Throwable {
        logger.info("MqttConnectedEvent:{}", mqttConnectedEvent);
    }
}
```
```java
@Component
public class MqttClientDisconnectListener implements EventListener<MqttDisconnectEvent> {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientDisconnectListener.class);

	@Inject
	private MqttClientCreator mqttClientCreator;

	@Override
	public void onEvent(MqttDisconnectEvent mqttDisconnectEvent) throws Throwable {
			logger.info("MqttDisconnectEvent:{}", mqttDisconnectEvent);
			// 在断线时更新 clientId、username、password
			mqttClientCreator.clientId("newClient" + System.currentTimeMillis())
				.username("newUserName")
				.password("newPassword");
	}
}

```

### 2.4 自定义 java 配置（可选）

```java
@Configuration
public class MqttClientCustomizerConfiguration {

	@Bean
	public MqttClientCustomizer mqttClientCustomizer() {
		return new MqttClientCustomizer() {
			@Override
			public void customize(MqttClientCreator creator) {
				// 此处可自定义配置 creator，会覆盖 yml 中的配置
				System.out.println("----------------MqttServerCustomizer-----------------");
			}
		};
	}

}
```

### 2.5 订阅示例
```java
/**
 * 客户端消息监听
 */
@Component
public class MqttClientSubscribeListener {
    private static final Logger logger = LoggerFactory.getLogger(MqttClientSubscribeListener.class);

    @MqttClientSubscribe("/test/#")
    public void subQos0(String topic, byte[] payload) {
        logger.info("subQos0,topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
    }

    @MqttClientSubscribe(value = "/qos1/#", qos = MqttQoS.AT_LEAST_ONCE)
    public void subQos1(String topic, byte[] payload) {
        logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
    }

    @MqttClientSubscribe("/sys/${productKey}/${deviceName}/thing/sub/register")
    public void thingSubRegister(String topic, byte[] payload) {
        // 1.3.8 开始支持，@MqttClientSubscribe 注解支持 ${} 变量替换，会默认替换成 +
        // 注意：mica-mqtt 会先从 Spring boot 配置中替换参数 ${}，如果存在配置会优先被替换。
        logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
    }

}
```
```java
/**
 * 客户端消息监听的另一种方式
 */
@MqttClientSubscribe("${topic1}")
public class MqttClientMessageListener implements IMqttClientMessageListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientMessageListener.class);

	@Override
	public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		logger.info("MqttClientMessageListener,topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}
}
```

### 2.6 共享订阅 topic 说明
mica-mqtt client 支持**两种共享订阅**方式：

1. 共享订阅：订阅前缀 `$queue/`，多个客户端订阅了 `$queue/topic`，发布者发布到topic，则只有一个客户端会接收到消息。
2. 分组订阅：订阅前缀 `$share/<group>/`，组客户端订阅了`$share/group1/topic`、`$share/group2/topic`..，发布者发布到topic，则消息会发布到每个group中，但是每个group中只有一个客户端会接收到消息。

### 2.8 MqttClientTemplate 使用示例

```java
@Component
public class ClientService {
    private static final Logger             logger = LoggerFactory.getLogger(ClientService.class);
    @Inject
    private              MqttClientTemplate client;

    public boolean publish(String body) {
        client.publish("/test/client", body.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    public boolean sub() {
        client.subQos0("/test/#", (context, topic, message, payload) -> {
            logger.info(topic + '\t' + new String(payload, StandardCharsets.UTF_8));
        });
        return true;
    }

}
```
