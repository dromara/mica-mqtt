# mica-mqtt-server-spring-boot-starter 使用文档

## 版本兼容
| 要求  | Spring boot 版本 |
|-----|----------------|
| 最高  | 4.x            |
| 最低  | 2.1.0.RELEASE  |

## 一、添加依赖

```xml
<dependency>
    <groupId>org.dromara.mica-mqtt</groupId>
    <artifactId>mica-mqtt-server-spring-boot-starter</artifactId>
    <version>${最新版本}</version>
</dependency>
```

## 二、mqtt 服务

### 2.1 配置项

```yaml
# mqtt 服务端配置
mqtt:
  server:
    # ------ 基础配置 ------
    enabled: true                       # 是否启用，默认：true
    name: Mica-Mqtt-Server              # 名称，默认：Mica-Mqtt-Server
    node-name:                          # 节点名称，用于处理集群
    debug: false                        # 调试日志，开启后会影响性能，开启 prometheus 时建议关闭
    stat-enable: true                   # 是否开启监控（指标收集），关闭可节省内存，默认：true
    proxy-protocol-on: false            # 开启代理协议，支持 nginx proxy_protocol on;，默认：false
    # ------ 线程池 ------
    tio-executor-size:                  # tio 编解码等线程数
    group-executor-size:                # AIO AsynchronousChannelGroup 的线程池
    mqtt-executor-size:                 # mqtt 工作线程数，默认：8或2倍CPU核心数（取较大值），消息量大时调大此参数
    graceful-timeout-sec: 30           # mqtt 工作线程池优雅关闭等待超时时间，单位：秒，默认：30（2.6.8 开始支持）。超过该时间后会调用 shutdownNow() 强制中断未完成任务。
    force-timeout-sec: 5               # shutdownNow 后的二次等待超时时间，单位：秒，默认：5（2.6.8 开始支持）。用于回收被中断的 worker 线程，通常 5~10s 足够。
    # ------ mqtt 协议参数 ------
    heartbeat-timeout: 120000           # 心跳超时时间（单位：毫秒，默认：1000 * 120），设为 0 或负数表示禁用
    keepalive-backoff: 0.75             # MQTT 客户端 keepalive 系数（默认：0.75），连接超时 = keepalive * 系数 * 2，不得小于 0.5
    read-buffer-size: 8KB               # 接收数据的 buffer size，默认：8k
    max-bytes-in-message: 10MB          # 消息解析最大 bytes 长度，默认：10M
    max-client-id-length: 64            # 客户端 ID 最大长度，mqtt 3.1 规定 23，这里默认 64 以减少问题
    # ------ MQTT 5.0 服务端能力属性（CONNACK Properties）------
    properties:
      receive-maximum: 65535            # 服务端允许客户端同时处理的 QoS1/QoS2 未确认报文上限，默认：65535（2.6.8 开始支持）
      maximum-qos: 2                    # 服务端支持的最大 QoS，默认：2（2.6.8 开始支持）
      retain-available: true            # 服务端是否支持保留消息，默认：true（2.6.8 开始支持）
      maximum-packet-size: 268435456    # 服务端可处理的最大报文大小（字节），默认：268435456（2.6.8 开始支持）
      topic-alias-maximum: 0            # 服务端支持的最大主题别名数，0 表示不启用，默认：0（2.6.8 开始支持）
      wildcard-subscription-available: true # 服务端是否支持通配符订阅，默认：true（2.6.8 开始支持）
      shared-subscription-available: true   # 服务端是否支持共享订阅，默认：true（2.6.8 开始支持）
      subscription-identifier-available: false # 服务端是否支持订阅标识符，默认：false（2.6.8 开始支持）
      server-keep-alive: 0              # 服务端下发给 MQTT 5.0 客户端的 Keep Alive，0 表示不接管，默认：0（2.6.8 开始支持）
    # ------ mqtt 认证 ------
    auth:
      enable: false                     # 是否开启 mqtt 认证，默认：false
      username: mica                    # mqtt 认证账号
      password: mica                    # mqtt 认证密码
    # ------ mqtt tcp 监听器 ------
    mqtt-listener:
      enable: true                      # 是否启用，默认：false
#      ip: 0.0.0.0                      # 服务端 ip，默认为空（0.0.0.0），建议不要设置
      port: 1883                        # 端口，默认：1883
    # ------ mqtt ssl 监听器 ------
    mqtt-ssl-listener:
      enable: false                     # 是否启用，默认：false
      port: 8883                        # 端口，默认：8883
      ssl:
        keystore-path:                   # 必填：ssl keystore 证书路径，支持 classpath:/ 路径
        keystore-pass:                   # 必填：ssl keystore 密码
        truststore-path:                 # 可选：ssl 双向认证 truststore 证书路径，支持 classpath:/ 路径
        truststore-pass:                 # 可选：ssl 双向认证 truststore 密码
        client-auth: NONE                # 客户端认证类型，默认：NONE（不需要），可选 OPTIONAL / REQUIRE
    # ------ websocket mqtt 监听器 ------
    ws-listener:
      enable: true                      # 是否启用，默认：false
      port: 8083                        # websocket 端口，默认：8083
    # ------ websocket ssl mqtt 监听器 ------
    wss-listener:
      enable: false                     # 是否启用，默认：false
      port: 8084                        # 端口，默认：8084
      ssl:
        keystore-path:                   # 必填：ssl keystore 证书路径，支持 classpath:/ 路径
        keystore-pass:                   # 必填：ssl keystore 密码
        truststore-path:                 # 可选：ssl 双向认证 truststore 证书路径，支持 classpath:/ 路径
        truststore-pass:                 # 可选：ssl 双向认证 truststore 密码
        client-auth: NONE                # 客户端认证类型，默认：NONE（不需要），可选 OPTIONAL / REQUIRE
    # ------ http api 监听器 ------
    http-listener:
      enable: true                      # 是否启用，默认：false
      port: 18083                       # http 端口
#      ip: 0.0.0.0                      # 服务端 ip，默认为空（0.0.0.0）
      basic-auth:                       # http Basic 认证
        enable: false                   # 是否启用，默认：false
        username: mica                  # http Basic 认证账号
        password: mica                  # http Basic 认证密码
      mcp:                              # 大模型 MCP（Model Context Protocol）
        enable: false                   # 是否启用，默认：false
        endpoint: /mcp/message          # stream http endpoint
        sse-endpoint: /mcp/sse          # sse 端点
        sse-message-endpoint: /mcp/message  # sse message 端点
      ssl:                              # http ssl 配置
        enable: false                   # 是否启用，默认：false
        keystore-path:                  # 必填：ssl keystore 证书路径，支持 classpath:/ 路径
        keystore-pass:                  # 必填：ssl keystore 密码
        truststore-path:                # 可选：ssl 双向认证 truststore 证书路径
        truststore-pass:                # 可选：ssl 双向认证 truststore 密码
        client-auth: NONE               # 客户端认证类型，默认：NONE（不需要），可选 OPTIONAL / REQUIRE
```

注意：**ssl** 存在三种情况

| 服务端开启ssl                            | 客户端                                        |
| ---------------------------------------- | --------------------------------------------- |
| ClientAuth 为 NONE（不需要客户端验证）   | 仅仅需要开启 ssl 即可不用配置证书             |
| ClientAuth 为 OPTIONAL（与客户端协商）   | 需开启 ssl 并且配置 truststore 证书           |
| ClientAuth 为 REQUIRE (必须的客户端验证) | 需开启 ssl 并且配置 truststore、 keystore证书 |

### 2.2 可实现接口（注册成 Spring Bean 即可）

| 接口                            | 是否必须       | 说明                                            |
|-------------------------------|------------|-----------------------------------------------|
| IMqttServerUniqueIdService    | 否          | 用于 clientId 不唯一时，自定义实现唯一标识，后续接口使用它替代 clientId |
| IMqttServerAuthHandler        | 是          | 用于服务端认证                                       |
| IMqttServerSubscribeValidator | 否（建议实现）    | 1.1.3 新增，用于对客户端订阅校验                           |
| IMqttServerPublishPermission  | 否（建议实现）    | 1.2.2 新增，用于对客户端发布权限校验                         |
| IMqttMessageListener          | 否（1.3.x为否） | 消息监听                                          |
| IMqttConnectStatusListener    | 是          | 连接状态监听                                        |
| IMqttSessionManager           | 否          | session 管理                                    |
| IMqttSessionListener          | 否          | session 监听                                    |
| IMqttMessageStore             | 集群是，单机否    | 遗嘱和保留消息存储                                     |
| AbstractMqttMessageDispatcher | 集群是，单机否    | 消息转发，（遗嘱、保留消息转发）                              |
| IMqttMessageInterceptor       | 否          | 消息拦截器，1.3.9 新增                                |

### 2.3 IMqttMessageListener (用于监听客户端上传的消息) 使用示例

```java
@Service
public class MqttServerMessageListener implements IMqttMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(MqttServerMessageListener.class);

    @Override
    public void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qoS, MqttPublishMessage message) {
        log.info("clientId:{} message:{} payload:{}", clientId, message, new String(message.getPayload(), StandardCharsets.UTF_8));
    }
}
```

### 2.4 自定义配置（可选）

```java
@Configuration(proxyBeanMethods = false)
public class MqttServerCustomizerConfiguration {

	@Bean
	public MqttServerCustomizer mqttServerCustomizer() {
		return new MqttServerCustomizer() {
			@Override
			public void customize(MqttServerCreator creator) {
				// 此处可自定义配置 creator，会覆盖 yml 中的配置
				System.out.println("----------------MqttServerCustomizer-----------------");
			}
		};
	}

}
```

### 2.5 MqttServerTemplate 使用示例

```java

import org.dromara.mica.mqtt.spring.server.MqttServerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author wsq
 */
@Service
public class ServerService {
    @Autowired
    private MqttServerTemplate server;

    public boolean publish(String body) {
        server.publishAll("/test/123", body.getBytes(StandardCharsets.UTF_8));
        return true;
    }
}
```

### 2.6 客户端上下线监听
使用 Spring event 解耦客户端上下线监听，注意： `1.3.4` 开始支持。会跟自定义的 `IMqttConnectStatusListener` 实现冲突，取一即可。

```java
@Service
public class MqttConnectStatusListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttConnectStatusListener.class);

	@EventListener
	public void online(MqttClientOnlineEvent event) {
		logger.info("MqttClientOnlineEvent:{}", event);
	}

	@EventListener
	public void offline(MqttClientOfflineEvent event) {
		logger.info("MqttClientOfflineEvent:{}", event);
	}

}
```

### 2.7 基于 mq 消息广播集群处理

详见: [mica-mqtt-broker](../../mica-mqtt-broker)

### 2.8 Prometheus + Grafana 监控对接

#### 添加依赖
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

#### 指标
| 支持的指标                          | 说明             |
|--------------------------------| ---------------- |
| mqtt_connections_accepted      | 共接受过连接数   |
| mqtt_connections_closed        | 关闭过的连接数   |
| mqtt_connections_size          | 当前连接数       |
| mqtt_messages_handled_packets  | 已处理消息数     |
| mqtt_messages_handled_bytes    | 已处理消息字节数  |
| mqtt_messages_received_packets | 已接收消息数      |
| mqtt_messages_received_bytes   | 已处理消息字节数 |
| mqtt_messages_send_packets     | 已发送消息数      |
| mqtt_messages_send_bytes       | 已发送消息字节数  |
