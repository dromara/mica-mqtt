# mica-mqtt-broker

基于 mica-net 集群实现的 MQTT Broker 模块，支持集群部署和跨节点消息路由。

## 添加依赖

```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-broker</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

## 快速开始

### 单节点模式

```java
MqttServer mqttServer = MqttServer.create()
    .enableMqtt(1883)
    .start();

// 发送给某个客户端
mqttServer.publish("clientId", "/test/topic", "hello".getBytes());

// 发送给所有订阅者
mqttServer.publishAll("/test/topic", "hello".getBytes());

mqttServer.stop();
```

### 集群模式

```java
// 1. 创建 MQTT Server 配置
MqttServerCreator serverCreator = MqttServer.create()
    .name("mqtt-cluster-node-1")
    .nodeName("127.0.0.1:9001")  // 节点唯一标识
    .enableMqtt(1883);

// 2. 配置集群
MqttClusterConfig clusterConfig = new MqttClusterConfig()
    .enabled(true)
    .clusterHost("127.0.0.1")
    .clusterPort(9001)
    .seedMembers(Arrays.asList("127.0.0.1:9001", "127.0.0.1:9002", "127.0.0.1:9003"));

// 3. 创建并启动集群 Broker
MqttClusterBrokerCreator brokerCreator = MqttBroker.create(serverCreator)
    .clusterConfig(clusterConfig);

MqttServer mqttServer = brokerCreator.start();
MqttClusterManager clusterManager = brokerCreator.getClusterManager();

// 4. 消息发布：走标准 MQTT 服务端 API，跨节点路由由发布管线自动完成
//    （订阅者在哪个节点，消息就转发到哪个节点）
mqttServer.publishAll("/test/cluster/topic", "hello cluster".getBytes(), MqttQoS.QOS0, false);

// 5. 停止：会一并释放集群端口并向其它节点广播下线
mqttServer.stop();
```

## 核心 API

### MqttBroker

创建集群 Broker 的入口类：

```java
// 方式一：使用现有的 MqttServerCreator
MqttClusterBrokerCreator creator = MqttBroker.create(mqttServerCreator);

// 方式二：创建新的 MqttServerCreator
MqttClusterBrokerCreator creator = MqttBroker.create();
```

> **集群模式请用 `start()`**：`build()` 只完成装配（会话管理器、消息存储、发布管线），**不会建立集群连接** —— 集群端口绑定、节点发现、心跳、状态同步都在 `MqttClusterManager#start()` 内完成。集群模式下调用 `build()` 会抛 `IllegalStateException`，避免节点静默退化成单机（MQTT 正常、跨节点消息全部不投递且无任何报错）。`build()` 仅适用于非集群场景。

### 构建与启动

| 方法 | 作用 | 集群模式 |
|---|---|---|
| `build()` | 只装配，不启动任何服务 | 抛 `IllegalStateException` |
| `start()` | 装配 + 启动（集群连接 + MQTT 服务端） | 唯一正确入口 |

`build()` 与 `start()` 均幂等：重复调用返回同一个实例，不会重复绑定端口或重复注册流水线处理器。

### MqttClusterConfig

集群配置（支持 Fluent API）：

| 方法 | 说明 | 默认值 |
|------|------|--------|
| `enabled(boolean)` | 是否启用集群 | false |
| `clusterHost(String)` | 集群通信监听地址 | 127.0.0.1 |
| `clusterPort(int)` | 集群通信监听端口 | 9000 |
| `seedMembers(List<String>)` | 种子节点列表 | [] |

### MqttClusterManager

集群管理器。**跨节点路由不需要直接调用它** —— 用标准服务端 API
（`mqttServer.publishAll(...)`）发布即可，发布管线会自动把消息转发到订阅者所在节点。

```java
// 发送集群消息到指定节点
boolean sendToNode(String nodeId, ClusterMessage clusterMsg)

// 广播集群消息到所有集群节点
void broadcast(ClusterMessage clusterMsg)

// 获取集群节点 id 集合（含本节点）
Set<String> getClusterNodeIds()

// 集群拓扑是否健康（全互联）
boolean isTopologyHealthy()

// 停止集群连接（不触碰 MQTT 服务端），关闭钩子内部使用
void stopCluster()
```

## 规则引擎

broker 内置轻量规则引擎：按 topic 匹配消息后，在 action 线程池上顺序执行一组动作（转发、HTTP 回调、落库、告警…），规则支持运行时增删改查。

### 快速开始

```java
MqttClusterBrokerCreator creator = MqttBroker.create()
    .clusterConfig(new MqttClusterConfig().enabled(true).clusterPort(9001));
MqttServer server = creator.start();

RuleManager rules = creator.getRuleManager();
rules.addRule(Rule.builder()
    .id("rule-001")
    .name("temp-to-cloud")
    .topicFilter("sensor/+/temperature")
    .codecType("json")
    .when("payload.temperature > 80")
    .timeoutMs(3000)
    .addAction(ActionRef.builder("mqtt")
        .prop("host", "cloud-mqtt.example.com")
        .prop("port", 1883)
        .prop("topicTemplate", "cloud/{clientId}/{topic}")
        .prop("qos", 1)
        .build())
    .addAction(ActionRef.builder("http")
        .prop("url", "https://ingest.example.com/temp")
        .prop("contentType", "application/json")
        .build())
    .build());
```

### 规则文件加载

```java
// JSON / YAML 规则文件，文件变化后自动热加载
rules.addLoader(new YamlRuleLoader(Paths.get("rules.yml")));
rules.start();
```

YAML 格式：

```yaml
rules:
  - id: rule-001
    name: temp-alert
    topicFilter: device/+/sensor
    codec: json
    when: "payload.temperature > 80"
    timeoutMs: 5000
    actions:
      - type: http
        url: https://oapi.dingtalk.com/robot/send?access_token=xxx
        method: POST
```

> YAML 加载器依赖 SnakeYAML：Spring Boot 应用已传递引入，纯 Java 用户需自行引入 `org.yaml:snakeyaml`。

### 内置能力

| 类别 | 名称 | 说明 |
|---|---|---|
| Action | `log` / `mqtt` / `http` | 记录日志、转发到另一台 MQTT、HTTP 回调 |
| Action 模板 | `publish` / `store` / `alert` / `webhook` | 重新发布、持久化（memory / file）、告警、webhook 推送 |
| Matcher | `topic` / `header` / `jsonPath` / `compound` / `aviator` | topic 命中即放行、header 条件、JSONPath-lite 条件、组合条件、表达式条件 |
| Codec | `raw` / `string` / `json` | payload 解码器 |
| SPI | `ActionFactory` / `RuleMatcherFactory` / `PayloadCodecFactory` / `RuleLoaderFactory` / `RuleStoreFactory` / `AlertNotifierFactory` | 均通过 `META-INF/services` 注册 |

### 执行模型

- 规则命中后，整条 action 链作为**一个任务**提交到 `rule-action-pool`（默认 `2*CPU` / `32*CPU` / 队列 1024），**IO 线程不执行任何动作**，慢后端不阻塞消息分发；
- 链内按声明顺序执行，`stopOnError` 为 `true` 时失败即中断后续；
- `timeoutMs`（默认 5000ms）超时后中断当前动作并跳过剩余动作，记一次失败指标；
- 线程池规格可用 `MqttClusterConfig#actionPoolCoreSize` / `actionPoolMaxSize` / `actionPoolQueueSize` 调整。

详细设计见 [docs/todo/mqtt-broker-rules-engine.md](../docs/todo/mqtt-broker-rules-engine.md)。

## 集群架构

```lua
                   +------------------+
                   |   MQTT Client    |
                   +--------+---------+
                            |
                   +--------v---------+
                   |   Node 1:1883    |
                   |   Cluster:9001   |
                   +--------+---------+
                            |
          +-----------------+-----------------+
          |                                   |
+---------v-------+                  +--------v--------+
|  Node 2:1883    |                  |   Node 3:1883   |
|  Cluster:9002   |                  |   Cluster:9003  |
+---------v-------+                  +--------v--------+
```

- 节点间通过 mica-net 集群进行通信
- 客户端连接任意节点，消息自动路由到订阅者所在节点
- 支持状态同步，新节点加入时自动同步已有客户端和订阅信息

## 测试

单元测试随 `mvn test` 执行；三个 `*IT` 集成测试类不在 surefire 默认匹配范围内，需显式激活对应 profile：

| 测试类 | 说明 | 执行方式 |
|---|---|---|
| `MqttClusterTest.java`、`MqttClusterIntegrationTest.java` | 集群路由与共享订阅集成测试 | `mvn test`（默认执行） |
| `ClusterMqttIT.java` | 多节点集群端到端测试 | `mvn test -P cluster-integration` |
| `ClusterProcessChaosIT.java` | 节点进程混沌测试 | `mvn test -P cluster-chaos` |
| `ClusterPerformanceBenchmarkIT.java` | 10w 级热路径性能基准 | `mvn test -P cluster-benchmark` |

规则引擎的测试在 `src/test/java/org/dromara/mica/mqtt/broker/rule/` 目录下，随 `mvn test` 一并执行。
