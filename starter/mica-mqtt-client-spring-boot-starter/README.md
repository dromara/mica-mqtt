# mica-mqtt-client-spring-boot-starter 使用文档

## 版本兼容
| 要求  | Spring boot 版本 |
|-----|----------------|
| 最高  | 3.x            |
| 最低  | 2.1.0.RELEASE  |

## 一、添加依赖

```xml
<dependency>
    <groupId>org.dromara.mica-mqtt</groupId>
    <artifactId>mica-mqtt-client-spring-boot-starter</artifactId>
    <version>${最新版本}</version>
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
    client-id: 000001           # 客户端Id（非常重要，一般为设备 sn，不可重复）
    username: mica              # 认证的用户名，注意：2.5.x 开始将 user-name 改成了 username
    password: 123456            # 认证的密码
    global-subscribe:           # 全局订阅的 topic，可被全局监听到，保留 session 停机重启，依然可以接受到消息。（2.2.9开始支持）
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


### 2.2 可实现接口（注册成 Spring Bean 即可）

| 接口                           | 是否必须 | 说明                             |
| ---------------------------   |------|--------------------------------|
| IMqttClientConnectListener    | 否    | 客户端连接成功监听                      |
| IMqttClientGlobalMessageListener    | 否    | 全局消息监听，可以监听到所有订阅消息。（2.2.9开始支持） |

### 2.3 客户端上下线监听
使用 Spring event 解耦客户端上下线监听，注意： `1.3.4` 开始支持。会跟自定义的 `IMqttClientConnectListener` 实现冲突，取一即可。

```java
/**
 * 示例：客户端连接状态监听
 *
 * @author L.cm
 */
@Service
public class MqttClientConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(MqttClientConnectListener.class);

    @Autowired
    private MqttClientCreator mqttClientCreator;

    @EventListener
    public void onConnected(MqttConnectedEvent event) {
        logger.info("MqttConnectedEvent:{}", event);
    }

    @EventListener
    public void onDisconnect(MqttDisconnectEvent event) {
        // 离线时更新重连时的密码，适用于类似阿里云 mqtt clientId 连接带时间戳的方式 
        logger.info("MqttDisconnectEvent:{}", event);
        // 在断线时更新 clientId、username、password
        mqttClientCreator.clientId("newClient" + System.currentTimeMillis())
            .username("newUserName")
            .password("newPassword");
    }

}
```

### 2.4 自定义 java 配置（可选）

```java
@Configuration(proxyBeanMethods = false)
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
@Service
public class MqttClientSubscribeListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientSubscribeListener.class);

	@MqttClientSubscribe("/test/#")
	public void subQos0(String topic, byte[] payload) {
		logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe(value = "/qos1/#", qos = MqttQoS.QOS1)
	public void subQos1(String topic, byte[] payload) {
		logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe("/sys/${productKey}/${deviceName}/thing/sub/register")
	public void thingSubRegister(String topic, byte[] payload) {
		// 1.3.8 开始支持，@MqttClientSubscribe 注解支持 ${} 变量替换，会默认替换成 +
		// 注意：mica-mqtt 会先从 Spring boot 配置中替换参数 ${}，如果存在配置会优先被替换。
		logger.info("topic:{} payload:{}", topic, new String(payload, StandardCharsets.UTF_8));
	}

	@MqttClientSubscribe(
		value = "/test/json",
		deserialize = MqttJsonDeserializer.class // 2.4.5 开始支持 自定义序列化，默认 json 序列化
	)
	public void testJson(String topic, MqttPublishMessage message, TestJsonBean data) {
		// 2.4.5 开始支持，支持 2 到 3 个参数，字段类型映射规则如下
		// String 字符串会默认映射到 topic，
		// MqttPublishMessage 会默认映射到 原始的消息，可以拿到 mqtt5 的 props 参数
		// byte[] 会映射到 mqtt 消息内容 payload
		// ByteBuffer 会映射到 mqtt 消息内容 payload
		// 其他类型会走序列化，确保消息能够序列化，默认为 json 序列化
		logger.info("topic:{} json data:{}", topic, data);
	}

}
```

### 2.6 共享订阅 topic 说明
mica-mqtt 支持两种**共享订阅**方式：

1. 共享订阅：订阅前缀 `$queue/`，多个客户端订阅了 `$queue/topic`，发布者发布到 `topic`，则只有一个客户端会接收到消息。
2. 分组订阅：订阅前缀 `$share/<group>/`，组客户端订阅了 `$share/group1/topic`、`$share/group2/topic`..，发布者发布到 `topic`，则消息会发布到每个 **group** 中，但是每个 **group** 中只有一个客户端会接收到消息。

**注意：** 如果发布的 `topic` 以 `/` 开头，例如：`/topic/test`，需要订阅 `$share/group1//topic/test`，另外 mica-mqtt 默认随机消息路由，共享订阅的多个客户端会随机收到消息。

### 2.7 MqttClientTemplate 使用示例

```java

import org.dromara.mica.mqtt.spring.client.MqttClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Service
public class MainService {
    private static final Logger logger = LoggerFactory.getLogger(MainService.class);
    @Autowired
    private MqttClientTemplate client;

    public boolean publish() {
        client.publish("/test/client", "mica最牛皮".getBytes(StandardCharsets.UTF_8));
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

## 3. 多个 mqtt client 客户端
### 3.1 自定义 MqttClientTemplate bean 2.2.11 开始已简化，老版本建议先升级。
```java
@Configuration
public class OtherMqttClientConfiguration {

	@Bean("mqttClientTemplate1")
	public MqttClientTemplate mqttClientTemplate1() {
		MqttClientCreator mqttClientCreator1 = MqttClient.create()
			.ip("mqtt.dreamlu.net")
			.username("mica")
			.password("mica");
		return new MqttClientTemplate(mqttClientCreator1);
	}

}
```

### 3.2 修改 starter 自带的 MqttClientTemplate Bean 引入
由于现在加入了一个新的名为 `mqttClientTemplate1` MqttClientTemplate，老的 starter 内置的 MqttClientTemplate 引入也需要添加 bean name。

```java
@Autowired
@Qualifier(MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN)
private MqttClientTemplate mqttClientTemplate;
```

### 3.3 新加入的 mqttClientTemplate1 MqttClientTemplate bean 引入
```java
@Autowired
@Qualifier("mqttClientTemplate1")
private MqttClientTemplate mqttClientTemplate;
```

### 3.4 新加入的 mqttClientTemplate1 注解订阅
注意：由于 `@MqttClientSubscribe` clientTemplateBean 默认是 `MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN`，所以新增的 `mqttClientTemplate1` 注解订阅的时候也需要配置。
```java
@MqttClientSubscribe(
    value = "/#", 
    clientTemplateBean = "mqttClientTemplate1"
)
public void sub1(String topic, byte[] payload) {
    logger.info("topic:{} payload:{}", topic, ByteBufferUtil.toString(payload));
}
```

### 接口代理

```java

@EnableMqttClients
public class MqttClientApplication {
    // ...
}

@MqttClient(clientBean = "mqttClientTemplate")
public interface HelloInterfaceB {

    @MqttClientPublish("/test/HelloInterfaceB")
    void sayHello(@MqttPayload Object payload);

}
```
