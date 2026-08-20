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
    shutdown-timeout-sec: 6000        # mqtt 工作线程池关闭等待超时时间，单位：秒，默认：6000（约 100 分钟，沿用 mica-net 默认值，2.6.8 开始支持）。
                                       # 该值仅控制 awaitTermination 的阻塞时长，超时不会强制中断线程；
                                       # 服务端 stop 时会按连接逐个触发 IMqttConnectStatusListener.onDisconnect，
                                       # 这些任务由 groupExecutor（默认 8~16 线程）串行处理，超时后这些任务仍会继续执行直到自然结束。
                                       # 请同步将部署环境终止宽限期（如 k8s terminationGracePeriodSeconds）调到不小于此值，否则进程会被 SIGKILL 强杀。
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
        keystore-path:                   # 必填：ssl keystore 证书路径，支持 classpath: 路径
        keystore-pass:                   # 必填：ssl keystore 密码
        truststore-path:                 # 可选：ssl 双向认证 truststore 证书路径，支持 classpath: 路径
        truststore-pass:                 # 可选：ssl 双向认证 truststore 密码
        client-auth: NONE                # 客户端认证类型，默认：NONE（不需要），可选 OPTIONAL / REQUIRE
        protocols:                       # 可选参数：启用的 TLS 协议，需运行时 JDK 支持（2.6.9 开始支持）
            - TLSv1.2
            - TLSv1.3
        cipher-suites:                   # 可选：启用的密码套件，为空时使用 JDK 默认配置（2.6.9 开始支持）
          - TLS_AES_128_GCM_SHA256
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
        use-cipher-suites-order: true     # 可选：是否优先使用服务端密码套件顺序（2.6.9 开始支持）
    # ------ websocket mqtt 监听器 ------
    ws-listener:
      enable: true                      # 是否启用，默认：false
      port: 8083                        # websocket 端口，默认：8083
    # ------ websocket ssl mqtt 监听器 ------
    wss-listener:
      enable: false                     # 是否启用，默认：false
      port: 8084                        # 端口，默认：8084
      ssl:
        keystore-path:                   # 必填：ssl keystore 证书路径，支持 classpath: 路径
        keystore-pass:                   # 必填：ssl keystore 密码
        truststore-path:                 # 可选：ssl 双向认证 truststore 证书路径，支持 classpath: 路径
        truststore-pass:                 # 可选：ssl 双向认证 truststore 密码
        client-auth: NONE                # 客户端认证类型，默认：NONE（不需要），可选 OPTIONAL / REQUIRE
        protocols:                       # 可选参数：启用的 TLS 协议，需运行时 JDK 支持（2.6.9 开始支持）
            - TLSv1.2
            - TLSv1.3
        cipher-suites:                   # 可选：启用的密码套件，为空时使用 JDK 默认配置（2.6.9 开始支持）
          - TLS_AES_128_GCM_SHA256
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
        use-cipher-suites-order: true     # 可选：是否优先使用服务端密码套件顺序（2.6.9 开始支持）
    # ------ http api 监听器 ------
    http-listener:
      enable: true                      # 是否启用，默认：false
      port: 18083                       # http 端口
#      ip: 0.0.0.0                      # 服务端 ip，默认为空（0.0.0.0）
      auth:                             # http api 认证（2.6.10 开始支持）
        enable: false                   # 是否启用，默认：false
        scheme: Basic                   # 认证 scheme: Basic / Bearer / 自定义，默认：Basic
        header-name: authorization      # token 所在的请求头，默认：authorization
        username: mica                  # scheme = Basic 时使用的账号
        password: mica                  # scheme = Basic 时使用的密码
      # 兼容旧字段,等价于上面的 auth.username / auth.password
      # 2.6.10 标记为 @Deprecated,将在后续版本移除
      basic-auth:
        enable: false
        username: mica
        password: mica
      mcp:                              # 大模型 MCP（Model Context Protocol）
        enable: false                   # 是否启用，默认：false
        endpoint: /mcp/message          # stream http endpoint
        sse-endpoint: /mcp/sse          # sse 端点
        sse-message-endpoint: /mcp/sse/message  # sse message 端点
      ssl:                              # http ssl 配置
        enable: false                   # 是否启用，默认：false
        keystore-path:                  # 必填：ssl keystore 证书路径，支持 classpath: 路径
        keystore-pass:                  # 必填：ssl keystore 密码
        truststore-path:                # 可选：ssl 双向认证 truststore 证书路径
        truststore-pass:                # 可选：ssl 双向认证 truststore 密码
        client-auth: NONE               # 客户端认证类型，默认：NONE（不需要），可选 OPTIONAL / REQUIRE
        protocols:                      # 可选参数：启用的 TLS 协议，需运行时 JDK 支持（2.6.9 开始支持）
            - TLSv1.2
            - TLSv1.3
        cipher-suites:                  # 可选：启用的密码套件，为空时使用 JDK 默认配置（2.6.9 开始支持）
          - TLS_AES_128_GCM_SHA256
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
        use-cipher-suites-order: true   # 可选：是否优先使用服务端密码套件顺序（2.6.9 开始支持）
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
| HttpFilter                    | 否（2.6.10+）  | 自定义 HTTP API 认证过滤器,优先级最高,完全接管              |
| ITokenValidator               | 否（2.6.10+）  | 自定义 Token 校验器,scheme/header 走配置文件                |

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

### 2.4 @MqttServerFunction（MQTT 消息处理函数）注解订阅

`2.5.3` 开始支持，通过 `@MqttServerFunction` 注解标注方法即可订阅 MQTT 消息，无需实现 `IMqttMessageListener` 接口。注意：如果自行实现了 `IMqttMessageListener`，`@MqttServerFunction` 注解就不生效了（二者互斥，取一即可）。

注解定义于 `org.dromara.mica.mqtt.core.annotation.MqttServerFunction`（`2.5.4` 开始统一到 mica-mqtt-common 包）：

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| value | String[] | 无 | 订阅的 topic filter，支持通配符 `+`、`#` 以及 `${变量}` 占位符（2.5.4 开始支持） |
| deserialize | Class<? extends MqttDeserializer> | MqttJsonDeserializer | 消息负载反序列化器，默认 JSON 反序列化，可自定义 |

方法参数按类型自动注入，均为可选参数（可省略，方法参数数量需在 2 ~ 6 之间，且方法必须为 public 且非 static）：

| 参数类型 | 说明 |
| --- | --- |
| ChannelContext | 客户端连接上下文，可选参数，如通过 `context.getClientNode()` 获取客户端节点信息 |
| String | 实际接收到消息的主题名称，可选参数 |
| Map<String, String> | topic 中 `${xxxx}` 变量解析结果（2.5.4 开始支持），可选参数，注意：类型必须为 `Map<String, String>` |
| MqttPublishMessage | 完整的 MQTT 发布消息对象，包含消息头和负载，可选参数 |
| byte[] | 消息负载内容，以字节数组形式提供，可选参数 |
| ByteBuffer | 消息负载内容，以 ByteBuffer 形式提供，可选参数 |
| 其他对象类型 | 消息负载内容，默认 JSON 反序列化，可通过注解 `deserialize` 属性自定义反序列化器 |

示例（参考 example 模块 `MqttServerMessageListener2`）：

```java
@Slf4j
@Service
public class MqttServerMessageListener2 {

	/**
	 * 精确匹配 /test/object，对象参数默认 json 序列化
	 *
	 * @param topic mqtt Topic
	 * @param user  订阅消息的负载内容，默认 json 序列化
	 */
	@MqttServerFunction("/test/object")
	public void func1(String topic, User<?> user) {
		log.info("topic:{} user:{}", topic, user);
	}

	/**
	 * 精确匹配 /test/client，byte[] 接收原始消息负载
	 */
	@MqttServerFunction("/test/client")
	public void func2(String topic, byte[] message) {
		log.info("topic:{} message:{}", topic, new String(message));
	}

	/**
	 * 匹配 /test/+，如需匹配所有消息请使用通配符 #；
	 * ${xxxx} 为 topic 变量占位符（2.5.4 开始支持）
	 */
	@MqttServerFunction("/test/${xxxx}")
	public void func3(ChannelContext context, String topic, Map<String, String> topicVars, MqttPublishMessage publishMessage, byte[] message) {
		// 获取客户端节点信息
		Node clientNode = context.getClientNode();
		log.info("clientNode:{} topic:{} topicVars:{} publishMessage:{} message:{}", clientNode, topic, topicVars, publishMessage, new String(message));
	}

}
```

### 2.5 自定义配置（可选）

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

### 2.6 MqttServerTemplate 使用示例

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

### 2.7 客户端上下线监听
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

### 2.8 基于 mq 消息广播集群处理

详见: [mica-mqtt-broker](../../mica-mqtt-broker)

### 2.9 HTTP API 自定义 Token 校验（2.6.10+）

HTTP API 认证支持三种方式,优先级从高到低:

1. **注入 `HttpFilter` Bean** — 完全自定义(双因素、IP 白名单 + token、复杂策略)
2. **注入 `ITokenValidator` Bean** — 自定义 token 校验逻辑,scheme/header 走配置文件
3. **配置文件 username/password** — 内置 `BasicAuthValidator`,开箱即用

#### 方式一:注入 ITokenValidator

适用场景:对接 OAuth2 introspection、JWT 解析、自建 token 服务等。

```yaml
mqtt:
  server:
    http-listener:
      auth:
        enable: true
        scheme: Bearer           # 解析 Authorization: Bearer xxx
        header-name: authorization
```

```java
@Configuration
public class TokenAuthConfig {

    @Bean
    public ITokenValidator myTokenValidator() {
        return (request, token) -> {
            // 示例:调用 OAuth2 introspection 端点
            return oauthClient.introspect(token).isActive();
        };
    }
}
```

请求示例:

```bash
curl -H "Authorization: Bearer xxx" http://localhost:18083/mqtt/publish?topic=/test&message=hello
```

#### 方式二:注入 HttpFilter

适用场景:需要完全控制 HTTP 协议(如自定义响应、双因素认证、IP 白名单等)。

```java
@Bean
public HttpFilter customAuthFilter() {
    return new TokenAuthFilter("X-API-Key", "", (request, token) -> {
        // X-API-Key: xxx 形式
        return myKeyStore.contains(token);
    });
}
```

请求示例:

```bash
curl -H "X-API-Key: xxx" http://localhost:18083/mqtt/publish?topic=/test&message=hello
```

#### 方式三:配置文件(默认 Basic)

适用场景:内网、简单保护。

```yaml
mqtt:
  server:
    http-listener:
      auth:
        enable: true
        scheme: Basic
        username: mica
        password: mica
```

请求示例:

```bash
curl -u mica:mica http://localhost:18083/mqtt/publish?topic=/test&message=hello
```

校验失败统一返回 401,并设置 `WWW-Authenticate: <scheme> realm="Mica mqtt realm"` 响应头。

### 2.10 Prometheus + Grafana 监控对接

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
