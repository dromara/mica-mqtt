# mica-mqtt-broker 集群实现文档

> **注意**：本文档描述的是 mica-mqtt-broker 模块的实际实现，与早期的设计方案可能存在差异。
>
> **配套文档**（按演进路线阅读）：
> - `mqtt-server-cluster.md`（本文）— 基础集群拓扑、消息协议、Session 管理
> - `mqtt-server-cluster-routing.md` — V2 路由层（EMQX dispatcher 风格）+ 共享订阅
> - `mqtt-server-cluster-storage.md` (v1.1) — V3 持久化层（H2 MVStore 统一引擎）

## 1. 方案概述

### 1.1 目标
将现有的 mica-net 集群能力引入到 mica-mqtt-server 中，实现多个 MQTT Broker 实例之间的：
- 节点发现与互联
- 客户端会话信息同步
- 跨节点消息路由与转发
- 订阅信息共享
- 集群状态监控

### 1.2 技术方案
基于 **mica-net cluster** 提供的 TCP 集群能力（底层基于 t-io），上层集群协议和消息处理逻辑**完全自研**。

**核心依赖：**
- `mica.server.cluster.core.ClusterApi` - mica-net 集群 API
- `mica.server.cluster.core.ClusterImpl` - mica-net 集群实现
- `mica.server.cluster.message.ClusterDataMessage` - mica-net 集群消息载体

**自研部分：**
- 集群消息类型定义（`ClusterMessage` 系列）
- 消息编解码（`ClusterMessageSerializer`）
- 集群会话管理（`ClusterMqttSessionManager`）
- 消息路由与转发（`ClusterMessageDispatcher`、`ClusterPublishHandler`）

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
```lua
                ┌─────────────┐
                │ Seed Node 1 │
                └──────┬──────┘
                       │
      ┌────────────────┼────────────────┐
      │                │                │
┌─────▼──────┐   ┌─────▼──────┐   ┌─────▼──────┐
│   Node 2   │   │   Node 3   │   │   Node 4   │
└────────────┘   └────────────┘   └────────────┘
```

- **种子节点（Seed Members）**：预配置的集群成员列表，用于初始节点发现
- **动态加入**：新节点启动后，连接任意种子节点即可加入集群
- **全连接拓扑**：所有节点两两连接（适合小规模集群）

### 2.2 核心组件

```
mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/
└── cluster/
    ├── MqttBroker.java                    # Broker 入口
    ├── config/                            # 集群配置与创建器
    │   ├── MqttClusterConfig.java         # 集群配置
    │   └── MqttClusterBrokerCreator.java  # Broker 创建器
    ├── core/                              # 集群核心组件
    │   ├── MqttClusterManager.java        # 集群管理器
    │   ├── ClusterMqttSessionManager.java # 集群会话管理器（装饰器模式）
    │   ├── ClusterMqttMessageStore.java   # 集群消息存储（装饰器模式）
    │   └── ClusterMqttConnectStatusListener.java # 连接状态监听
    ├── message/                           # 集群消息类型（V1 已实现 10 个，code 标注于行尾）
    │   ├── ClusterMessage.java            # 集群消息接口
    │   ├── ClusterMessageType.java        # 消息类型枚举 (V1: 1-10, V2: 11-13, V3: 14-21)
    │   ├── ClusterMessageSerializer.java  # 消息序列化器
    │   ├── ClientConnectMessage.java      # 客户端连接通知 (V1, code=1)
    │   ├── ClientDisconnectMessage.java   # 客户端断开通知 (V1, code=2)
    │   ├── SubscribeNotifyMessage.java    # 订阅通知 (V1, code=3)
    │   ├── UnsubscribeNotifyMessage.java  # 取消订阅通知 (V1, code=4)
    │   ├── PublishForwardMessage.java     # 消息转发请求 (V1, code=5)
    │   ├── NodeLeaveMessage.java          # 节点离开通知 (V1, code=6)
    │   ├── StateSyncRequestMessage.java   # 状态同步请求 (V1, code=7)
    │   ├── StateSyncResponseMessage.java  # 状态同步响应 (V1, code=8)
    │   ├── WillMessageNotifyMessage.java  # 遗嘱消息通知 (V1, code=9)
    │   └── RetainMessageNotifyMessage.java # 保留消息通知 (V1, code=10)
    ├── pipeline/                          # 消息管道
    │   ├── ClusterMessageDispatcher.java   # 集群消息分发器
    │   └── ClusterPublishHandler.java      # 发布消息处理器
    └── store/                             # [V3 新增] 存储层
        ├── LocalKvStore.java              # 存储抽象接口
        ├── H2MvStoreImpl.java              # H2 MVStore 实现 (Session/Retain/SharedSub/Inflight)
        ├── RetainIndex.java                # 内存 Skiplist 通配索引
        └── InflightTtlCleaner.java         # QoS 飞行消息 TTL 后台清理线程
```

**V2/V3 新增的协议消息**（在 `ClusterMessageType` 中按枚举值扩展，V1 节点收到新类型会记录 warning 并忽略）：

| 枚举值 | 消息类型 | 所属文档 | 状态 |
|---|---|---|---|
| 11-13 | `SHARED_SUBSCRIBE_NOTIFY` / `SHARED_SUBSCRIBE_REMOVE` / `SHARED_DISPATCH_TO_CLIENT` | routing 文档 | V2 待实现 |
| 14-17 | `SESSION_TAKEOVER_REQUEST/RESPONSE` / `SESSION_MIGRATED_NOTIFY` / `SESSION_DELETE_NOTIFY` | storage 文档 | V3 待实现 |
| 18-19 | `SHARED_SUB_STATE_SYNC` / `SHARED_SUB_TAKEOVER` | storage 文档 | V3 待实现 |
| 20-21 | `RETAIN_REPLICATE` / `RETAIN_QUERY` | storage 文档 | V3 待实现 |

> **v2.9 修正**：原 v2.8 把 V2/V3 编号放在 9-19，与 V1 已实现的 `WILL_MESSAGE(9)` / `RETAIN_MESSAGE(10)` 冲突。本版本起 V1 占 1-10，V2 从 11 起，V3 从 14 起。V2 中不再使用 `SHARED_PUBLISH_FORWARD`（routing v1.2 §3.5 标记为"可选优化"，未列入实现计划；如后续需要可加回 22 号）。**cluster 文档是协议号表的 canonical 来源**，routing/storage 文档必须以本表为准。

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

#### 消息接口
```java
public interface ClusterMessage {
    ClusterMessageType getType();
    void toClusterData(Map<String, String> headers);
    byte[] toPayload();
    void fromClusterData(ClusterDataMessage message);
}
```

#### 消息类型枚举

```java
public enum ClusterMessageType {
    // ===== V1（已实现）=====
    CLIENT_CONNECT(1),          // 客户端连接通知
    CLIENT_DISCONNECT(2),       // 客户端断开通知
    SUBSCRIBE_NOTIFY(3),        // 订阅通知
    UNSUBSCRIBE_NOTIFY(4),      // 取消订阅通知
    PUBLISH_FORWARD(5),         // 消息转发
    NODE_LEAVE(6),              // 节点离开
    STATE_SYNC_REQUEST(7),      // 状态同步请求
    STATE_SYNC_RESPONSE(8),     // 状态同步响应
    WILL_MESSAGE(9),            // 遗嘱消息通知
    RETAIN_MESSAGE(10);         // 保留消息通知

    // ===== V2（routing 文档扩展，待实现，编号 11-13）=====
    SHARED_SUBSCRIBE_NOTIFY(11),
    SHARED_SUBSCRIBE_REMOVE(12),
    SHARED_DISPATCH_TO_CLIENT(13);

    // ===== V3（storage 文档扩展，待实现，编号 14-21）=====
    // 注：以下为规划中的枚举值，Java enum 不允许多段 // 块，
    // 实际落地时需把 V1/V2/V3 合并为单个枚举体，并删去此处的分号。
    SESSION_TAKEOVER_REQUEST(14),
    SESSION_TAKEOVER_RESPONSE(15),
    SESSION_MIGRATED_NOTIFY(16),
    SESSION_DELETE_NOTIFY(17),
    SHARED_SUB_STATE_SYNC(18),
    SHARED_SUB_TAKEOVER(19),
    RETAIN_REPLICATE(20),
    RETAIN_QUERY(21);
}
```

**向后兼容**：旧节点收到新消息类型时记录 warning 并忽略。V2/V3 完整定义见：
- 路由层扩展：`docs/todo/mqtt-server-cluster-routing.md` §5
- 存储层扩展：`docs/todo/mqtt-server-cluster-storage.md` §5

#### 关键消息类型

**PublishForwardMessage（消息转发）**
```java
public class PublishForwardMessage implements ClusterMessage {
    private Message message;    // MQTT 消息体
}
```

**SubscribeNotifyMessage（订阅通知）**
```java
public class SubscribeNotifyMessage implements ClusterMessage {
    private String clientId;           // 客户端ID
    private String nodeId;             // 节点ID
    private List<Subscribe> subscriptions; // 订阅列表
}
```

### 3.3 Session 管理器（装饰器模式）

`ClusterMqttSessionManager` 包装了原有的 `IMqttSessionManager`。V1 状态全在内存，V3 引入 L2 持久化后会在以下位置增加 L2 落点：

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
    public void removeRemoteSubscriptions(String clientId, List<String> topics);
    public List<Subscribe> searchAllSubscribe(String topic);  // 获取所有订阅（含远程）
    public List<Subscribe> getClientSubscriptions(String clientId);
    public Map<String, String> getRemoteClientNodeMap();
    public void syncFullState(Map<String, String> clientNodeMap, Map<String, List<Subscribe>> subscriptionMap);
}
```

**V3 持久化集成点**（参见 storage 文档 §4.1）：

| 当前 V1 行为 | V3 增加 L2 落点 | 触发时机 |
|---|---|---|
| `createSession` 写内存 | 同步写 H2 `mqtt_session` 表 | CONNACK 返回前必须落盘 |
| `removeSession` 删内存 | 同步删 H2 + 广播 `SESSION_DELETE_NOTIFY` | DISCONNECT 时 |
| `getSession` 读内存 | miss 时从 H2 加载 | 跨节点接管场景 |
| `registerRemoteClient` | 写 H2 + 广播 | 远程客户端注册时 |

**V3 跨节点接管协议**（参见 storage 文档 §4.1.3）：

```java
// 协议流程（新增于 V3）
SESSION_TAKEOVER_REQUEST(14),   // 新节点 -> 老节点: { clientId }
SESSION_TAKEOVER_RESPONSE(15),  // 老节点 -> 新节点: { session, pendingInflight }
SESSION_MIGRATED_NOTIFY(16),    // 新节点 -> 全集群: { clientId, newNode }
SESSION_DELETE_NOTIFY(17);      // 任何节点 -> 全集群: { clientId }
```

### 3.4 消息分发器（ClusterMessageDispatcher）

```java
public class ClusterMessageDispatcher extends BaseMessageHandler {
    // V1 行为（当前实现）:
    //   1. 查找所有订阅者（含远程） - 全量广播
    //   2. 按节点分组
    //   3. 向远程节点转发（O(1) 网络开销：每个节点只发一次）
    //   4. 返回 true 继续本地分发
    //
    // 已知问题: 共享订阅同 group 跨节点时消息被多次转发, 见 §5.5
}
```

**V2 演进**（routing 文档 §1.2 详细描述）：改为 EMQX dispatcher 模型，发布者所在节点本地决策"只发给 1 个目标节点"，消除 V1 的重复转发问题。新增协议消息 `SHARED_DISPATCH_TO_CLIENT(13)`，由 `ClusterMessageDispatcher` 的子类 `SharedSubscriptionDispatcher` 处理。

### 3.5 序列化方案

采用 **自定义二进制序列化**（基于 `ClusterMessageSerializer`）：

```java
// 使用 ByteBuffer 进行紧凑的二进制编码
// 消息格式：type(int) + headers + payload
// 传输载体：t-io ClusterDataMessage
```

**优势：**
- 性能比 Java 原生序列化快
- 体积小（定长编码 + UTF-8 字符串）
- 无反序列化安全漏洞
- 与 mica-net cluster 无缝集成

### 3.6 状态同步策略

采用 **主动全量同步**策略：
- 新节点加入时，通过 `STATE_SYNC_REQUEST` 向其他节点请求全量状态
- 其他节点返回 `STATE_SYNC_RESPONSE`，包含完整的客户端映射和订阅信息
- `ClusterMqttSessionManager.syncFullState()` 应用同步数据

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

**V1 现状**（纯内存 + 广播）：
- **最终一致性**：集群运行期间采用异步广播，存在短暂数据不一致窗口
- **会话接管**：如果 Client ID 发生跨节点重连，需要额外处理
- **保留消息**：各节点在内存中保存副本快照（暂不支持跨节点共享）

**V3 改进**（持久化层引入后，参见 storage 文档）：
- Session 一致性：H2 `mqtt_session` 表 + WAL 崩溃安全 + 跨节点 `SESSION_TAKEOVER` 协议
- Retain 一致性：H2 `mqtt_retain` 表 + 内存 `RetainIndex`（ConcurrentSkipListMap）+ 跨节点 `RETAIN_REPLICATE` 协议
- Shared Sub 一致性：H2 `mqtt_shared_sub` 表 + owner/backup 角色 + 跨节点 `SHARED_SUB_STATE_SYNC` 协议
- QoS 1/2 飞行消息：H2 `mqtt_inflight` 表 + 30s 后台 TTL 清理（接受 30s 滞后换 0 第三方依赖）

**V3 关键不变量**（来自 storage 文档 §2.4）：
- Session 写入：必须在 CONNACK 前同步落 H2
- 节点宕机：重启后 L1 必须从 L2 完整恢复，不依赖其他节点

### 5.4 故障恢复

**V1 现状**（纯内存）：
- **节点故障级联清理**：当收到 `NODE_LEAVE` 事件时，存活节点会自动清理该宕机节点上的所有远程客户端及订阅信息
- **脑裂问题**：当前方案不处理，建议通过网络隔离避免
- **数据丢失**：宕机节点的 session / retain / shared sub 全部丢失，重连客户端需重新鉴权

**V3 改进**（持久化层引入后）：
- **节点重启不丢数据**：从本地 H2 恢复 session / retain / shared sub
- **跨节点接管**：客户端 sticky 失败连接到新节点时，新节点通过 `SESSION_TAKEOVER` 协议从老节点拉取 session（含飞行消息）
- **Shared Sub 故障切换**：owner 节点宕机时，backup 节点检测后升级为新 owner（零消息真空，对比 V1 的 15s 真空）
- **降级模式**：L2 启动失败时允许 Broker 启动但记录告警，session 退化为纯内存（保证可用性）

### 5.5 订阅同步设计（V1 全量复制）

**全量复制方案**：所有订阅（普通订阅 + 共享订阅）都被同步到每个节点的 TrieTopicManager，`searchAllSubscribe()` 返回全量订阅者。

**路由流程**：
1. 发布消息时，`ClusterMessageDispatcher` 拦截，调用 `searchAllSubscribe(topic)` 查到所有订阅者
2. 通过 `clientNodeMap` 判断每个订阅者在哪个节点
3. 向所有**远程节点**（有订阅者的非本节点）转发消息
4. 本地订阅者由 Pipeline 后面的 `UpStreamMessageHandler` 本地投递

**已知问题**：当同一 `$share/<group>/` 订阅的客户端分布在不同节点时，消息会被多次转发（每个节点都收到并投递）。这是 V1 全量复制方案的固有局限。

**V2 演进**：routing 文档 §1.2 引入 EMQX dispatcher 模型解决此问题。发布者所在节点本地决策"只发给 1 个目标节点"，新增 `SHARED_DISPATCH_TO_CLIENT(13)` 消息类型。

---

## 6. 功能检查清单

> 状态说明：✅ 已实现 / 🚧 部分实现 / ❌ 未实现
> 路线图：V1 = 当前版本，V2 = routing 文档目标，V3 = storage 文档目标

### 6.1 节点发现与集群基础管理
- [x] V1: 基于 TCP 的节点互联与发现（通过 mica-net cluster 实现）
- [x] V1: 节点离开时的状态清理（`NODE_LEAVE` 事件处理）
- [ ] 脑裂（Split-Brain）检测与自动恢复机制

### 6.2 客户端会话与状态同步
- [x] V1: 客户端连接/断开事件的集群广播
- [x] V1: Client ID 跨节点重连处理（基础）
- [ ] V3: 集群级会话接管（Client Takeover）— `SESSION_TAKEOVER_REQUEST(14)` / `RESPONSE(15)` / `MIGRATED_NOTIFY(16)` / `DELETE_NOTIFY(17)` 协议
- [ ] V3: 离线会话状态漫游 — H2 `mqtt_session` 持久化
- [ ] V3: 飞行中消息同步（In-Flight Messages）— H2 `mqtt_inflight` + 30s TTL 清理

### 6.3 消息路由与订阅分发
- [x] V1: 订阅/取消订阅状态全网实时同步
- [x] V1: 跨节点 Publish 消息按需路由转发
- [🚧] V1: 共享订阅（`$share` / `$queue`）— 全量复制方案，**存在重复转发问题**（同 group 跨节点时多次转发）
- [ ] V2: 共享订阅 dispatcher 模型（EMQX 风格）— `SHARED_DISPATCH_TO_CLIENT(13)` 协议

### 6.4 遗嘱与保留消息
- [x] V1: 保留消息的集群共享与存储 — `ClusterMqttMessageStore` 装饰器 + 广播
- [x] V1: 遗嘱消息的集群备份 — WILL_MESSAGE(9) 同步到所有节点
- [ ] V3: 节点失联后的 will 消息代发（其他节点检测到老节点 NODE_LEAVE 时代为触发）— 协议待规划
- [ ] V3: 保留消息持久化 — H2 `mqtt_retain` + 内存 `RetainIndex` 通配查询
- [ ] V3: 保留消息分片复制 — `RETAIN_REPLICATE(20)` / `RETAIN_QUERY(21)` 协议

### 6.5 持久化能力（V3 新增，参见 storage 文档）
- [ ] H2 MVStore 统一引擎接入（~2MB 单 jar）
- [ ] Session 元数据持久化 + ACID 事务
- [ ] Retain 通配查询（内存 Skiplist 索引）
- [ ] Shared Subscription owner 状态持久化
- [ ] QoS 1/2 飞行消息持久化 + TTL 自动清理
- [ ] 节点重启不依赖其他节点即可恢复

### 6.6 可观测性与高级特性
- [ ] 集群级别的统一指标监控 API
- [ ] 全局限流控制协调机制
- [ ] 存储层 metrics（L2 文件大小、Inflight 滞后堆积、Retain 索引内存）

---

**文档版本：** v2.9
**更新日期：** 2026-06-05
**状态：** V1 已实现（含已知问题）；V2/V3 演进路线已与 routing/storage 文档对齐

**v2.9 变更摘要**（对照 v2.8 修复设计文档检查报告 P0/P1 项）：
- **§2.2 目录树修正**：config/ 从 core/ 拆出；V1 消息文件加 (V1, code=N) 标注
- **§2.2 协议号表修正**：V1 占 1-10，V2 从 11 起（SHARED_SUBSCRIBE_NOTIFY(11) / SHARED_SUBSCRIBE_REMOVE(12) / SHARED_DISPATCH_TO_CLIENT(13)），V3 从 14 起（14-17 session 接管、18-19 shared sub、20-21 retain）。本表为 **canonical** 协议号来源，routing/storage 必须对齐
- **§3.2 枚举块更新**：与协议号表同步；注明 V1 实际有 10 个枚举值
- **§3.3 接管协议代码块**：12-15 → 14-17
- **§3.4 / §5.5 dispatcher ref**：SHARED_DISPATCH_TO_CLIENT(11) → (13)
- **§6 检查清单**：拆分 Will "备份 vs 代发"——V1 只实现备份（WILL_MESSAGE(9) 广播），代发待 V3 规划
- **§6 编号对齐**：V2/V3 状态条目都补全具体协议号
- **去掉 SHARED_PUBLISH_FORWARD**：routing v1.2 §3.5 标记为可选优化，V2 实施时不再使用

**更新日期：** 2026-06-05
**状态：** V1 已实现（含已知问题）；V2/V3 演进路线已与 routing/storage 文档对齐

**v2.8 变更摘要**（与 storage v1.1 / routing 文档同步）：
- 顶部增加"配套文档"导航
- §2.2 组件图：补充 V3 `store/` 子包（`LocalKvStore` / `H2MvStoreImpl` / `RetainIndex` / `InflightTtlCleaner`）
- §3.2 消息类型枚举：补全 V2 (11-13) / V3 (14-21) 共 11 个新类型（v2.9 修正：原 9-19 编号与 V1 WILL_MESSAGE(9)/RETAIN_MESSAGE(10) 冲突）
- §3.3 Session 管理器：标注 V3 L2 落点（create/remove/get/register 四处）
- §3.4 消息分发器：补充 V2 dispatcher 模型切换说明
- §5.3 / §5.4 一致性与故障恢复：增加 V3 改进项（H2 持久化、跨节点接管、Shared Sub 故障切换）
- §5.5 订阅同步：已知问题保留，指向 routing 文档的 V2 解决方案
- §6 检查清单：拆分 V1/V2/V3 三档状态，新增 §6.5 持久化能力专章
- 移除"用 Redis 方案解决"等过时说法
