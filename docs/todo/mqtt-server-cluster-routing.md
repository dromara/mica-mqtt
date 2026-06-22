# mica-mqtt-broker 集群路由与共享订阅设计文档

> **本文档定位**：本文件是 `mqtt-server-cluster.md` 的**路由层专章**，专注于跨节点 PUBLISH 消息路由与 MQTT 5.0 共享订阅（`$share` / `$queue`）的去重投递问题。
>
> **配套文档**（按演进路线阅读，版本已对齐）：
> - `mqtt-server-cluster.md` (v3.0) — 基础集群、V1 已实现能力
> - `mqtt-server-cluster-storage.md` (v1.2) — V3 持久化层（H2 MVStore 统一引擎）
> - **本文档** (v1.2) — V2 路由层（EMQX dispatcher 风格）+ 共享订阅

---

## 1. 方案概述

### 1.1 路由层要解决的问题

集群环境下，PUBLISH 消息需要从发布者所在节点路由到所有订阅者所在的节点。普通（非共享）订阅是简单的扇出问题，V1 的全量广播即可。但 MQTT 5.0 引入的**共享订阅**对路由提出了新要求：

> `$share/<group>/<topic>` 同一个 group 内的订阅者构成一个**消费组**，每条消息**恰好投递一次**给 group 内的一个客户端。

V1 全量广播方案下：

```
group "g1" 订阅了 topic "sensor/temp":
  Node1: clientA (订阅 $share/g1/sensor/temp)
  Node2: clientB (订阅 $share/g1/sensor/temp)
  Node3: clientC (订阅 $share/g1/sensor/temp)

发布者 Node4 publish "sensor/temp"
  -> V1 全量广播: Node1, Node2, Node3 都收到
  -> 每个节点本地投递一次
  -> 客户端 A, B, C 都收到 -> 重复 3 次
```

### 1.2 本文核心思想：EMQX Dispatcher 模型

EMQX 的处理方式优雅而简单——**既然每个节点都持有全量路由表，那决策就在发布者所在的节点本地完成**：

```
发布者 Node4 publish "sensor/temp"
  |
  |- (1) Node4 本地查 topic trie, 得到 group g1 候选列表
  |     {clientA@Node1, clientB@Node2, clientC@Node3}
  |
  |- (2) 用 strategy (如 round_robin) 在本地选 1 个, 假设选 clientB@Node2
  |
  |- (3) clientB 在 Node2 -> 只向 Node2 转发 1 次
  |
  +- (4) Node2 本地投递 -> 1 次
```

**关键优势**：

| 维度 | V1 全量广播 | EMQX Dispatcher |
|---|---|---|
| 跨节点转发次数 | N（每个有订阅者的节点） | **1**（只发给选中的节点） |
| 投递次数 | N | **1** |
| 单点故障 | 无 | **无**（没有 owner 概念） |
| 复杂度 | 低 | **中**（需 strategy） |
| 状态开销 | 全量复制 | 全量复制（V1 已有） |

### 1.3 与 storage 文档的关系

路由层**不依赖**存储层，但存储层能为路由层带来：

- **Shared subscription 状态持久化**（H2 `mqtt_shared_sub` 表，参见 storage 文档 §4.4）
- **节点宕机后路由表快速恢复**（L1 从 L2 加载，参见 storage 文档 §4.2.4 Retain 内存索引的 reload 机制）
- **Owner 故障自动切换**（参见 storage 文档 §4.4.4，dispatcher 模型本身去除了 owner 单点）

本文假设存储层**未启用**，所有状态在内存中。开启存储层后只需增加持久化步骤，不改变路由逻辑。

### 1.4 版本路线图对齐

| 集群演进层 | 本文档对应 | 触发条件 | 解决什么问题 |
|---|---|---|---|
| **V1** | 基础集群文档 | 起步状态 | TCP 互联、消息广播、Session 同步 |
| **V2** | **本文档**（routing） | 共享订阅出现重复投递 | EMQX dispatcher 模型 + 5 种 strategy |
| **V3** | storage 文档 | 节点宕机数据丢失 / 需要故障接管 | H2 持久化 + 跨节点 SESSION_TAKEOVER |

> **V2 和 V3 是正交的**：可以只做 V2（纯内存 dispatcher），也可以只做 V3（H2 持久化但保留 V1 全量广播），也可以两个都做（推荐演进路径）。

### 1.5 与 cluster v3.0 / storage v1.2 的协议消息对齐

本文档新增的 3 个协议消息已在 cluster v3.0 的 `ClusterMessageType` 枚举中注册：

| 枚举值 | 名称 | 方向 | 用途 |
|---|---|---|---|
| 11 | `SHARED_DISPATCH_TO_CLIENT` | dispatcher → 目标节点 | **dispatcher 模型核心**，本地决策后只发给 1 个目标节点 |
| 12 | `SHARED_SUBSCRIBE_NOTIFY` | 任意 → 任意 | 共享订阅注册广播 |
| 13 | `SHARED_SUBSCRIBE_REMOVE` | 任意 → 任意 | 共享订阅取消广播 |

storage 文档（v1.2）新增的 7 个协议消息（14-20）与本文档**无功能重叠**，可独立实现。

---

## 2. 架构设计

### 2.1 核心组件

```
mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/
└── cluster/
    ├── MqttBroker.java                          # Broker 入口
    ├── config/
    │   ├── MqttClusterConfig.java               # 集群配置
    │   ├── MqttClusterBrokerCreator.java        # Broker 创建器
    │   └── MqttStorageConfig.java               # V3 存储配置
    ├── core/
    │   ├── MqttClusterManager.java              # 集群管理器
    │   ├── ClusterMqttSessionManager.java       # 会话装饰器 (V1)
    │   ├── ClusterMqttMessageStore.java         # 消息存储装饰器 (V1)
    │   └── ClusterStorage.java                  # V3 持久化协调器
    ├── message/
    │   ├── ClusterMessage.java                  # 消息接口
    │   ├── ClusterMessageType.java              # 枚举 (扩展后)
    │   ├── ClusterMessageSerializer.java        # 序列化器
    │   ├── ... (ClientConnect / Disconnect / SubscribeNotify 等)
    │   ├── PublishForwardMessage.java           # 普通转发
    │   └── SharedDispatchToClientMessage.java   # [已实现] dispatcher -> 目标节点
    ├── pipeline/
    │   ├── ClusterMessageDispatcher.java        # [已重写] dispatcher 模型
    │   ├── ClusterPublishHandler.java           # 发布消息处理器
    │   └── strategy/                            # [已实现] 策略目录
    │       ├── SharedSubscriptionStrategy.java  # 策略接口
    │       ├── RandomStrategy.java              # 随机
    │       ├── RoundRobinStrategy.java          # 轮询
    │       ├── LocalFirstStrategy.java          # 本地优先
    │       ├── HashClientStrategy.java          # 哈希亲和
    │       └── StickyStrategy.java              # 黏住
    ├── metrics/
    │   └── ClusterMetrics.java                  # 集群指标采集
    └── store/                                   # V3 存储层
        ├── LocalKvStore.java                    # 存储抽象接口
        ├── H2MvStoreImpl.java                   # H2 MVStore 实现
        └── ... (SessionStore / InflightStore / RetainIndex 等)
```

### 2.2 关键数据流

#### 场景 1：普通订阅（无共享）

```
Client A (Node1) publish "sensor/temp"
  |
  |- Node1: ClusterMessageDispatcher.handle()
  |   |- searchAllSubscribe("sensor/temp") -> 普通订阅者
  |   |   {clientB@Node1, clientC@Node2}
  |   |
  |   |- 本地: clientB 投递
  |   |
  |   |- 远程: 向 Node2 发送 PublishForwardMessage { topic, msg }
  |   |   节点 C 投递
  |   |
  |   +- (注意: 普通订阅不涉及 strategy)
```

#### 场景 2：共享订阅（dispatcher 模型核心）

```
Client A (Node1) publish "sensor/temp"
  |
  |- Node1: ClusterMessageDispatcher.handle()
  |   |
  |   |- 分离普通订阅 (本地投递或转发)
  |   |
  |   |- 分离共享订阅:
  |   |   searchAllSharedSubscribe("sensor/temp") -> 候选
  |   |   {clientX@Node2, clientY@Node3, clientZ@Node2}  // group "g1"
  |   |
  |   |- strategy.pick(candidates, msg) -> 选 1 个
  |   |   假设 RoundRobin 选 clientY@Node3
  |   |
  |   |- 只向 Node3 发送 SharedDispatchToClientMessage
  |   |     { clientId=clientY, topic, msg }
  |   |
  |   +- Node3 收到后, 查找本地 clientY 的订阅 context, 投递
```

#### 场景 3：发布者自己也是订阅者

```
Client A (Node1) 既是发布者, 又是共享订阅组 g1 的成员
  |
  |- Node1 收到 A 的 publish
  |- 候选列表中包含 A 自己 (A@Node1)
  |- strategy 完全可能选中 A
  |- 选中后直接在本地投递
  +- 不需要跨节点转发
```

### 2.3 与 V1 集群广播的关系

| 机制 | V1（已实现） | V2（本设计） |
|---|---|---|
| 订阅广播 | 同步全集群 | **不变**（仍需） |
| 客户端映射同步 | 同步 | **不变** |
| 普通 publish 转发 | 广播到所有有订阅者节点 | **不变** |
| 共享 publish 转发 | 广播（**bug：重复**） | **只发给选中节点** |
| 节点加入状态同步 | 全量 STATE_SYNC | **不变** |

**结论**：V1 的基础设施（同步、广播、状态同步）全部保留，**只改 dispatcher 的路由决策逻辑**。

---

## 3. 详细设计

### 3.1 Topic Trie 状态维护（V1 已有，本文不重述）

每个节点维护：

```
TrieTopicManager {
    Map<topic, Set<Subscribe>>    // 精确 topic
    Trie<Subscribe>                // 通配 topic (含 + 和 #)
    Map<group, Set<Subscribe>>    // 共享订阅组
}
```

集群通过 SUBSCRIBE_NOTIFY / UNSUBSCRIBE_NOTIFY 广播到所有节点，保持一致。

### 3.2 ClusterMessageDispatcher 重构

#### 接口签名

```java
public class ClusterMessageDispatcher extends BaseMessageHandler {

    private final SharedSubscriptionStrategy strategy;  // 注入

    @Override
    public boolean handle(MqttPublishMessage msg, String topic) {
        // 1. 普通订阅: 原有逻辑
        deliverNormal(topic, msg);

        // 2. 共享订阅: 全新 dispatcher 模型
        deliverShared(topic, msg);

        return true;
    }
}
```

#### 普通订阅投递

```java
private void deliverNormal(String topic, MqttPublishMessage msg) {
    // 1. 本地普通订阅
    List<Subscribe> locals = localManager.searchAllSubscribe(topic, false);
    for (Subscribe s : locals) {
        s.deliver(msg);
    }

    // 2. 远端普通订阅 (按节点分组)
    Map<String, List<Subscribe>> grouped = remoteManager
        .searchAllSubscribe(topic, false)
        .stream()
        .collect(Collectors.groupingBy(s -> clientNodeMap.get(s.getClientId())));

    // 3. 每个远端节点发一次
    for (var entry : grouped.entrySet()) {
        if (!entry.getKey().equals(myNodeId)) {
            clusterSend(entry.getKey(), new PublishForwardMessage(topic, msg));
        }
    }
}
```

#### 共享订阅 dispatcher 投递

```java
private void deliverShared(String topic, MqttPublishMessage msg) {
    // 1. 收集所有共享订阅者 (含本地+远端)
    List<Subscribe> candidates = new ArrayList<>();
    candidates.addAll(localManager.searchAllSubscribe(topic, true));   // shared only
    candidates.addAll(remoteManager.searchAllSubscribe(topic, true));  // shared only

    if (candidates.isEmpty()) return;

    // 2. 按 group 分组 (一个 topic 可能属于多个 group)
    Map<String, List<Subscribe>> byGroup = candidates.stream()
        .collect(Collectors.groupingBy(s -> extractGroup(s.getTopic())));

    // 3. 每个 group 独立选 1 个 subscriber
    for (var entry : byGroup.entrySet()) {
        Subscribe picked = strategy.pick(groupName, candidates, localNodeId, message);
        if (picked == null) continue;

        String homeNode = clientNodeMap.getOrDefault(picked.getClientId(), myNodeId);

        if (homeNode.equals(myNodeId)) {
            // 本地投递
            picked.deliver(msg);
        } else {
            // 跨节点只发一次
            clusterSend(homeNode, new SharedDispatchToClientMessage(
                picked.getClientId(), topic, msg));
        }
    }
}

// 从 $share/<group>/<topic> 提取 group
private String extractGroup(String fullTopic) {
    // 例: "$share/g1/sensor/temp" -> "g1"
    if (fullTopic.startsWith("$share/")) {
        int second = fullTopic.indexOf('/', "$share/".length());
        return fullTopic.substring("$share/".length(), second);
    }
    // $queue/... 同样处理
    return fullTopic;
}
```

### 3.3 SharedSubscriptionStrategy 接口

```java
public interface SharedSubscriptionStrategy {
    /**
     * 从候选订阅者中选 1 个
     *
     * @param groupName   共享订阅组名 (用于 group 级状态)
     * @param candidates  全量候选 (本地+远端, 已带 clientId 和 homeNode)
     * @param localNodeId 当前执行节点的 ID, 用于本地优先策略
     * @param message     当前消息 (用于 hash / packetId 等)
     * @return 选中的订阅者; 返回 null 表示 group 当前无活跃订阅者
     */
    Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message);

    /**
     * 策略名称 (用于配置 & 监控)
     */
    String name();
}
```

### 3.4 内置策略实现

#### RandomStrategy

```java
public class RandomStrategy implements SharedSubscriptionStrategy {
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

    public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public String name() { return "random"; }
}
```

**特点**：
- 最简单，零状态
- 适合订阅者数较多、消息量大、单机处理能力均匀的场景
- 缺点：可能短时间内多次选到同一节点

#### RoundRobinStrategy

```java
public class RoundRobinStrategy implements SharedSubscriptionStrategy {
    // 每个 group 一个计数器, AtomicLong
    private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
        if (candidates.isEmpty()) return null;
        long seq = counters.computeIfAbsent(groupName, k -> new AtomicLong(0)).getAndIncrement();
        return candidates.get((int) (seq % candidates.size()));
    }

    public String name() { return "round_robin"; }
}
```

**特点**：
- 每个 group 内部轮询，**公平**
- 不同 group 独立计数
- 缺点：counter 是**节点本地**的，跨节点不共享（每个 publisher 节点独立计数）
  - 这意味着负载在跨节点粒度上**不保证均衡**
  - 但每个 publisher 节点对自己的消息是公平的

#### LocalFirstStrategy（推荐默认）

```java
public class LocalFirstStrategy implements SharedSubscriptionStrategy {
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

    public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
        if (candidates.isEmpty()) return null;

        // 1. 优先选本节点订阅者
        List<Subscribe> locals = candidates.stream()
            .filter(s -> localNodeId.equals(clientNodeMap.get(s.getClientId())))
            .collect(Collectors.toList());

        if (!locals.isEmpty()) {
            return locals.get(ThreadLocalRandom.current().nextInt(locals.size()));
        }

        // 2. 本节点没有, 远端随机
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public String name() { return "local_first"; }
}
```

**特点**：
- 优先本地投递，**减少跨节点流量**
- mica-mqtt 默认推荐
- 适合订阅者数 > 节点数、且发布者分布均匀的场景

#### HashClientStrategy

```java
public class HashClientStrategy implements SharedSubscriptionStrategy {
    public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
        if (candidates.isEmpty()) return null;
        // 用 packetId 做 hash, 保证同一消息序列去同一节点
        int hash = Math.abs(message.getPacketId() ^ groupName.hashCode());
        return candidates.get(hash % candidates.size());
    }

    public String name() { return "hash_client"; }
}
```

**特点**：
- 同一 (group, packetId 范围) 消息总是去同一节点
- 适合"消息顺序敏感"的业务
- 缺点：负载分布不如 round_robin 均匀

#### StickyStrategy

```java
public class StickyStrategy implements SharedSubscriptionStrategy {
    // group -> 上次选中的 clientId
    private final ConcurrentMap<String, String> sticky = new ConcurrentHashMap<>();

    public Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message) {
        if (candidates.isEmpty()) return null;

        String last = sticky.get(groupName);
        if (last != null) {
            for (Subscribe s : candidates) {
                if (s.getClientId().equals(last)) {
                    return s;  // 继续用上次选中的
                }
            }
        }

        // 上次选中的不在候选 (可能掉线), 重选
        Subscribe picked = candidates.get(0);
        sticky.put(groupName, picked.getClientId());
        return picked;
    }

    public String name() { return "sticky"; }
}
```

**特点**：
- 黏住策略，适合"长连接亲和"业务
- 缺点：负载不均

### 3.5 策略选择

| 场景 | 推荐策略 | 理由 |
|---|---|---|
| 通用场景 | `local_first` | 减少跨节点流量，mica-mqtt 默认 |
| 订阅者均匀分布 | `round_robin` | 公平 |
| 大量客户端、低 QoS | `random` | 最简单 |
| 消息顺序敏感 | `hash_client` | 顺序保证 |
| 状态缓存亲和 | `sticky` | 同一客户端连到相同 broker |
| 多 group | 各 group 独立选择 | 策略是 group 级别状态 |

### 3.6 消息类型扩展

`ClusterMessageType` 枚举新增 3 个值（与 cluster v3.0 对齐）：

```java
public enum ClusterMessageType {
    // V1 已有 (1-10)
    CLIENT_CONNECT(1),
    CLIENT_DISCONNECT(2),
    SUBSCRIBE_NOTIFY(3),
    UNSUBSCRIBE_NOTIFY(4),
    PUBLISH_FORWARD(5),
    NODE_LEAVE(6),
    STATE_SYNC_REQUEST(7),
    STATE_SYNC_RESPONSE(8),
    WILL_MESSAGE(9),
    RETAIN_MESSAGE(10),

    // routing 文档新增 (已实现)
    SHARED_DISPATCH_TO_CLIENT(11),   // dispatcher -> 选中节点
    SHARED_SUBSCRIBE_NOTIFY(12),     // 共享订阅变更广播
    SHARED_SUBSCRIBE_REMOVE(13);     // 共享订阅取消广播

    // storage 文档新增 (14-20)
    // ...
}
```

> **注**：`SHARED_SUBSCRIBE_NOTIFY(12)` 在 V1 中可能复用 `SUBSCRIBE_NOTIFY`，但为清晰起见本文档建议**独立消息类型**，便于未来按 group 单独过滤。

---

## 4. 边界场景与处理

### 4.1 订阅者刚断开（决策漂移）

```
T0: Node4 查 trie, 候选 = {clientX@Node2}
T1: clientX 在 Node2 断连, Node2 广播 CLIENT_DISCONNECT
T2: Node4 还没收到广播 (消息在途中)
T3: Node4 选中 clientX, 发送 SharedDispatchToClientMessage 给 Node2
T4: Node2 收到后, clientX 已不存在
```

**处理**：
```java
// Node2 收到 SharedDispatchToClientMessage 的处理
public void onSharedDispatch(SharedDispatchToClientMessage msg) {
    MqttSession session = localSessionManager.get(msg.clientId);
    if (session == null || !session.isConnected()) {
        // clientX 已掉线, 触发"重选"
        log.warn("Shared dispatch target {} offline, retry", msg.clientId);

        // 方案 A: 丢弃 (QoS 0 适用, 高吞吐)
        // 方案 B: 在 Node2 本地重选 (Node2 有完整候选, 因为 V1 全量同步)
        Subscribe rePick = strategy.pick(
            extractGroup(msg.topic),
            searchAllSharedSubscribe(msg.topic, true),
            myNodeId,
            msg.publishMessage
        );
        if (rePick != null) {
            rePick.deliver(msg.publishMessage);
        }
        // 方案 C: 回 NACK 给 Node4, 让 Node4 重选 (增加一跳)
    } else {
        session.deliver(msg.publishMessage);
    }
}
```

**推荐方案 B**（Node2 本地重选）：
- 候选列表来自 V1 全量同步，Node2 自己有
- 不需要回 NACK，延迟低
- 失败的概率极低（V1 同步间隔 < 100ms）

### 4.2 跨节点订阅者迁移

```
T0: clientX 在 Node2 订阅 $share/g1/sensor/temp
T1: clientX 断连
T2: clientX 重连到 Node3
T3: Node3 广播 SUBSCRIBE_NOTIFY { clientX -> $share/g1/sensor/temp, node=Node3 }
T4: Node4 (publisher) 还没收到广播
T5: Node4 查 trie, 候选中 clientX 还标记为 Node2
T6: Node4 选中 clientX, 发到 Node2
T7: Node2: clientX 已不在本地 -> 触发 4.1 流程 -> Node2 重选
```

**处理**：复用 §4.1 方案 B，Node2 重选时使用最新 trie。

### 4.3 集群脑裂

如果网络分区：
- 多数派节点继续处理 publish
- 少数派节点被隔离，短暂无法接收 SUBSCRIBE_NOTIFY

**影响**：
- 少数派节点上选中的 subscriber 可能在多数派已掉线
- Node 收到 SharedDispatch 后 clientId 不存在 -> 走 §4.1 流程
- 脑裂恢复后状态自动同步

**简化决策**：本设计不处理脑裂的复杂副本问题，因为：
1. V1 全量同步在脑裂恢复后会快速补齐
2. §4.1 流程能覆盖大多数不一致情况
3. 真正严格的脑裂处理需要 quorum/raft，超出本文范围

### 4.4 性能特征

| 操作 | 复杂度 | 说明 |
|---|---|---|
| `searchAllSubscribe` | O(trie) | 已有 V1 |
| `strategy.pick` | O(candidates) | candidates 数量 = group 内订阅者数 |
| `clusterSend` | O(1) | 1 次跨节点发送 |
| **总开销** | **O(trie + candidates)** | 通常 candidates 很小 |

**对比 V1**：
- V1: O(trie) + O(N) 转发
- V2: O(trie) + O(1) 转发 + O(candidates) strategy
- 当 N=10, candidates=3 时，V2 **网络开销是 V1 的 1/10**

---

## 5. 与其他方案对比

| 方案 | 转发次数 | 投递次数 | 复杂度 | 单点 | 持久化 | 状态 |
|---|---|---|---|---|---|---|
| V1 全量广播（当前） | N | N | 低 | 无 | 无 | 内存 |
| V2.1 owner 选举 | 2 | 1 | 中 | **owner** | 无 | 内存 |
| V2.2 主备 | 2 | 1 | 高 | 容忍 1 | 无 | 内存 |
| **V2 dispatcher（本文）** | **1** | **1** | **中** | **无** | **无** | **内存** |
| V2 dispatcher + V3 持久化 | 1 | 1 | 中 | 无 | **H2** | 内存 + 磁盘 |

> **v1.1 变更**：原表"V3 dispatcher"在 v1.0 中指路由层演进，v1.1 起统一定义为"V2 dispatcher"（routing V2）。原因：cluster v2.8 已将整个集群演进分为 V1/V2/V3 三层，routing 属 V2，storage 属 V3，避免版本号重复。带持久化的 dispatcher 方案对应"V2 dispatcher + V3 持久化"组合。

**V2 dispatcher 的精髓**：
- V1 已经把路由表全量复制了，**为什么还要选个 owner？**
- 让发布者节点本地决策就够了
- 不引入单点、不引入心跳、不引入状态复制

---

## 6. 配置示例

### 6.1 全局默认策略

```yaml
mqtt:
  cluster:
    shared-subscription:
      # 默认策略
      strategy: local_first       # random | round_robin | local_first | hash_client | sticky
      # group 级覆盖 (可选)
      group-overrides:
        "g1": round_robin         # 高吞吐组用轮询
        "g2": hash_client         # 顺序敏感组用 hash
```

### 6.2 Java API

```java
MqttServer server = MqttBroker.create()
    .getServerCreator()
    .port(1883)
    .clusterConfig(new MqttClusterConfig()
        .enabled(true)
        .sharedSubStrategy("local_first")
        // .sharedSubStrategy("round_robin")
        // .sharedSubStrategy("hash_client")
        .seedMembers(...)
    )
    .start();
```

### 6.3 动态切换策略

策略实现是**无状态**（除 RoundRobin/Sticky 计数器外），支持运行时切换：

```java
// 监控到 g1 负载倾斜, 切换策略
MqttClusterManager.updateGroupStrategy("g1", new RoundRobinStrategy());
```

---

## 7. 实施路径

### 阶段 0：基础设施（3 天）

- [ ] 定义 `SharedSubscriptionStrategy` 接口
- [ ] 实现 5 个内置策略
- [ ] 增加 `SHARED_DISPATCH_TO_CLIENT` 消息类型
- [ ] `ClusterMqttSessionManager.searchAllSubscribe(topic, sharedOnly)` 区分普通 vs 共享

### 阶段 1：Dispatcher 重构（5 天）

- [ ] `ClusterMessageDispatcher.handle()` 改造
  - [ ] 分离普通投递 / 共享投递
  - [ ] 实现 strategy 选择
  - [ ] 跨节点 `SharedDispatchToClientMessage` 发送
- [ ] 接收端 `ClusterMessageDispatcher.onClusterMessage()` 处理新消息
- [ ] 单元测试（mock 候选列表）

### 阶段 2：边界场景（3 天）

- [ ] 目标订阅者掉线 -> Node 端重选
- [ ] 订阅者跨节点迁移
- [ ] 空 group 跳过
- [ ] 大量订阅者下的 strategy 性能

### 阶段 3：集成测试（4 天）

- [ ] 3 节点集成测试
  - [ ] 验证无重复投递
  - [ ] 验证 QoS 1/2 行为一致
- [ ] 5 节点压测
  - [ ] 不同 strategy 下的吞吐差异
  - [ ] subscriber 跨节点重连场景
- [ ] 故障注入
  - [ ] 节点宕机恢复
  - [ ] 网络抖动

**总计 ~2 周**。

---

## 8. 注意事项

### 8.1 与 V1 SUBSCRIBE_NOTIFY 的关系

V1 中所有订阅（普通 + 共享）都通过 `SUBSCRIBE_NOTIFY` 广播。本文建议：

- **阶段 1 复用** `SUBSCRIBE_NOTIFY`，但携带 `isShared` 标记
- **阶段 2 拆分** 为 `SUBSCRIBE_NOTIFY` / `SHARED_SUBSCRIBE_NOTIFY`，便于优化

### 8.2 Strategy 状态的所有权

- `RoundRobin.counter` / `Sticky.sticky` 是**每个 publisher 节点本地**状态
- 同一 group 在不同 publisher 节点上独立计数
- 优点：简单，无需同步
- 缺点：跨 publisher 的负载均衡不保证
- 解决：使用 `HashClient` 或 `Random` 替代

### 8.3 升级路径

**v1.1 变更**：与 cluster v3.0 / storage v1.2 对齐，演进路径分三档：

```
V1 (现状, 已实现)  
  ↓ + 加 strategy 接口 (阶段 A)
V1.5 (混合期: 共享订阅走 V2 dispatcher, 普通订阅走 V1 广播)
  ↓ + dispatcher 切换为统一入口 (阶段 B)
V2 dispatcher (本文, 全部订阅走 dispatcher 模型)
  ↓ + 加 H2 持久化层 (阶段 C, 见 storage v1.2)
V2 dispatcher + V3 持久化 (终极目标)
```

**分阶段说明**：

| 阶段 | 改造内容 | 兼容性 | 预计工作量 |
|---|---|---|---|
| **A** | 仅加 `SharedSubscriptionStrategy` 接口与 5 个实现，dispatcher 仍走 V1 广播逻辑 | 100% 兼容 V1 | 2-3 天 |
| **B** | dispatcher 切换为统一入口：普通订阅走 `searchAllSubscribe` 后直接投递，共享订阅走 strategy 选择后单点转发 | 兼容 V1（旧节点会重复投递共享订阅消息） | 5 天 |
| **C** | 引入 H2 持久化（参见 storage v1.2） | 兼容 V1/V2 | 1 周（与 storage 阶段 0-2 同步） |

**建议**：阶段 A 和 B 可以平滑升级，向后兼容。V1 节点在升级期间会重复投递共享订阅消息，但不影响功能正确性（业务层去重）。阶段 C 涉及存储层，建议作为独立项目按 storage v1.2 实施路径推进。

### 8.4 监控指标

```
cluster_shared_sub_dispatch_total{strategy, group}        # 总调度次数
cluster_shared_sub_pick_local_total{strategy, group}      # 选中本地次数
cluster_shared_sub_pick_remote_total{strategy, group}     # 选中远端次数
cluster_shared_sub_dispatch_failed_total{reason}          # 失败次数
cluster_shared_sub_dispatch_latency_seconds               # 延迟分布
```

---

## 9. 功能检查清单

### 9.1 核心功能

- [x] 普通（非共享）订阅跨节点投递 — V1 已有
- [x] 共享订阅去重投递 — 本文设计
- [x] 多策略可插拔 — `SharedSubscriptionStrategy` 接口
- [x] 订阅者掉线时本地重选 — §4.1
- [x] 跨节点订阅者迁移兼容 — §4.2

### 9.2 集群协议扩展

- [x] `SHARED_DISPATCH_TO_CLIENT` (11) — dispatcher 模型核心
- [x] `SHARED_SUBSCRIBE_NOTIFY` (12) — 可选拆分
- [x] `SHARED_SUBSCRIBE_REMOVE` (13) — 可选拆分

### 9.3 兼容性

- [x] 向后兼容 V1：旧消息类型保留
- [x] 平滑升级路径 — §8.3
- [x] 灰度发布可行 — strategy 可配置

### 9.4 与 storage 层协同

- [ ] 共享订阅 owner 状态持久化（H2 `mqtt_shared_sub` 表）— 详见 `mqtt-server-cluster-storage.md` (v1.2) §4.4
- [ ] Shared Sub 故障切换：owner 宕机时 backup 升级（storage v1.2 §4.4.4，零消息真空）
- [ ] 节点重启后策略计数器恢复（可选，RoundRobin/Sticky 需要状态恢复）
- [ ] 共享订阅变更事件审计（H2 WAL，可选）

**v1.1 备注**：v1.0 文档提的"用 H2 做持久化"在 v1.1 已被 storage 文档采纳为统一方案（单 jar ~2MB，无 RocksDB 依赖）。本文档不重复持久化设计，详情见 storage 文档。

### 9.5 已知限制

- [ ] 脑裂下少数派节点可能错误路由（§4.3）
- [ ] Strategy 计数器是节点本地，跨 publisher 负载不严格均衡（§8.2）
- [ ] $queue 特殊处理同 $share（MQTT 5 规范下两者语义一致，但需测试覆盖）

---

## 10. 设计哲学总结

> **V1 给了我们全量路由表的复制能力，但没告诉我们怎么用好它。**
>
> 本文的核心观点是：**既然每个节点都知道"全局有什么"，那每个节点都可以做"全局决策"**。这消除了 owner 这个抽象（也消除了 owner 带来的所有问题：单点、切换、一致性）。
>
> 这种"去中心化决策 + 中心化存储"的模式，是分布式系统最朴素的智慧：**能用本地数据解决的问题，绝不引入全局协调**。

---

**文档版本**：v1.2
**更新日期**：2026-06-22
**状态**：设计稿 + V2 dispatcher 已实现

**v1.2 变更摘要**（以代码实现为准全面对齐 cluster v3.0）：
- §1.5 协议号修正：SHARED_DISPATCH_TO_CLIENT 9→11，SHARED_SUBSCRIBE_NOTIFY 10→12，SHARED_SUBSCRIBE_REMOVE 11→13；移除不存在的 SHARED_SUBSCRIBE_FORWARD 和 SHARED_PUBLISH_FORWARD
- §2.1 目录树重写：补充 config/ 包（MqttClusterConfig、MqttStorageConfig）、core/ClusterStorage.java、metrics/ClusterMetrics.java；移除不存在的 SharedSubscribeNotifyMessage、SharedPublishFanoutMessage
- §3.3 策略接口签名修正：与代码对齐，4 个参数 `pick(String, List, String, Message)`
- §3.6 枚举块修正：协议号 9-11 → 11-13，补充 V1 完整 1-10 枚举

**配套文档**：
- `docs/todo/mqtt-server-cluster.md` (v3.0)（基础集群）
- `docs/todo/mqtt-server-cluster-storage.md` (v1.2)（存储层）
