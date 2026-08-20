# mica-mqtt-server-solon-plugin 使用文档

本插件基于 https://gitee.com/peigenlpy/mica-mqtt-solon-plugin 调整合并到官方（已经过作者同意）。

## 版本兼容
| 要求  | Solon 版本 |
|-----|----------|
| 最高  | 4.x      |
| 最低  | 2.8.0    |

## 一、添加依赖

```xml
<dependency>
    <groupId>org.dromara.mica-mqtt</groupId>
    <artifactId>mica-mqtt-server-solon-plugin</artifactId>
    <version>${version}</version>
</dependency>
```

## 二、mqtt 服务

### 2.1 配置项

```yaml
# mqtt 服务端配置
mqtt:
  server:
    enabled: true               # 是否开启服务端，默认：true
    name: Mica-Mqtt-Server      # 名称，默认：Mica-Mqtt-Server
    heartbeat-timeout: 120000   # 心跳超时，单位毫秒，默认: 1000 * 120
    read-buffer-size: 8KB       # 接收数据的 buffer size，默认：8k
    max-bytes-in-message: 10MB  # 消息解析最大 bytes 长度，默认：10M
    properties:                 # MQTT 5.0 服务端能力属性（CONNACK Properties）
      receive-maximum: 65535    # 服务端允许客户端同时处理的 QoS1/QoS2 未确认报文上限，默认：65535（2.6.8 开始支持）
      maximum-qos: 2            # 服务端支持的最大 QoS，默认：2（2.6.8 开始支持）
      retain-available: true    # 服务端是否支持保留消息，默认：true（2.6.8 开始支持）
      maximum-packet-size: 268435456 # 服务端可处理的最大报文大小（字节），默认：268435456（2.6.8 开始支持）
      topic-alias-maximum: 0    # 服务端支持的最大主题别名数，0 表示不启用，默认：0（2.6.8 开始支持）
      wildcard-subscription-available: true # 服务端是否支持通配符订阅，默认：true（2.6.8 开始支持）
      shared-subscription-available: true   # 服务端是否支持共享订阅，默认：true（2.6.8 开始支持）
      subscription-identifier-available: false # 服务端是否支持订阅标识符，默认：false（2.6.8 开始支持）
      server-keep-alive: 0      # 服务端下发给 MQTT 5.0 客户端的 Keep Alive，0 表示不接管，默认：0（2.6.8 开始支持）
    auth:
      enable: false             # 是否开启 mqtt 认证
      username: mica            # mqtt 认证用户名
      password: mica            # mqtt 认证密码
    debug: true                 # 如果开启 prometheus 指标收集建议关闭
    stat-enable: true           # 开启指标收集，debug 和 prometheus 开启时需要打开，默认开启，关闭节省内存
    shutdown-timeout-sec: 6000 # mqtt 工作线程池关闭等待超时时间，单位：秒，默认：6000（约 100 分钟，沿用 mica-net 默认值，2.6.8 开始支持）。
                                # 该值仅控制 awaitTermination 的阻塞时长，超时不会强制中断线程；
                                # 服务端 stop 时会按连接逐个触发 IMqttConnectStatusListener.onDisconnect，
                                # 这些任务由 groupExecutor（默认 8~16 线程）串行处理，超时后这些任务仍会继续执行直到自然结束。
                                # 请同步将部署环境终止宽限期（如 k8s terminationGracePeriodSeconds）调到不小于此值，否则进程会被 SIGKILL 强杀。
    mqtt-listener:              # mqtt 监听器
      enable: true              # 是否开启，默认：false
#      ip: "0.0.0.0"            # 服务端 ip 默认为空，0.0.0.0，建议不要设置
      port: 1883                # 端口，默认：1883
    mqtt-ssl-listener:          # mqtt ssl 监听器
      enable: false             # 是否开启，默认：false
      port: 8883                # 端口，默认：8883
      ssl:                      # ssl 配置，必须
        keystore-path:          # 必须参数：ssl keystore 目录，支持 classpath: 路径。
        keystore-pass:          # 必选参数：ssl keystore 密码
        truststore-path:        # 可选参数：ssl 双向认证 truststore 目录，支持 classpath: 路径。
        truststore-pass:        # 可选参数：ssl 双向认证 truststore 密码
        client-auth: NONE       # 是否需要客户端认证（双向认证），默认：NONE（不需要）
        protocols:              # 可选参数：启用的 TLS 协议，需运行时 JDK 支持（2.6.9 开始支持）
          - TLSv1.2
          - TLSv1.3
        cipher-suites:          # 可选参数：启用的密码套件，为空时使用 JDK 默认配置（2.6.9 开始支持）
          - TLS_AES_128_GCM_SHA256
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
        use-cipher-suites-order: true # 可选参数：是否优先使用服务端密码套件顺序（2.6.9 开始支持）
    ws-listener:                # websocket mqtt 监听器
      enable: true              # 是否开启，默认：false
      port: 8083                # websocket 端口，默认：8083
    wss-listener:               # websocket ssl mqtt 监听器
      enable: false             # 是否开启，默认：false
      port: 8084                # 端口，默认：8084
      ssl:                      # ssl 配置，必须
        keystore-path:          # 必须参数：ssl keystore 目录，支持 classpath: 路径。
        keystore-pass:          # 必选参数：ssl keystore 密码
        truststore-path:        # 可选参数：ssl 双向认证 truststore 目录，支持 classpath: 路径。
        truststore-pass:        # 可选参数：ssl 双向认证 truststore 密码
        client-auth: NONE       # 是否需要客户端认证（双向认证），默认：NONE（不需要）
        protocols:              # 可选参数：启用的 TLS 协议，需运行时 JDK 支持（2.6.9 开始支持）
            - TLSv1.2
            - TLSv1.3
        cipher-suites:          # 可选参数：启用的密码套件，为空时使用 JDK 默认配置（2.6.9 开始支持）
          - TLS_AES_128_GCM_SHA256
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
        use-cipher-suites-order: true # 可选参数：是否优先使用服务端密码套件顺序（2.6.9 开始支持）
    http-listener:
      enable: true
      port: 18083
      auth:                     # http api 认证（2.6.10 开始支持）
        enable: true
        scheme: Basic           # 认证 scheme: Basic / Bearer / 自定义，默认：Basic
        header-name: authorization  # token 所在的请求头，默认：authorization
        username: mica
        password: mica
      # 兼容旧字段,等价于上面的 auth.username / auth.password
      # 2.6.10 标记为 @Deprecated,将在后续版本移除
      basic-auth:
        enable: true
        username: mica
        password: mica
      mcp:                      # 大模型 mcp
        enable: true
        endpoint: /mcp/message          # stream http endpoint
        sse-endpoint: /mcp/sse          # sse 端点
        sse-message-endpoint: /mcp/sse/message  # sse message 端点
      ssl:                      # http ssl 配置
        enable: false           # 是否启用，默认：false
        keystore-path:          # 必填：ssl keystore 证书路径，支持 classpath: 路径
        keystore-pass:          # 必填：ssl keystore 密码
        truststore-path:        # 可选：ssl 双向认证 truststore 证书路径
        truststore-pass:        # 可选：ssl 双向认证 truststore 密码
        client-auth: NONE       # 客户端认证类型，默认：NONE（不需要），可选 OPTIONAL / REQUIRE
        protocols:              # 可选参数：启用的 TLS 协议，需运行时 JDK 支持（2.6.9 开始支持）
            - TLSv1.2
            - TLSv1.3
        cipher-suites:          # 可选：启用的密码套件，为空时使用 JDK 默认配置（2.6.9 开始支持）
          - TLS_AES_128_GCM_SHA256
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
        use-cipher-suites-order: true # 可选：是否优先使用服务端密码套件顺序（2.6.9 开始支持）
```

注意：**ssl** 存在三种情况

| 服务端开启ssl                            | 客户端                                        |
| ---------------------------------------- | --------------------------------------------- |
| ClientAuth 为 NONE（不需要客户端验证）   | 仅仅需要开启 ssl 即可不用配置证书             |
| ClientAuth 为 OPTIONAL（与客户端协商）   | 需开启 ssl 并且配置 truststore 证书           |
| ClientAuth 为 REQUIRE (必须的客户端验证) | 需开启 ssl 并且配置 truststore、 keystore证书 |

### 2.2 可实现接口（注册成 Solon Bean 即可）

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
| IpStatListener                | 否          | t-io ip 状态监听                                  |
| IMqttMessageInterceptor       | 否          | 消息拦截器，1.3.9 新增                                |
| HttpFilter                    | 否（2.6.10+）  | 自定义 HTTP API 认证过滤器,优先级最高,完全接管              |
| ITokenValidator               | 否（2.6.10+）  | 自定义 Token 校验器,scheme/header 走配置文件                |

### 2.3 IMqttMessageListener (用于监听客户端上传的消息) 使用示例

```java
@Component
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

方法参数按类型自动注入，均为可选参数（可省略）：

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
@Component
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
@Configuration
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
@Component
public class ServerService {
   @Inject
   private MqttServerTemplate server;

   public boolean publish(String body) {
      server.publishAll("/test/123", body.getBytes(StandardCharsets.UTF_8));
      return true;
   }
}
```

### 2.7 客户端上下线监听
使用 Solon event 解耦客户端上下线监听，注意：会跟自定义的 `IMqttConnectStatusListener` 实现冲突，取一即可。

```java
@Component
public class MqttConnectOfflineListener implements EventListener<MqttClientOfflineEvent> {
   private static final Logger logger = LoggerFactory.getLogger(MqttConnectOfflineListener.class);

   @Override
   public void onEvent(MqttClientOfflineEvent mqttClientOfflineEvent) throws Throwable {
      logger.info("MqttClientOnlineEvent:{}", mqttClientOfflineEvent);
   }
}
```

```java
@Component
public class MqttConnectOnlineListener implements EventListener<MqttClientOnlineEvent> {
	private static final Logger logger = LoggerFactory.getLogger(MqttConnectOnlineListener.class);

	@Override
	public void onEvent(MqttClientOnlineEvent mqttClientOnlineEvent) throws Throwable {
		logger.info("MqttClientOnlineEvent:{}", mqttClientOnlineEvent);
	}
}
```

### 2.8 HTTP API 自定义 Token 校验（2.6.10+）

HTTP API 认证支持三种方式,优先级从高到低:

1. **注入 `HttpFilter` Bean** — 完全自定义(双因素、IP 白名单 + token、复杂策略)
2. **注入 `ITokenValidator` Bean** — 自定义 token 校验逻辑,scheme/header 走配置文件
3. **配置文件 username/password** — 内置 `BasicAuthValidator`,开箱即用

#### 方式一:注入 ITokenValidator

适用场景:对接 OAuth2 introspection、JWT 解析、自建 token 服务等。

```yaml
mqtt.server.http-listener:
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
mqtt.server.http-listener:
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

### 2.9 Prometheus + Grafana 监控对接

#### 添加依赖
```xml
<dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-cloud-metrics</artifactId>
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

#### 配置说明

solon 官方配置文档：https://solon.noear.org/article/588
