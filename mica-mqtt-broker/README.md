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
    .port(1883)
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
    .port(1883);

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

// 4. 集群消息发布（自动路由到所有订阅者所在的节点）
clusterManager.publish("/test/cluster/topic", "hello cluster".getBytes(), 0, false);

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

### MqttClusterConfig

集群配置（支持 Fluent API）：

| 方法 | 说明 | 默认值 |
|------|------|--------|
| `enabled(boolean)` | 是否启用集群 | false |
| `clusterHost(String)` | 集群通信监听地址 | 127.0.0.1 |
| `clusterPort(int)` | 集群通信监听端口 | 9000 |
| `seedMembers(List<String>)` | 种子节点列表 | [] |

### MqttClusterManager

集群管理器，提供以下方法：

```java
// 集群消息发布（自动按 topic 路由到有订阅者的节点）
void publish(String topic, byte[] payload, int qos, boolean retain)

// 发送消息到指定节点
void sendToNode(String nodeId, ClusterMessage clusterMsg)

// 广播消息到所有集群节点
void broadcast(ClusterMessage clusterMsg)

// 获取远程节点列表
Collection<Node> getRemoteMembers()
```

## 规则引擎

broker 内置轻量规则引擎：按 topic 匹配消息后，在 action 线程池上顺序执行一组动作（转发、HTTP 回调、落库、告警…），规则支持运行时增删改查。

### 快速开始

```java
MqttClusterBrokerCreator creator = MqttBroker.create()
    .clusterConfig(MqttClusterConfig.enabled(true).clusterPort(9001));
MqttServer server = creator.build();

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

## 测试示例

详见 `src/test/java/org/dromara/mica/mqtt/broker/cluster/` 目录下的测试类：

- `ClusterTestNode1.java` - 集群节点1启动示例
- `ClusterTestNode2.java` - 集群节点2启动示例
- `ClusterTestNode3.java` - 集群节点3启动示例
- `MqttClusterIntegrationTest.java` - 集群集成测试
