# mica-mqtt-broker 集群存储层设计文档

> **本文档定位**：本文件是 `mqtt-server-cluster.md` 的**存储层专章**，描述在引入 H2 MVStore 本地嵌入式存储后，集群能力获得的升级与新特性。v1.1 起整个存储层统一为单一 H2 引擎（单 jar ~2MB），不再依赖 RocksDB / MapDB 等第三方存储引擎。如未特别说明，本文假设读者已熟悉 cluster 文档中的基础集群拓扑与消息协议。

---

## 1. 方案概述

### 1.1 背景与动机

在纯内存 + 集群广播的 V1 方案下，节点宕机会带来以下问题：

| 问题 | 影响 |
|---|---|
| Session 全部丢失 | 持久会话（`cleanSession=false`）客户端需重新鉴权、重订阅 |
| 飞行中 QoS 1/2 消息丢失 | MQTT 协议承诺被打破 |
| 保留消息（Retain）部分丢失 | 新订阅者拿不到历史 retain |
| 共享订阅 owner 状态丢失 | 需等客户端 keepalive 超时重订阅，**最长 15s 消息真空** |
| 集群冷启动慢 | 新节点加入需要全量同步所有状态 |

引入本地文件存储后，上述问题得到根本性改善。

### 1.2 目标

- **持久化**：节点重启后能恢复 Session、Retain、共享订阅状态
- **可恢复**：QoS 1/2 飞行消息可重放，session 可跨节点接管
- **可降级**：本地存储故障不应导致 Broker 整体不可用
- **零外部依赖**：仅使用嵌入式库（无 Redis、Kafka、DB 等独立服务）

### 1.3 选型结论

| 数据类型 | 存储方案 | 理由 |
|---|---|---|
| Session 元数据 | **H2 MVStore** | 写少读多、结构化、ACID 事务、WAL 崩溃安全 |
| Retain 消息 | **H2 MVStore + 内存 Skiplist 索引** | H2 持久化，内存索引加速通配符查询 |
| Shared Subscription 状态 | **H2 MVStore** | 低频变更、需一致性读 |
| QoS 1/2 飞行消息 | **H2 MVStore + 定时清理线程** | 纯 Java，TTL 通过 expireAt + 后台扫描实现 |
| Topic Trie（路由表） | **内存** | 关键路径，不落盘 |
| 客户端连接上下文 | **内存（t-io）** | 运行时态，无需持久 |

> **核心原则**：**零第三方存储引擎依赖，仅 H2 一个 jar（~2MB）**。所有持久化诉求由 H2 MVStore 统一承担，配合"内存索引 + 定时清理"补齐 H2 缺失的通配查询与 TTL 能力。

#### 1.3.1 关于 RocksDB / MapDB / RogueMap 的评估结论

v1.0 文档曾推荐 RocksDB 承担 QoS 1/2 飞行消息存储。v1.1 重新评估后**全部排除**，理由：

| 候选 | 排除理由 |
|---|---|
| **RocksDB JNI** | 单 jar ~80MB（含 linux-x64 / osx / win-x64 三套 jni 库），直接让 broker 体积翻倍；需要为 ARM 等平台自编译；JNI 段错误诊断困难；mica-mqtt 现有依赖皆为纯 Java，引入 JNI 风格突兀 |
| **MapDB 3.x** | 自身 jar 1.5MB，但 transitive 依赖累计 8MB+（kotlin-stdlib、eclipse-collections、jackson 等）；社区活跃度下降（最新版本停留在 3.0.9，2020 年后无大更新） |
| **RogueMap** | 单作者、95 star、5 open issue；缺少 range scan（无法做 Retain topic 通配）、无 WAL（事务崩溃安全不达标）、TTL 仅惰性过期（文件持续膨胀）；主打 AI Memory 场景，与 broker 关键路径不匹配 |

**TTL 替代方案**：H2 MVStore 本身不内置 TTL，但 QoS 1/2 飞行消息场景对 TTL 的核心诉求是"过期数据回收"和"按 clientId 范围扫描"。前者用后台 `ScheduledExecutor` 周期扫描可解（30s 滞后完全可接受），后者 H2 MVStore 的 `Map<String,byte[]>` 已原生支持 key 精确查与迭代器过滤。

**Retain 通配查询替代方案**：H2 MVStore 的 Map 只能精确查。解决方案是叠一层"**H2 持久化 + 内存 `ConcurrentSkipListMap` 索引**"——H2 负责崩溃安全，内存索引负责通配加速。10w 级 retain 规模下，全表扫或子范围扫都 < 1ms。

> **MVP 建议**：整个存储层只引入 H2 MVStore（一个 jar，~2MB，零原生依赖），同时实现两层薄的自研索引（Retain 索引 + Inflight TTL 清理器）。总自研代码量约 1 周工作量，长期运行无第三方存储引擎的版本绑定风险。

### 1.4 与 cluster 文档的关系

```
docs/todo/
├── mqtt-server-cluster.md            # 总览、拓扑、基础消息协议
├── mqtt-server-cluster-routing.md    # 路由 + 共享订阅 (EMQX dispatcher 风格)
└── mqtt-server-cluster-storage.md    # 本文档：存储层
```

---

## 2. 架构设计

### 2.1 三层数据架构

```
+------------------------------------------------------------+
|  L1  内存热数据 (Hot Path)                                  |
|      - t-io ChannelContext / 会话对象                       |
|      - Topic Trie (路由表, O(logN) topic 匹配)              |
|      - ClientNodeMap (clientId -> nodeId)                  |
|      - Shared Subscription 候选列表 (来自 V1 全量同步)      |
|      - Retain 内存索引 (ConcurrentSkipListMap, 通配加速)   |
+------------------------------------------------------------+
|  L2  本地文件存储 (Persistent)                              |
|      - H2 MVStore -- session / retain / shared sub / 飞行  |
|      - TTL 后台清理线程 (QoS 飞行消息)                     |
+------------------------------------------------------------+
|  L3  集群广播 (Cluster Sync)                                |
|      - mica-net / t-io 集群消息                             |
|      - 用于: 跨节点状态镜像、shard 备份、session 迁移        |
+------------------------------------------------------------+
```

### 2.2 数据流向

```
                       +----------------------+
   Client Publish ---> | MqttBroker           |
                       |  |- Dispatcher (L1)  | --> 远节点投递
                       |  |- SessionMgr (L1)  |
                       |  +- MessageStore     |
                       |       |- L1 缓存      |
                       |       +- L2 落盘  ----+--> L3 复制
                       +----------------------+
                                  ^
                                  | 重启恢复
                                  |
                       +----------------------+
                       | Local Store (L2)     |
                       |  - H2 MVStore        |
                       |    (session/retain/  |
                       |     sharedsub/飞行)  |
                       |  - TTL 清理线程      |
                       +----------------------+
```

### 2.3 节点启动时序

```
节点启动
  |
  |- 1. 启动 L2 存储 (打开 H2 MVStore, 启动 TTL 清理线程)
  |
  |- 2. 从 L2 加载: session / retain / shared sub
  |     +-- 加载到 L1 内存结构
  |
  |- 3. 加入集群 (mica-net)
  |     +-- 与其他节点握手、同步路由表
  |
  |- 4. 启动 MQTT 监听端口 (1883)
  |
  +- 5. 接收新连接、新订阅
```

### 2.4 关键不变量

| 场景 | 不变量 |
|---|---|
| Session 写入 | 必须在返回 CONNACK 前同步落 L2 |
| Retain 写入 | L2 落盘后异步广播（不阻塞发布） |
| QoS 1/2 飞行消息 | 异步写入 H2（executor 批量刷盘），后台 TTL 线程 30s 清理一次 |
| 共享订阅状态变更 | L2 落盘 -> 广播 -> 收到 >=1 副本 ACK 后才回执客户端 |
| 节点宕机 | 重启后 L1 必须从 L2 完整恢复，不依赖其他节点 |

---

## 3. 存储引擎抽象

### 3.1 接口设计

```java
public interface LocalKvStore {

    /** 启动并打开底层存储 */
    void open(Path dataDir);

    /** 关闭并释放资源 */
    void close();

    /** 通用 KV 操作 */
    byte[] get(String key);
    void put(String key, byte[] value);
    void delete(String key);

    /** 范围扫描 */
    List<KeyValue> scan(String prefix);

    /** 事务支持（仅 H2 实现） */
    void executeInTransaction(Runnable body);

    /** 健康检查 */
    StoreStats stats();
}
```

### 3.2 H2 MVStore 实现要点

```java
public class H2MvStoreImpl implements LocalKvStore {
    private MVStore store;
    private Map<String, Value> sessionMap;     // 表 1
    private Map<String, Value> retainMap;      // 表 2
    private Map<String, Value> sharedSubMap;   // 表 3

    public void open(Path dataDir) {
        store = MVStore.open(dataDir.resolve("mqtt-cluster.mv").toString());
        sessionMap = store.openMap("sessions");
        retainMap  = store.openMap("retain");
        // ...
    }

    public void executeInTransaction(Runnable body) {
        store.commit();  // H2 MVStore 默认自动 commit
        // 复杂事务可使用 store.registerVersion()
    }
}
```

**关键配置**：
- `autoCommitBufferSize=1024`：批量写入缓冲
- `compression=1`（LZF）：降低磁盘占用
- `cacheSize=64MB`：内存缓存

### 3.3 Inflight 飞行消息实现要点（H2 + 定时清理）

v1.1 起 QoS 1/2 飞行消息不再依赖 RocksDB，改由 H2 MVStore + 后台 TTL 线程承担。设计目标：写入路径与 Session/Retain 完全一致（同一个 H2 文件），TTL 清理与 RocksDB compaction filter 行为等价但实现简单。

```java
public class H2InflightStore implements LocalKvStore {

    private MVStore store;
    private Map<String, Value> inflightMap;        // 表
    private final ScheduledExecutorService cleaner;

    public void open(Path dataDir) {
        store = MVStore.open(dataDir.resolve("mqtt-cluster.mv").toString());
        inflightMap = store.openMap("inflight");

        // 后台 TTL 清理: 30s 一次, 滞后窗口可接受
        cleaner = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "mqtt-inflight-cleaner");
                   t.setDaemon(true); return t; });
        cleaner.scheduleAtFixedRate(this::cleanupExpired, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * key: "clientId:packetId"
     * value: [expireAt:8B][payload:N]  -- 头部 8 字节存过期时间戳
     */
    public void putWithTtl(String key, byte[] value, long ttlMs) {
        long expireAt = System.currentTimeMillis() + ttlMs;
        byte[] wrapped = new byte[8 + value.length];
        ByteBuffer.wrap(wrapped).putLong(expireAt);
        System.arraycopy(value, 0, wrapped, 8, value.length);
        inflightMap.put(key, ValueDataType.wrap(wrapped));
    }

    /** 按 clientId 前缀扫描, 用于重连重放 */
    public List<byte[]> scanByClient(String clientId) {
        String prefix = clientId + ":";
        List<byte[]> result = new ArrayList<>();
        for (Map.Entry<String, Value> e : inflightMap.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                byte[] wrapped = e.getValue().getBytes();
                // 跳过已过期的
                long expireAt = ByteBuffer.wrap(wrapped, 0, 8).getLong();
                if (expireAt > System.currentTimeMillis()) {
                    result.add(Arrays.copyOfRange(wrapped, 8, wrapped.length));
                }
            }
        }
        return result;
    }

    /** TTL 清理线程, 30s 触发一次 */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<String, Value>> it = inflightMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Value> e = it.next();
            byte[] wrapped = e.getValue().getBytes();
            long expireAt = ByteBuffer.wrap(wrapped, 0, 8).getLong();
            if (expireAt < now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            // MVStore 自动 commit, 无需显式调用
        }
    }
}
```

**与 RocksDB TTL 的关键差异**：

| 维度 | RocksDB | H2 + 定时清理 |
|---|---|---|
| 过期回收时机 | compaction 时实时回收 | 后台线程 30s 周期 |
| 最坏过期滞后 | 几乎 0 | 30s |
| jar 体积 | +80MB | +0MB（H2 已包含） |
| 平台依赖 | JNI / 多平台原生库 | 纯 Java |
| 实现复杂度 | 高（LSM tree 调优） | 低（普通 Map 迭代） |

**30s 滞后是否可接受**：飞行消息的实际 TTL 通常是 session.keepalive × 3 = 90s ~ 240s 量级，30s 滞后相对可忽略。客户端重连时还会做一次 `scanByClient`，过期的会被应用层过滤。

---

## 4. 详细设计

### 4.1 Session 持久化

#### 4.1.1 数据模型（H2 MVStore）

```sql
-- session 表 (key = clientId)
CREATE TABLE mqtt_session (
    clientId           VARCHAR(128) PRIMARY KEY,
    nodeId             VARCHAR(64)  NOT NULL,   -- 当前归属节点
    cleanSession       BOOLEAN      NOT NULL,
    keepaliveSeconds   INTEGER      NOT NULL,
    subscriptions      CLOB,                    -- JSON: [{topic, qos}]
    pendingInflightRef CLOB,                    -- 关联 inflight 记录
    createdAt          BIGINT       NOT NULL,
    updatedAt          BIGINT       NOT NULL,
    expireAt           BIGINT       NOT NULL    -- session 过期时间
);
CREATE INDEX idx_session_node ON mqtt_session(nodeId);
CREATE INDEX idx_session_expire ON mqtt_session(expireAt);
```

#### 4.1.2 写入流程

```java
// IMqttSessionManager.createSession(clientId, session)
public void createSession(String clientId, MqttSession session) {
    // L1: 加载到内存
    inMemorySessions.put(clientId, session);

    // L2: 同步落盘 (必须在返回 CONNACK 前)
    localStore.executeInTransaction(() -> {
        sessionMap.put(clientId, serializeSession(session));
    });
}
```

#### 4.1.3 跨节点 Session 接管协议

新增集群消息：

```java
SESSION_TAKEOVER_REQUEST(12),   // 新节点 -> 老节点
SESSION_TAKEOVER_RESPONSE(13),  // 老节点 -> 新节点
SESSION_MIGRATED_NOTIFY(14),    // 新节点 -> 全集群广播
SESSION_DELETE_NOTIFY(15),      // 任何节点 -> 全集群
```

**协议流程**：

```
Client 重连 (sticky 失败, 连接到 NodeB)
  |
  |- (1) NodeB 在本地 L2 查 session
  |     +-- miss
  |
  |- (2) NodeB 计算 hash(clientId) -> 期望 owner = NodeA
  |     (或查 L1 clientNodeMap)
  |
  |- (3) NodeB -> NodeA: SESSION_TAKEOVER_REQUEST { clientId }
  |
  |- (4) NodeA:
  |     |- 验证 clientId 合法性 (可选: 鉴权 token)
  |     |- 从 L2 加载完整 session
  |     |- 序列化 session payload
  |     +-- NodeA -> NodeB: SESSION_TAKEOVER_RESPONSE { session, pendingInflight }
  |
  |- (5) NodeB:
  |     |- 写入本地 L2: mqtt_session 表
  |     |- 加载到 L1 内存
  |     +-- 重放 QoS 1/2 飞行消息 (见 4.3)
  |
  |- (6) NodeB -> 全集群: SESSION_MIGRATED_NOTIFY { clientId, newNode=NodeB }
  |
  +- (7) NodeA 收到广播后, 清理本地 L2 (或保留 5 分钟兜底)
```

**幂等性**：
- 接管请求带 `requestId`，避免重复执行
- 接管过程加分布式锁：`lock:clientId:{clientId}`

#### 4.1.4 清理策略

```java
// 定时任务: 每 60s 扫描
@Scheduled(fixedRate = 60_000)
public void cleanupExpiredSessions() {
    long now = System.currentTimeMillis();
    // L1 + L2 同步删除
    for (var entry : sessionMap.entrySet()) {
        if (entry.getValue().expireAt < now) {
            // 通知集群
            clusterBroadcast(new SessionDeleteNotify(entry.getKey()));
            sessionMap.remove(entry.getKey());
        }
    }
}
```

### 4.2 Retain 消息持久化

#### 4.2.1 数据模型

```sql
-- retain 表 (key = topic)
CREATE TABLE mqtt_retain (
    topic          VARCHAR(256) PRIMARY KEY,
    payload        BLOB         NOT NULL,
    qos            TINYINT      NOT NULL,
    retainHandling TINYINT,                  -- MQTT 5.0
    messageExpiry  BIGINT,                   -- 消息过期时间
    createdAt      BIGINT       NOT NULL,
    replicas       CLOB                     -- JSON: ["node1", "node2"]
);
```

#### 4.2.2 写入流程（分片复制模式）

```java
public void addRetainMessage(String topic, ByteBuf payload, int qos) {
    RetainMessage msg = new RetainMessage(topic, payload, qos);

    // L2: 本地落盘
    retainMap.put(topic, serialize(msg));

    // L3: 异步分片复制
    List<String> replicas = shardRouter.replicasOf(topic, 2); // RF=2
    for (String node : replicas) {
        if (!node.equals(myNodeId)) {
            clusterSendAsync(node, new RetainReplicateMessage(topic, msg));
        }
    }
}
```

**分片策略**：

```java
public class RetainShardRouter {
    private final ConsistentHashRing ring;  // 复用 shared sub 的 ring

    public List<String> replicasOf(String topic, int rf) {
        long h = crc32(topic.getBytes());
        return ring.getReplicas(h, rf);
    }
}
```

**为什么分片而非全广播**：
- 10w retain 消息 × N 节点 = 灾难级内存
- 分片后每个节点只承担 1/RF 的内存
- 读路径仍能查本地 + 跨节点（极小延迟）

#### 4.2.3 订阅者拉取

```java
public List<RetainMessage> getRetainMessages(String topicFilter) {
    // 1. 内存索引通配查询 (主路径)
    List<RetainMessage> local = retainIndex.match(topicFilter);

    // 2. 跨节点补充 (按需, 仅在分片缺失时)
    if (needsRemoteQuery(topicFilter)) {
        for (String peer : shardRouter.peersForTopic(topicFilter)) {
            local.addAll(clusterQuery(peer, new RetainQuery(topicFilter)));
        }
    }
    return local;
}
```

#### 4.2.4 Retain 内存索引设计（v1.1 新增）

> **v1.1 变更**：4.2.3 引入"内存索引层"。H2 MVStore 的 `Map` 只能精确查，topic 通配查询（`+` / `#`）走 H2 LIKE 会全表扫不可接受。因此在 H2 之上叠一层 `ConcurrentSkipListMap`。

```java
public class RetainIndex {

    // L1 索引: 纯内存, 加速通配查询
    private final ConcurrentSkipListMap<String, RetainPayload> treeMap =
        new ConcurrentSkipListMap<>();

    // L2 持久化: H2 MVStore 同一个文件, 独立 Map
    private final Map<String, Value> retainTable;

    public void put(String topic, RetainPayload payload) {
        // 1. 同步落 H2 (崩溃安全)
        synchronized (retainTable) {
            retainTable.put(topic, serialize(payload));
        }
        // 2. 异步更内存 (崩溃时丢一点, 启动时从 H2 rebuild)
        treeMap.put(topic, payload);
    }

    /**
     * 通配查询策略:
     * 1. 提取 topicFilter 在首个 '+' / '#' 之前的字面量前缀
     * 2. 优先用 subMap 缩小候选集
     * 3. 再用 MqttUtil.matchTopic 做精确匹配 (剔除假阳性)
     */
    public List<RetainPayload> match(String topicFilter) {
        String literalPrefix = extractLiteralPrefix(topicFilter);

        if (literalPrefix.isEmpty()) {
            // 全表扫描 (retain < 1w 时 < 1ms, 可接受)
            return treeMap.entrySet().stream()
                .filter(e -> MqttUtil.matchTopic(topicFilter, e.getKey()))
                .map(Map.Entry::getValue)
                .collect(toList());
        }

        // 范围扫描: [literalPrefix, literalPrefix + "￿")
        return treeMap.subMap(literalPrefix, false,
                              literalPrefix + "￿", false)
            .entrySet().stream()
            .filter(e -> MqttUtil.matchTopic(topicFilter, e.getKey()))
            .map(Map.Entry::getValue)
            .collect(toList());
    }

    /** 启动时从 H2 重建内存索引 */
    public void reload() {
        treeMap.clear();
        retainTable.forEach((k, v) ->
            treeMap.put(k, deserialize(v.getBytes())));
    }
}
```

**内存与 H2 一致性**：
- 写路径：先 H2 后内存（H2 成功是事实，内存丢失可重建）
- 启动路径：先 H2 加载到内存，再对外提供服务
- 删除路径：同上

**为什么不直接用 H2 B+Tree 索引**：H2 MVStore 不暴露 B+Tree，只暴露 Map 抽象。要么用 H2 Server 模式的 SQL（引入 H2 server，复杂度上升），要么用 Map LIKE（性能不可接受）。内存 Skiplist 是性价比最高的折中。

**为什么不引入 MapDB B+Tree**：MapDB 3.x 的 transitive 依赖累计 8MB+（kotlin-stdlib / eclipse-collections / jackson），违背"零第三方存储引擎"原则。10w 级 retain 规模下，`ConcurrentSkipListMap` 的全表扫 < 1ms，已足够。

### 4.3 QoS 1/2 飞行消息持久化

> **v1.1 变更**：存储引擎从 RocksDB 改为 H2 MVStore + 定时清理线程。详见 §1.3.1 选型评估。

#### 4.3.1 数据模型（H2 MVStore）

```
Table: mqtt_inflight  (key = "clientId:packetId")
-------------------------------------------------
Value 布局:  [expireAt: 8B][MqttPublishMessage 序列化: NB]
expireAt   = System.currentTimeMillis() + session.keepalive * 3
-------------------------------------------------
```

与 Session/Retain 共用同一个 `mqtt-cluster.mv` 文件，**单文件多 Map**。

#### 4.3.2 写入流程

```java
// Qos1PublishHandler
public void sendQos1Publish(String clientId, int packetId, MqttPublishMessage msg) {
    // 1. 发送到 t-io 缓冲区（关键路径, 不阻塞）
    ctx.writeAndFlush(msg);

    // 2. 提交到异步写线程（不阻塞发送线程）
    asyncWriteExecutor.submit(() -> {
        long ttlMs = session.keepalive() * 3 * 1000L;  // 经验值
        inflightStore.putWithTtl(
            encodeKey(clientId, packetId),
            serialize(msg),
            ttlMs
        );
    });
}

// Qos1PubAckHandler
public void onPubAck(String clientId, int packetId) {
    asyncWriteExecutor.submit(() -> {
        inflightStore.delete(encodeKey(clientId, packetId));
    });
}
```

**关键不变量**：
- 发送路径只 `writeAndFlush`（不落盘），由 `asyncWriteExecutor` 异步落 H2
- ACK 路径同样异步，避免阻塞 PUBACK 响应
- H2 MVStore 本身已开启 `autoCommit`，单条 put 即写 binlog

#### 4.3.3 重连重放

```java
// Client 重连成功, 在 session 接管完成后触发
public void replayInflightMessages(String clientId) {
    // 按 clientId 前缀扫描, 应用层过滤过期
    List<byte[]> pending = inflightStore.scanByClient(clientId);

    long now = System.currentTimeMillis();
    for (byte[] payload : pending) {
        // payload 已剥离 expireAt 头
        MqttPublishMessage msg = deserialize(payload);
        ctx.writeAndFlush(msg);
        // 保留在 H2, 收到 PUBACK 后再删 (重入幂等由 packetId 保障)
    }
}
```

#### 4.3.4 容量与清理

**自动清理**（后台守护线程，30s 周期）：

```java
@Scheduled(fixedRate = 30_000)
public void cleanupExpired() {
    long now = System.currentTimeMillis();
    int removed = 0;
    Iterator<Map.Entry<String, Value>> it = inflightMap.entrySet().iterator();
    while (it.hasNext()) {
        Map.Entry<String, Value> e = it.next();
        long expireAt = readExpireTime(e.getValue());
        if (expireAt < now) {
            it.remove();
            removed++;
        }
    }
    if (removed > 0) {
        // MVStore 自动 commit; 可选: 打点 metrics
        metrics.recordInflightExpired(removed);
    }
}
```

**手动清理**（节点优雅关闭时）：

```java
// MqttBroker.stop()
public void stop() {
    inflightStore.cleanupExpired();  // 同步清理一次
    store.close();
    cleaner.shutdown();
}
```

**存储估算**：
- 单条飞行消息平均 1KB（含 8B expireAt 头）
- 1w 在线客户端 × 平均 10 条未 ack = 100w 条 ≈ 1GB
- H2 MVStore 单文件可支撑数十 GB，超过时按 broker/data/{nodeId}/ 子目录分片

**30s 滞后下的数据膨胀风险**：
- 极端场景：30s 内涌入 1w 条且全部立即过期 → 清理前文件多 1w 条 ≈ 10MB
- 接受阈值：30s 清理窗口内堆积 < 100MB
- 监控指标：`inflight.size() - inflight.aliveCount()` 的差值，告警阈值 10w

### 4.4 Shared Subscription 状态

> **本文不重复 dispatcher 模型的设计细节**（见 `mqtt-server-cluster-routing.md`），仅说明 H2 引入后**持久化层面**的增强。

#### 4.4.1 数据模型

```sql
CREATE TABLE mqtt_shared_sub (
    groupName    VARCHAR(128) PRIMARY KEY,
    strategy     VARCHAR(32)  NOT NULL,    -- random / round_robin / local_first
    members      CLOB         NOT NULL,    -- JSON: [{clientId, nodeId, topic, qos}]
    version      BIGINT       NOT NULL,    -- 乐观锁
    updatedAt    BIGINT       NOT NULL
);
```

#### 4.4.2 节点角色

每个 group 在不同节点上的状态：

```
group "g1":
  Node1 (owner):   L2 持久化 + L1 内存 + 处理 PUBLISH_FORWARD
  Node2 (backup):  L2 持久化副本 + 异步接收 owner 的状态变更
  Node3 (普通):    不持久化, 仅在 V1 全量同步中持有只读副本
```

#### 4.4.3 状态同步协议

```java
SHARED_SUB_STATE_SYNC(16),    // owner -> backup
SHARED_SUB_TAKEOVER(17),      // 新 owner 接管宣告
```

```java
// Owner 端: 状态变更时
public void onSharedSubChange(String group, SharedGroupState newState) {
    // L2 落盘 (本地就是 owner)
    sharedSubMap.put(group, serialize(newState));

    // 通知 backup
    String backup = shardRouter.backupOf(group);
    if (backup != null && !backup.equals(myNodeId)) {
        clusterSend(backup, new SharedSubStateSync(group, newState));
    }
}
```

#### 4.4.4 故障恢复

```
Owner (Node1) 宕机
  |
  |- Backup (Node2) 检测到:
  |     - mica-net 通知 Node1 离开
  |     - 或自己成为新的 hash ring 首节点
  |
  |- Node2 把自己从 backup 升为 owner
  |     (L2 状态已经完整, 无需重建)
  |
  +- 集群广播 SHARED_SUB_TAKEOVER
        其他节点重新计算 owner, 后续 PUBLISH 转发到 Node2
```

**对比纯内存方案**：
- **之前**：owner 挂 = 整个 group 状态丢失，**最长 15s 消息真空**
- **现在**：L2 持久化 = **零消息真空**

---

## 5. 集群协议扩展汇总

新增的集群消息类型：

```java
// 之前已有
CLIENT_CONNECT(1),
CLIENT_DISCONNECT(2),
SUBSCRIBE_NOTIFY(3),
UNSUBSCRIBE_NOTIFY(4),
PUBLISH_FORWARD(5),
NODE_LEAVE(6),
STATE_SYNC_REQUEST(7),
STATE_SYNC_RESPONSE(8),

// routing 文档新增
SHARED_SUBSCRIBE_FORWARD(9),
SHARED_PUBLISH_FORWARD(10),
SHARED_DISPATCH_TO_CLIENT(11),

// 本文档新增 (存储层)
SESSION_TAKEOVER_REQUEST(12),
SESSION_TAKEOVER_RESPONSE(13),
SESSION_MIGRATED_NOTIFY(14),
SESSION_DELETE_NOTIFY(15),
SHARED_SUB_STATE_SYNC(16),
SHARED_SUB_TAKEOVER(17),
RETAIN_REPLICATE(18),
RETAIN_QUERY(19),
```

向后兼容：旧节点收到新消息类型时记录 warning 并忽略。

---

## 6. 配置示例

### 6.1 最小配置（仅 H2，v1.1 推荐方案）

```yaml
mqtt:
  server:
    name: mqtt-broker-1
    port: 1883
    cluster:
      enabled: true
      host: 192.168.1.10
      port: 9000
      seed-members:
        - 192.168.1.10:9000
        - 192.168.1.11:9000

  # 存储层 (v1.1: 单一 H2 引擎, 无 rocksdb 配置块)
  storage:
    enabled: true
    type: h2
    data-dir: ./data/mqtt
    h2:
      cache-size-mb: 64
      compress: lzf
      auto-commit-buffer: 1024
    # QoS 1/2 飞行消息 TTL 清理
    inflight:
      cleanup-interval-seconds: 30
      default-ttl-factor: 3      # TTL = session.keepalive * factor
      max-stale-records: 100000  # 滞后堆积告警阈值
    # Retain 内存索引
    retain-index:
      rebuild-on-startup: true
      max-in-memory: 1000000     # 超过后降级为全表扫
```

### 6.2 Java API

```java
MqttServer server = MqttBroker.create()
    .getServerCreator()
    .port(1883)
    .storageConfig(new MqttStorageConfig()
        .enabled(true)
        .type(StorageType.H2)            // v1.1: 仅 H2
        .dataDir("./data/mqtt")
        .h2CacheSizeMb(64)
        .inflightCleanupIntervalSeconds(30)
        .inflightDefaultTtlFactor(3)
        .retainIndexMaxInMemory(1_000_000)
    )
    .clusterConfig(new MqttClusterConfig()
        .enabled(true)
        .seedMembers(Arrays.asList("192.168.1.10:9000", "192.168.1.11:9000"))
    )
    .start();
```

### 6.3 调优建议

| 场景 | 配置建议 |
|---|---|
| 高频发布、QoS 1/2 多 | 调小 `cleanup-interval-seconds`（如 10s），增大 H2 `cache-size-mb` |
| Retain 消息量大（>10w） | 启用 `max-in-memory` 限制，超出后降级为 H2 全表扫（保护 JVM 堆） |
| 集群节点数多 | 调小 H2 cache，让更多 session 数据落盘而非缓存 |
| 磁盘 IO 慢 | H2 开启 `compress=lzf` 减 IO（CPU 换 IO） |
| 极致低延迟 | 关闭 Retain 持久化（仅内存），接受宕机丢失 |

---

## 7. 实施路径

### 阶段 0：H2 引入（1 周）

- [ ] 定义 `LocalKvStore` 接口
- [ ] 实现 `H2MvStoreImpl`
- [ ] `ClusterMqttSessionManager` 接入
- [ ] `ClusterMqttMessageStore` retain 接入
- [ ] **兼容性**：保留内存模式开关，配置化切换

### 阶段 1：Session 跨节点接管（1 周）

- [ ] 新增 SESSION_TAKEOVER 协议
- [ ] 接管时锁、幂等、超时
- [ ] sticky 失败场景测试

### 阶段 2：Shared Subscription 持久化（1 周）

- [ ] H2 持久化 owner 状态
- [ ] backup 副本同步
- [ ] 重启恢复测试

### 阶段 3：H2 Inflight 存储 + TTL 清理（0.5 周）

> **v1.1 变更**：原计划 1 周接入 RocksDB，现简化为 0.5 周实现 H2 + 后台清理线程。

- [ ] `InflightStore` 抽象（接口定义）
- [ ] `H2InflightStore` 实现（同文件不同 Map）
- [ ] 后台 TTL 清理线程（30s 周期）
- [ ] 异步写线程池（不阻塞发送路径）
- [ ] 客户端重连重放 + 过期过滤
- [ ] 滞后堆积告警 metrics

### 阶段 4：Retain 分片复制（1 周）

- [ ] `RetainShardRouter`
- [ ] 异步复制协议
- [ ] 读路径优化

**总计 ~5 周**。每阶段独立可发布、可回滚。

---

## 8. 注意事项

### 8.1 磁盘与备份

- **磁盘 IO**：H2 MVStore 顺序写为主，普通 SSD 即可，建议 `ext4` / `xfs`
- **磁盘容量**：规划 3-5x 内存大小作为初值
- **备份**：定期快照 `data/` 目录；H2 MVStore 支持在线备份（`MVStore.getFileStore().copyTo()`）
- **监控指标**：
  - H2 文件大小：`store.getFileStore().size()`
  - Inflight 滞后堆积：`inflight.size() - inflight.aliveCount()`
  - Retain 索引内存：`retainIndex.memoryBytes()`

### 8.2 内存保护

- L2 不能完全替代 L1，关键路径仍走内存
- 当 L2 加载数据 > 阈值时（如 1GB），拒绝新连接或降级
- 配置示例：
  ```yaml
  storage:
    h2:
      max-loaded-size-mb: 1024   # 超过则拒绝新 session
  ```

### 8.3 降级模式

- L2 启动失败：允许 Broker 启动但记录告警，session 退化为纯内存
- L2 写入失败：重试 3 次后转异步队列，避免阻塞请求

### 8.4 跨平台

- H2 MVStore：纯 Java，无平台问题
- 整个存储层 v1.1 起**完全摆脱 JNI 依赖**，ARM / 国产化平台零适配成本
- 若后续需要切换到 RocksDB（V4+），届时再评估平台覆盖

### 8.5 与 cluster 文档的关系

本文档描述的是存储层。集群协议、路由、共享订阅的逻辑设计请参考：

- 基础集群：`docs/todo/mqtt-server-cluster.md`
- 路由与共享订阅：`docs/todo/mqtt-server-cluster-routing.md`

---

## 9. 功能检查清单

### 9.1 持久化能力

- [ ] Session 元数据持久化（H2）
- [ ] Retain 消息持久化（H2）+ 内存 Skiplist 通配索引
- [ ] Shared Subscription 状态持久化（H2）
- [ ] QoS 1/2 飞行消息持久化（H2）+ 后台 TTL 清理

### 9.2 集群能力升级

- [x] 集群级 Session 接管（Client Takeover）— SESSION_TAKEOVER 协议
- [x] 离线会话状态漫游 — session 持久化 + 跨节点迁移
- [x] 飞行中消息同步（In-Flight Messages）— H2 + 定时清理重放
- [x] Retain 持久共享 — H2 持久 + 分片复制
- [x] Shared Subscription 持久化 — owner 状态 H2 落盘

### 9.3 可观测性

- [ ] L2 存储大小、读写 QPS 指标
- [ ] Session 接管次数、失败率
- [ ] QoS 1/2 重放次数、丢消息率
- [ ] Retain 消息复制延迟

### 9.4 待办（V4+）

- [ ] 跨数据中心复制（异地容灾）
- [ ] H2 MVStore 跨节点热备份（增量同步）
- [ ] Retain 消息压缩（多版本合并）

---

## 10. 与之前所有方案对比

| 方案 | session | 飞行 | retain | shared sub | 接管时间 | 单点故障 |
|---|---|---|---|---|---|---|
| V1 (纯内存+广播) | - | - | - | - 重复 | N/A | 严重 |
| V2.1 (owner) | - | - | - | + | 15s | owner 单点 |
| V2.2 (主备) | - | - | - | + | 3s | 容忍 1 |
| EMQX dispatcher | - | - | - | + | <1s | 较轻 |
| **V3 + H2（统一引擎）** | + | + | + | + | **<1s** | **几乎无** |

---

**文档版本**：v1.1
**更新日期**：2026-06-05
**状态**：设计稿，待评审
**v1.1 变更摘要**：
- 选型重写：去掉 RocksDB，整个存储层统一为 H2 MVStore（单 jar ~2MB）
- 新增 §1.3.1 评估结论：详细对比 RocksDB / MapDB / RogueMap 的排除理由
- §3.3 / §4.3 重写：Inflight 实现改为 H2 + 后台 TTL 清理线程
- §4.2.4 新增：Retain 内存 Skiplist 索引设计
- §6 配置示例更新：移除 rocksdb 块，新增 inflight / retain-index 配置
- §7 阶段 3 调整：1 周 → 0.5 周
- §8.4 跨平台更新：完全摆脱 JNI 依赖
**配套文档**：
- `docs/todo/mqtt-server-cluster.md`（基础集群）
- `docs/todo/mqtt-server-cluster-routing.md`（路由 + 共享订阅）
