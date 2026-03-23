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

## 集群架构

```
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
+---------v---------                   ---------v--------
|   Node 2:1883      |                   |   Node 3:1883    |
|   Cluster:9002    |                   |   Cluster:9003   |
+---------v--------                   ---------v---------
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
