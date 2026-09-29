# 使用文档

## 添加依赖

```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-server</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

## 服务端使用

```java
// 注意：为了能接受更多链接（降低内存），请添加 jvm 参数 -Xss129k
MqttServer mqttServer = MqttServer.create()
    // 服务端 ip 默认为空，0.0.0.0，建议不要设置
    .ip("0.0.0.0")
    // 默认：1883
    .port(1883)
    // 默认为： 8092（mqtt 默认最大消息大小），为了降低内存可以减小小此参数，如果消息过大 t-io 会尝试解析多次（建议根据实际业务情况而定）
    .readBufferSize(512)
    // 最大包体长度，如果包体过大需要设置此参数，默认为： 8092
    .maxBytesInMessage(1024 * 100)
    // 自定义认证
    .authHandler((clientId, userName, password) -> true)
    // 消息监听
    .messageListener((context, clientId, topic, qos, message) -> {
        logger.info("clientId:{} payload:{}", clientId, new String(message.payload(), StandardCharsets.UTF_8));
    })
    // 心跳超时时间，默认：120s
    .heartbeatTimeout(120_1000L)
    // ssl 配置
    .useSsl("", "", "")
    // 开启代理协议，支持 nginx 开启 tcp proxy_protocol on; 时转发源 ip 信息。2.4.1 版本开始支持
    .proxyProtocolEnable()
    // 自定义客户端上下线监听
    .connectStatusListener(new IMqttConnectStatusListener() {
        @Override
        public void online(String clientId) {

        }

        @Override
        public void offline(String clientId) {

        }
    })
    // 自定义消息转发，可用 mq 广播实现集群化处理
    .messageDispatcher(new IMqttMessageDispatcher() {
        @Override
        public void config(MqttServer mqttServer) {

        }

        @Override
        public boolean send(Message message) {
            return false;
        }

        @Override
        public boolean send(String clientId, Message message) {
            return false;
        }
    })
    .debug() // 开启 debug 信息日志
    .start();

// 发送给某个客户端
mqttServer.publish("clientId","/test/123", "mica最牛皮".getBytes(StandardCharsets.UTF_8));

// 发送给所有在线监听这个 topic 的客户端
mqttServer.publishAll("/test/123", "mica最牛皮".getBytes(StandardCharsets.UTF_8));

// 停止服务
mqttServer.stop();
```

## 持久会话与离线消息（2.7.0）

服务端默认支持协议级的持久会话，客户端 `cleanSession=false`（MQTT 5 为 `Clean Start=false`）断开后，
订阅、在途消息与离线期间的消息都会保留，重连（`CONNACK` 的 `Session Present = 1`）后自动回放。

```java
MqttServer mqttServer = MqttServer.create()
    .enableMqtt(1883)
    // 持久会话总开关，false 时退化为"断开即清理"，默认 true
    .enablePersistentSession(true)
    // 每个会话的离线消息队列上限，溢出丢最旧，<= 0 表示不缓存，默认 1000
    .maxOfflineQueueSize(1000)
    // 离线消息保存时长（秒），0 表示不限期，默认 0
    .offlineMessageTtlSeconds(0)
    // QoS0 消息是否也进入离线队列，默认 false（QoS0 不保证投递）
    .mqueueStoreQos0(false)
    // MQTT5 客户端未携带 Session Expiry Interval 时的默认值（秒），默认 7200
    .sessionExpiryIntervalSeconds(7200)
    // MQTT3.1.1 持久会话空闲过期时间（秒），0 表示不自动过期，默认 0
    .v311IdleSessionTimeoutSeconds(0)
    // 每会话在途消息上限，<= 0 表示不限制，默认 1000
    .maxInflightPerSession(1000)
    .start();
```

会话判定规则（MQTT 5.0 规范 3.1.2.11.4）：

| 场景 | 是否持久会话 | CONNACK Session Present |
|------|-------------|------------------------|
| MQTT 3.1.1 `Clean Session = 1` | 否 | 0 |
| MQTT 3.1.1 `Clean Session = 0` | 是 | 服务端仍有该会话时为 1 |
| MQTT 5 `Clean Start = 1` | 否（本次丢弃旧会话） | 0 |
| MQTT 5 `Clean Start = 0`，`Session Expiry Interval > 0` | 是 | 服务端仍有该会话时为 1 |
| MQTT 5 `Session Expiry Interval = 0` | 否（连接结束即结束） | 0 |

> 离线消息与会话状态由 `IMqttSessionManager` 承载，默认实现 `InMemoryMqttSessionManager` 是进程内存储，
> 服务重启后会话与离线消息丢失。需要持久化可自行实现 `IMqttSessionManager` 接入 Redis / H2。

## http 和 websocket 依赖（2.4.2或之前版本需要该步骤）：

开启 http 或 websocket 需要添加 mica-net-http 依赖，如果不需要 http、websocket 把它们可以使用 `.httpEnable(false)` 和 `.websocketEnable(false)` 关掉就不需要该依赖了。

```xml
<dependency>
    <groupId>net.dreamlu</groupId>
    <artifactId>mica-net-http</artifactId>
    <version>${version}</version>
</dependency>
```

另外 http api 需要项目带有 jackson、fastjson、fastjson2、gson、hutool-json、snack3（mica-mqtt 2.3.4开始支持） 这些json工具其一。

## HTTP API 认证

HTTP API 通过 `MqttHttpApiListener` 暴露,支持 Basic / Bearer / 自定义 scheme 三种认证方式(2.6.10 开始支持)。框架提供两个开箱即用的 `HttpFilter` 和一个 `ITokenValidator` 抽象校验器,用户可以任选其一。

### 1. Basic 认证(配置账号密码)

```java
MqttServer.create()
    .httpApiListener(builder -> builder
        .serverNode(18083)
        .basicAuth("mica", "mica")  // 注入 BasicAuthFilter
    )
    .start();
```

请求示例:

```bash
curl -u mica:mica http://localhost:18083/mqtt/publish?topic=/test&message=hello
```

### 2. Bearer Token(对接 OAuth2 / 自建服务 / JWT)

实现 `ITokenValidator`,在 `validate` 方法里调用第三方校验服务,框架不绑定具体实现。

```java
public class OAuthTokenValidator implements ITokenValidator {
    @Override
    public boolean validate(HttpRequest request, String token) {
        // 示例:调用 OAuth2 introspection 端点
        return oauthClient.introspect(token).isActive();
    }
}
```

注册:

```java
MqttServer.create()
    .httpApiListener(builder -> builder
        .serverNode(18083)
        .tokenAuth(new OAuthTokenValidator())  // 注入 TokenAuthFilter + 自定义校验器
    )
    .start();
```

请求示例:

```bash
curl -H "Authorization: Bearer xxx" http://localhost:18083/mqtt/publish?topic=/test&message=hello
```

### 3. 自定义 header + scheme(网关透传 token)

支持自定义请求头名和 scheme 前缀,适用于网关已经将 token 放到 `X-API-Key` 等自定义头部的场景。

```java
MqttServer.create()
    .httpApiListener(builder -> builder
        .serverNode(18083)
        .authFilter(new TokenAuthFilter("X-API-Key", "", myValidator))  // X-API-Key: xxx
    )
    .start();
```

构造器签名:

| 构造器 | 解析形式 | 说明 |
| --- | --- | --- |
| `new TokenAuthFilter(validator)` | `Authorization: Bearer xxx` | 默认 header + Bearer |
| `new TokenAuthFilter(headerName, validator)` | `<headerName>: Bearer xxx` | 自定义 header + Bearer |
| `new TokenAuthFilter(headerName, scheme, validator)` | `<headerName>: <scheme> xxx` | 完全自定义 |
| `new BasicAuthFilter(username, password)` | `Authorization: Basic base64` | Basic 等价于 `TokenAuthFilter("authorization", "Basic", BasicAuthValidator)` |

校验失败返回 401,并设置 `WWW-Authenticate: <scheme> realm="Mica mqtt realm"` 响应头。
