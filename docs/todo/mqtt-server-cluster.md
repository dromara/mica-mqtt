# mica-mqtt-broker 集群实现文档

> **注意**：本文档描述的是 mica-mqtt-broker 模块的实际实现，与早期的设计方案可能存在差异。

## 1. 方案概述

### 1.1 目标
将现有的 mica-net 集群能力引入到 mica-mqtt-server 中，实现多个 MQTT Broker 实例之间的：
- 节点发现与互联
- 客户端会话信息同步
- 跨节点消息路由与转发
- 订阅信息共享
- 集群状态监控

### 1.2 技术方案
基于 **mica-net core cluster** 提供的 TCP 集群能力，实现轻量级、低延迟的 Broker 集群方案。

**优势：**
- 无需额外依赖（Redis、Kafka 等）
- 直接基于 mica-net 框架，集成简单
- TCP 直连，延迟低
- 适合中小规模集群（3-10 节点）

**适用场景：**
- 局域网或同机房部署
- 需要高性能、低延迟的场景
- 无外部消息中间件的环境

---

## 2. 架构设计

### 2.1 集群拓扑
```
                    ┌─────────────┐
                    │ Seed Node 1 │
                    └──────┬──────┘
                           │
           ┌────────────────┼────────────────┐
           │                │                │
     ┌─────▼──────┐  ┌─────▼──────┐  ┌─────▼──────┐
     │   Node 2   │  │   Node 3   │  │   Node 4   │
     └────────────┘  └────────────┘  └────────────┘
```

- **种子节点（Seed Members）**：预配置的集群成员列表，用于初始节点发现
- **动态加入**：新节点启动后，连接任意种子节点即可加入集群
- **全连接拓扑**：所有节点两两连接（适合小规模集群）

### 2.2 核心组件

```
mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/
└── cluster/                          # 集群包
    ├── MqttClusterConfig.java       # 集群配置
    ├── MqttClusterManager.java      # 集群管理器（主入口）
    ├── MqttClusterBrokerCreator.java # Broker 创建器
    ├── ClusterMqttSessionManager.java # 集群会话管理器（装饰器模式）
    ├── ClusterMqttConnectStatusListener.java # 连接状态监听
    ├── message/                      # 集群消息类型
    │   ├── ClusterMessage.java      # 集群消息基类
    │   ├── MessageType.java         # 消息类型枚举
    │   ├── ClientConnectMessage.java # 客户端连接通知
    │   ├── ClientDisconnectMessage.java # 客户端断开通知
    │   ├── SubscribeNotifyMessage.java # 订阅通知
    │   ├── UnsubscribeNotifyMessage.java # 取消订阅通知
    │   ├── PublishForwardMessage.java  # 消息转发请求
    │   ├── StateSyncResponseMessage.java # 状态同步响应
    │   └── GenericClusterMessage.java # 通用消息
    └── dispatcher/
        └── ClusterMessageDispatcher.java # 集群消息分发器
```

### 2.3 消息路由流程

#### 场景 1：客户端 A 发布消息到本地订阅者
```
Client A (Node1)  ─publish─→  Node1  ─local deliver─→  Client B (Node1)
```
**处理：** 本地转发，无需集群通信

#### 场景 2：客户端 A 发布消息到远程订阅者
```
Client A (Node1)  ─publish─→  Node1  ─cluster forward─→  Node2  ─local deliver─→  Client C (Node2)
```
**处理步骤：**
1. Node1 收到 Client A 的 PUBLISH 消息
2. ClusterMessageDispatcher 拦截消息，查询全局订阅表
3. 发现 Node2 上有客户端订阅了该 topic
4. 向 Node2 发送 PublishForwardMessage
5. Node2 收到后，在本地查询订阅表并分发给客户端

---

## 3. 详细设计

### 3.1 集群配置（MqttClusterConfig）

```java
public class MqttClusterConfig {
    // 是否启用集群
    private boolean enabled = false;

    // 集群监听地址和端口（用于集群节点间通信）
    private String clusterHost = "127.0.0.1";
    private int clusterPort = 9000;

    // 种子节点列表（格式：host:port）
    private List<String> seedMembers = new ArrayList<>();

    // 集群名称（相同集群名称的节点才能互联）
    private String clusterName = "mica-mqtt-cluster";

    // 集群心跳间隔（毫秒）
    private long heartbeatInterval = 5000;

    // 节点失联超时（毫秒）
    private long nodeTimeout = 15000;
}
```

### 3.2 集群消息协议

#### 消息格式
```java
public abstract class ClusterMessage implements Serializable {
    private String messageId;      // 消息唯一ID
    private String sourceNode;     // 源节点名称
    private long timestamp;        // 时间戳
    private MessageType type;      // 消息类型
}

public enum MessageType {
    CLIENT_CONNECT,        // 客户端连接通知
    CLIENT_DISCONNECT,     // 客户端断开通知
    SUBSCRIBE_NOTIFY,      // 订阅通知
    UNSUBSCRIBE_NOTIFY,    // 取消订阅通知
    PUBLISH_FORWARD,       // 消息转发
    HEARTBEAT,             // 心跳
    NODE_JOIN,             // 节点加入
    NODE_LEAVE,            // 节点离开
    STATE_SYNC_REQUEST,    // 状态同步请求
    STATE_SYNC_RESPONSE    // 状态同步响应
}
```

#### 关键消息类型

**PublishForwardMessage（消息转发）**
```java
public class PublishForwardMessage extends ClusterMessage {
    private String topic;           // 主题
    private byte[] payload;         // 消息体
    private int qos;               // QoS 级别
    private boolean retain;         // 是否保留
}
```

**SubscribeNotifyMessage（订阅通知）**
```java
public class SubscribeNotifyMessage extends ClusterMessage {
    private String clientId;        // 客户端ID
    private String nodeId;          // 节点ID
    private List<Subscribe> subscriptions; // 订阅列表
}
```

### 3.3 Session 管理器（装饰器模式）

`ClusterMqttSessionManager` 包装了原有的 `IMqttSessionManager`：

```java
public class ClusterMqttSessionManager implements IMqttSessionManager {
    private final IMqttSessionManager delegate;
    private final MqttClusterManager clusterManager;
    private final ConcurrentHashMap<String, String> clientNodeMap;  // clientId -> nodeId

    // 核心方法
    public String getClientNode(String clientId);
    public void registerRemoteClient(String clientId, String nodeId);
    public void removeRemoteClient(String clientId);
    public void clearNodeClientsAndSubscriptions(String nodeId);
    public void syncRemoteSubscriptions(String clientId, String nodeId, List<Subscribe> subscriptions);
    public List<Subscribe> searchAllSubscribe(String topic);  // 获取所有订阅（含远程）
}
```

### 3.4 消息分发器（ClusterMessageDispatcher）

```java
public class ClusterMessageDispatcher extends BaseMessageHandler {
    // 拦截 UP_STREAM 消息
    // 1. 查找所有订阅者（含远程）
    // 2. 按节点分组
    // 3. 向远程节点转发（O(1) 网络开销：每个节点只发一次）
    // 4. 返回 true 继续本地分发
}
```

### 3.5 序列化方案

采用 **Hessian** 序列化（基于 dubbo hessian-lite）：

```java
private byte[] serialize(ClusterMessage msg) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Hessian2Output out = new Hessian2Output(bos);
    out.writeObject(msg);
    out.flush();
    return bos.toByteArray();
}

private ClusterMessage deserialize(byte[] data) {
    ByteArrayInputStream bis = new ByteArrayInputStream(data);
    Hessian2Input in = new Hessian2Input(bis);
    return (ClusterMessage) in.readObject();
}
```

**优势：**
- 性能比 Java 原生序列化快 3-5x
- 体积小 30-50%
- 自动支持多态
- 无反序列化安全漏洞

### 3.6 状态同步策略

采用 **懒加载同步**策略：
- 新节点加入时**不主动同步数据**
- 当收到 PUBLISH 时，通过 `searchAllSubscribe` 按需查找远程订阅者并转发
- 避免大数据量同步导致的 OOM 和网络包过大问题

---

## 4. 使用示例

### 4.1 Java API 配置

```java
MqttServer server = MqttBroker.create()
    .getServerCreator()
    .nodeName("node-1")
    .port(1883)
    .clusterConfig(new MqttClusterConfig()
        .enabled(true)
        .clusterHost("192.168.1.10")
        .clusterPort(9000)
        .seedMembers(Arrays.asList(
            "192.168.1.10:9000",
            "192.168.1.11:9000",
            "192.168.1.12:9000"
        ))
    )
    .start();
```

### 4.2 Spring Boot 配置

```yaml
mqtt:
  server:
    name: mqtt-broker-1
    node-name: node-1
    port: 1883
    cluster:
      enabled: true
      host: 192.168.1.10
      port: 9000
      name: mica-mqtt-cluster
      heartbeat-interval: 5000
      node-timeout: 15000
      seed-members:
        - 192.168.1.10:9000
        - 192.168.1.11:9000
        - 192.168.1.12:9000
```

---

## 5. 注意事项与限制

### 5.1 网络要求
- **低延迟网络**：建议同机房部署（延迟 < 5ms）
- **防火墙配置**：开放集群端口（默认 9000-9099）
- **带宽规划**：根据消息吞吐量预估集群间流量

### 5.2 集群规模
- **推荐节点数**：3-7 个节点
- **最大节点数**：不超过 10 个（全连接拓扑限制）
- **超大规模**：建议使用 Redis/Kafka 消息分发方案

### 5.3 数据一致性
- **最终一致性**：集群运行期间采用异步广播，存在短暂数据不一致窗口
- **会话接管**：如果 Client ID 发生跨节点重连，需要额外处理
- **保留消息**：各节点在内存中保存副本快照（暂不支持跨节点共享）

### 5.4 故障恢复
- **节点故障级联清理**：当收到 `NODE_LEAVE` 事件时，存活节点会自动清理该宕机节点上的所有远程客户端及订阅信息
- **脑裂问题**：当前方案不处理，建议通过网络隔离避免

---

## 6. 功能检查清单

### 6.1 节点发现与集群基础管理
- [x] 基于 TCP 的节点互联与发现（通过 t-io cluster 实现）
- [x] 节点离开时的状态清理（`NODE_LEAVE` 事件处理）
- [ ] 脑裂（Split-Brain）检测与自动恢复机制

### 6.2 客户端会话与状态同步
- [x] 客户端连接/断开事件的集群广播
- [ ] 集群级会话接管（Client Takeover）
- [ ] 离线会话状态漫游
- [ ] 飞行中消息同步（In-Flight Messages）

### 6.3 消息路由与订阅分发
- [x] 订阅/取消订阅状态全网实时同步
- [x] 跨节点 Publish 消息按需路由转发
- [ ] 共享订阅（Shared Subscriptions `$share`）

### 6.4 遗嘱与保留消息
- [ ] 保留消息的集群共享与存储
- [ ] 遗嘱消息的集群同步与触发代发

### 6.5 可观测性与高级特性
- [ ] 集群级别的统一指标监控 API
- [ ] 全局限流控制协调机制

---

## 7. 后续优化方向

### 7.1 功能增强
- [ ] 集群监控 API（节点状态、流量统计）
- [ ] 动态节点发现（ZooKeeper/Consul 集成）
- [ ] 客户端会话持久化（支持节点故障转移）

### 7.2 性能优化
- [ ] 消息批量转发（减少网络开销）
- [ ] 订阅表索引优化
- [ ] 零拷贝消息转发

### 7.3 运维工具
- [ ] 集群管理 Web 控制台
- [ ] 集群健康检查脚本
- [ ] 监控指标导出（Prometheus）

---

**文档版本：** v2.0
**更新日期：** 2026-03-18
**状态：** 已实现
