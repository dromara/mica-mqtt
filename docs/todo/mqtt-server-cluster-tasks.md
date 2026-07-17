# mica-mqtt-broker 集群能力实施任务清单

> **本文档定位**：将 `cluster` (v3.0) / `routing` (v1.2) / `storage` (v1.2) 三份设计文档中的 V2 / V3 演进内容，**拆解为可独立发布、可回滚的 PR 级别任务**。每个任务给出工作量、依赖、风险、验收标准，可直接作为迭代排期表。
>
> **配套文档**：
> - `mqtt-server-cluster.md` (v3.0) — V1 基础集群
> - `mqtt-server-cluster-routing.md` (v1.2) — V2 路由层
> - `mqtt-server-cluster-storage.md` (v1.2) — V3 持久化层
> - **本文档** (v1.1) — V2/V3 实施任务分解

---

## 1. 总体路线图

> **2026-07-16 实现校准**：P0 与 P2.0-P2.5 主链路已完成；P1 dispatcher 已接入真实 MQTT PUBLISH。3/5 独立 JVM 协议探活、kill -9、成员收敛、同端口重连和 H2 状态恢复已验收；10 万候选策略/Retain 微基线与部署侧 Prometheus 告警已完成。剩余工作集中在网络抖动和端到端 MQTT TPS/兼容性压测（P3）。

### 1.1 时间线（甘特图，团队 2 人并行）

```
Week:  1    2    3    4    5    6    7    8
       |    |    |    |    |    |    |    |
A ----[====P0====]
              \---[==P1.0==]
                    \---[====P1.1====]
                                \---[==P1.2==]
                                       \---[==P1.3==]
B ----[====P2.0====]
              \---[==P2.1==]
                    \---[==P2.2==]
                          \---[==P2.3==]
                                \---[==P2.4==]
                                       \---[====P3====]
                                                     \---[==P4==]

P0  基础接口 (A)
P1  V2 Routing (A)
P2  V3 Storage (B)
P3  V2+V3 集成测试 (A+B)
P4  生产就绪 (A 或 B)
```

### 1.2 任务依赖图

```
P0 (基础)
├── P1.0 strategy 接口 ──► P1.1 5 个 strategy 实现 ──► P1.2 dispatcher 重构
│                                                              │
│                                                              ▼
│                                                       P1.3 边界场景
│                                                              │
└── P2.0 LocalKvStore 接口 ──► P2.1 H2 实现 ──► P2.2 session 持久化
                                       │
                                       ├─► P2.3 shared sub 持久化
                                       ├─► P2.4 inflight + TTL
                                       └─► P2.5 retain 索引
                                                              │
                                                              ▼
                                                       P3 集成测试
                                                              │
                                                              ▼
                                                       P4 生产就绪
```

### 1.3 优先级分层

| 层级 | 任务 | 价值 | 工作量 | 风险 |
|---|---|---|---|---|
| **P0 必须** | P0 / P1.0 / P2.0 | 高 | 1 周 | 低 |
| **P1 重要** | P1.1 / P1.2 / P2.1 / P2.2 | 高 | 3 周 | 中 |
| **P2 增值** | P1.3 / P2.3 / P2.4 | 中 | 1.5 周 | 中 |
| **P3 可选** | P2.5 / P4 | 低 | 1.5 周 | 中 |

---

## 2. 阶段 P0：基础接口（1 周，A 同学）

> **目标**：抽出 V2/V3 共用的接口与基础设施，避免后续 PR 互相阻塞。

### P0.1 `ClusterMessageType` 枚举扩展

| 项 | 内容 |
|---|---|
| 工作量 | 0.5 天 |
| 依赖 | 无 |
| 风险 | 低（纯枚举扩展，向后兼容） |
| 验收 | 11 个新枚举值编译通过；旧消息类型仍可用；`ClusterMessageType.values()` 测试覆盖 |
| 涉及文件 | `cluster/message/ClusterMessageType.java` |
| 设计参考 | cluster v3.0 §3.2 |

```java
// 一次性添加所有 V2/V3 消息类型（避免后续多次改这个文件）
public enum ClusterMessageType {
    // V1（已存在）
    CLIENT_CONNECT(1), ..., STATE_SYNC_RESPONSE(8),
    WILL_MESSAGE(9),
    RETAIN_MESSAGE(10),

    // V2 routing (11-13, P1.x 使用)
    SHARED_DISPATCH_TO_CLIENT(11),
    SHARED_SUBSCRIBE_NOTIFY(12),
    SHARED_SUBSCRIBE_REMOVE(13),

    // V3 storage (14-20, P2.x 使用)
    SESSION_TAKEOVER_REQUEST(14), ..., RETAIN_QUERY(20);
}
```

### P0.2 `LocalKvStore` 抽象接口

| 项 | 内容 |
|---|---|
| 工作量 | 1 天 |
| 依赖 | 无 |
| 风险 | 低（接口设计，参考 storage v1.2 §3.1） |
| 验收 | 接口编译；`H2MvStoreImpl` / `MemoryKvStoreImpl` 两个实现可切换；包含 `executeInTransaction` 事务方法 |
| 涉及文件 | `cluster/store/LocalKvStore.java`（新建） |
| 设计参考 | storage v1.2 §3.1 |

```java
public interface LocalKvStore extends AutoCloseable {
    void open(Path dataDir);
    void close();
    byte[] get(String key);
    void put(String key, byte[] value);
    void delete(String key);
    List<KeyValue> scan(String prefix);  // P2.5 之前可返回空 list
    void executeInTransaction(Runnable body);
    StoreStats stats();
}
```

### P0.3 配置扩展

| 项 | 内容 |
|---|---|
| 工作量 | 0.5 天 |
| 依赖 | P0.1, P0.2 |
| 风险 | 低 |
| 验收 | `MqttStorageConfig` / `MqttClusterConfig` 字段添加完整；YAML 配置可解析 |
| 涉及文件 | `cluster/config/MqttClusterConfig.java` 等 |

### P0.4 度量埋点基座

| 项 | 内容 |
|---|---|
| 工作量 | 1 天 |
| 依赖 | 无 |
| 风险 | 低 |
| 验收 | `ClusterMetrics` 类提供计数器 / 计时器；后续 PR 仅需 `metrics.xxxInc()` 调用 |
| 涉及文件 | `cluster/metrics/ClusterMetrics.java`（新建） |
| 设计参考 | routing v1.2 §8.4 + storage v1.2 §9.3 |

### P0.5 `MqttBroker` 集成入口

| 项 | 内容 |
|---|---|
| 工作量 | 1 天 |
| 依赖 | P0.1 ~ P0.4 |
| 风险 | 中（影响启动流程） |
| 验收 | `MqttBroker.create()` 链式 API 支持 `storageConfig()` 与 `routingConfig()`，未配置时走 V1 行为 |
| 涉及文件 | `cluster/MqttBroker.java`, `MqttClusterBrokerCreator.java` |

**P0 风险点**：
- 接口设计如果偏 V2 倾向，可能阻碍 V3 实现 → **建议 P0 完成前找 1-2 个 review**
- `MqttBroker` 启动顺序调整可能影响现有 V1 测试 → **保留 V1 默认行为，仅当显式配置才启用新模块**

---

## 3. 阶段 P1：V2 Routing（2.5 周，A 同学）

> **目标**：解决 V1 共享订阅重复投递问题（cluster v3.0 §6.3 标记的 🚧 项）
>
> **设计依据**：`mqtt-server-cluster-routing.md` v1.2 全文

### P1.0 `SharedSubscriptionStrategy` 接口与基础设施

| 项 | 内容 |
|---|---|
| 工作量 | 2 天 |
| 依赖 | P0.1, P0.4 |
| 风险 | 低（纯接口 + 5 个简单实现） |
| 验收 | 5 个 strategy 单元测试通过；`MqttClusterConfig.sharedSubStrategy()` 可注入 |
| 涉及文件 | `cluster/pipeline/strategy/*.java`（5 个文件） |
| 设计参考 | routing v1.2 §3.4 |

```java
// 接口设计
public interface SharedSubscriptionStrategy {
    Subscribe pick(String groupName, List<Subscribe> candidates, String localNodeId, Message message);
}

// 5 个实现
// RandomStrategy / RoundRobinStrategy / LocalFirstStrategy
// HashClientStrategy / StickyStrategy
```

**5 个 strategy 实现工作量拆分**：
- 简单：Random, HashClient（无状态）— 各 0.5 天
- 中等：LocalFirst, Sticky（带本地查表）— 各 0.5 天
- 较复杂：RoundRobin（带计数器）— 1 天

**验收测试**（routing v1.2 §7 阶段 2）：
- [x] 目标订阅者掉线 → Node 端重选
- [x] 空 group 跳过
- [x] 大量订阅者下的 strategy 性能（10w 订阅者 < 10ms pick）

### P1.1 Dispatcher 重构

| 项 | 内容 |
|---|---|
| 工作量 | 1 周 |
| 依赖 | P1.0 |
| 风险 | **高**（涉及消息分发核心路径） |
| 验收 | 普通订阅走 V1 行为不变；共享订阅走 strategy + 跨节点单点转发；5 节点集成测试无重复投递 |
| 涉及文件 | `cluster/pipeline/ClusterMessageDispatcher.java`（重写） |
| 设计参考 | routing v1.2 §3.5 / §4 |

**子任务拆分**：

| 子任务 | 工作量 | 备注 |
|---|---|---|
| 1.1.1 `SharedSubscribeNotifyMessage` 实现 + 序列化 | 0.5 天 | 复用 SUBSCRIBE_NOTIFY，isShared 标记 |
| 1.1.2 `SharedDispatchToClientMessage` 实现 | 0.5 天 | 协议消息 11 |
| 1.1.3 dispatcher handle() 拆分普通/共享分支 | 1 天 | 共享分支走 strategy |
| 1.1.4 接收端 `onClusterMessage()` 处理 SHARED_DISPATCH_TO_CLIENT | 0.5 天 | 本地投递 |
| 1.1.5 兼容性测试（V1 节点混合 V2 节点） | 1 天 | 必须保证 V1 节点不报错 |
| 1.1.6 单元测试（mock candidates 列表） | 1 天 | strategy 各种边界 |
| 1.1.7 集成测试（3 节点） | 0.5 天 | 验证无重复投递 |

**P1.1 风险点**：
- V1 节点收到 SHARED_DISPATCH_TO_CLIENT(11) 消息会记录 warning 忽略（已确认是设计预期）
- strategy 选择失败时降级为 V1 广播行为，保证可用性
- dispatcher 拆分会破坏现有 Pipeline 测试，需补 5+ 回归测试

### P1.2 边界场景

| 项 | 内容 |
|---|---|
| 工作量 | 3 天 |
| 依赖 | P1.1 |
| 风险 | 中 |
| 验收 | routing v1.2 §7 阶段 2 全部覆盖 |
| 涉及文件 | `cluster/pipeline/ClusterMessageDispatcher.java` 等 |

**测试用例清单**：
- [x] 目标订阅者掉线 → dispatcher 节点重选
- [x] 订阅者跨节点迁移（client 断开重连到新节点）
- [x] 空 group 跳过（普通订阅的 group 为空）
- [x] group 成员全部离线 → 消息丢弃（不缓存）
- [x] strategy 切换：RoundRobin 切换到 HashClient
- [ ] 网络抖动：dispatcher 消息丢失处理

### P1.3 性能压测

| 项 | 内容 |
|---|---|
| 工作量 | 2 天 |
| 依赖 | P1.2 |
| 风险 | 低 |
| 验收 | 5 节点 / 1w 共享订阅 / 1k TPS 下，CPU < 60%，延迟 P99 < 50ms |
| 涉及文件 | `mica-mqtt-broker/src/test/.../ClusterStressTest.java`（新建） |

---

## 4. 阶段 P2：V3 Storage（5 周，B 同学）

> **目标**：节点宕机后状态不丢失（cluster v3.0 §6.2 标记的未实现项）
>
> **设计依据**：`mqtt-server-cluster-storage.md` v1.2 全文
>
> **核心原则**：单一 H2 引擎（单 jar ~2MB），无 RocksDB 依赖

### P2.0 H2 引擎基础（1 周）

| 项 | 内容 |
|---|---|
| 工作量 | 5 天 |
| 依赖 | P0.2, P0.5 |
| 风险 | 中（H2 配置错误会导致启动失败） |
| 验收 | `H2MvStoreImpl` 通过 `LocalKvStore` 接口测试；启动/关闭/事务三个核心场景 |
| 涉及文件 | `cluster/store/H2MvStoreImpl.java`（新建） |
| 设计参考 | storage v1.2 §3.2 |

**子任务**：

| 子任务 | 工作量 | 备注 |
|---|---|---|
| 2.0.1 引入 H2 依赖（pom.xml） | 0.5 天 | `com.h2database:h2:2.2.224` |
| 2.0.2 `H2MvStoreImpl.open()` / `close()` | 1 天 | 文件创建、目录、错误处理 |
| 2.0.3 `get` / `put` / `delete` 三个基础方法 | 1 天 | 走 `MVStore.openMap()` |
| 2.0.4 `executeInTransaction` 事务 | 0.5 天 | `store.commit()` |
| 2.0.5 `StoreStats` 指标 | 0.5 天 | 文件大小、entry count |
| 2.0.6 单元测试 + 集成测试 | 1 天 | 含断电恢复测试 |
| 2.0.7 `ClusterMqttSessionManager` 接入 | 0.5 天 | 验证 V1 行为不变（仅多写 H2） |

**关键不变量**（storage v1.2 §2.4）：
- Session 写入必须在 CONNACK 前同步落 H2
- 启动时 L1 必须从 L2 完整恢复

**P2.0 风险点**：
- H2 `autoCommitDisabled()` 模式下的 commit 频率与 mica-mqtt 现有 session 写入频率是否匹配
- 文件锁：H2 默认 `FILE_LOCK=FILE`，集群多个 broker 不能共用同一目录
- 测试要包含：kill -9 模拟崩溃 → 重启验证 H2 数据完整

### P2.1 Session 跨节点接管协议（1 周）

| 项 | 内容 |
|---|---|
| 工作量 | 5 天 |
| 依赖 | P2.0 |
| 风险 | **高**（涉及集群协议） |
| 验收 | 客户端 sticky 失败时自动接管；接管时锁、幂等、超时完备；不会重复接管 |
| 涉及文件 | `cluster/message/Session*Message.java`（4 个新消息类） |
| 设计参考 | storage v1.2 §4.1.3 |

**子任务**：

| 子任务 | 工作量 | 备注 |
|---|---|---|
| 2.1.1 4 个 session 消息类（REQUEST/RESPONSE/MIGRATED/DELETE） | 1 天 | 消息 14-17 |
| 2.1.2 `SessionManager.takeover(clientId)` 协议入口 | 1 天 | 失败回退到 V1 行为 |
| 2.1.3 接管时分布式锁（基于 H2 + 内存双重锁） | 1 天 | 防并发接管 |
| 2.1.4 sticky 失败重定向逻辑 | 0.5 天 | t-io 客户端重连触发 |
| 2.1.5 接管测试 + 异常场景 | 1.5 天 | 包括超时、节点宕机、跨版本 |

**P2.1 风险点**：
- 接管时锁的实现：如果用 H2 全局锁会阻塞所有 session
- 接管超时：默认 5s，跨机房场景需调大
- 与 V1 节点共存：V1 节点收到 SESSION_TAKEOVER_REQUEST(14) 应记录 warning 忽略

### P2.2 Shared Subscription 持久化（1 周）

> 当前代码与最初 owner 单写模型有所收敛：V2 dispatcher 已是无中心全量路由，P2.2 因而复用订阅同步消息在所有 V3 节点保存全量 H2 副本，并确定性维护 owner/backup 元数据。协议 18/19 保留兼容位，不作为当前主链路；3/5 JVM kill -9 与 H2 恢复已验收。

| 项 | 内容 |
|---|---|
| 工作量 | 5 天 |
| 依赖 | P2.0, P1.2（P2 本身不依赖 P1，但建议串行） |
| 风险 | 中 |
| 验收 | owner 宕机后 backup 升级为新 owner；零消息真空 |
| 涉及文件 | `cluster/store/SharedSubStore.java`（新建） |
| 设计参考 | storage v1.2 §4.4 |

**子任务**：

| 子任务 | 工作量 | 备注 |
|---|---|---|
| 2.2.1 `mqtt_shared_sub` 表 schema | 0.5 天 | groupName / strategy / members / version / updatedAt |
| 2.2.2 `SharedSubStore` 抽象 + H2 实现 | 1.5 天 | 含 version 乐观锁 |
| 2.2.3 owner/backup 角色管理 | 1 天 | 一致性哈希分片 |
| 2.2.4 `SHARED_SUB_STATE_SYNC(18)` / `SHARED_SUB_TAKEOVER(19)` 消息 | 1 天 | 协议 18-19 |
| 2.2.5 故障切换测试 | 1 天 | owner kill -9 后 backup 接管 |

**P2.2 风险点**：
- owner 选举的一致性问题：脑裂时可能两个节点都认为自己是 owner
- version 乐观锁在并发更新时可能活锁

### P2.3 QoS 1/2 飞行消息 + TTL 清理（0.5 周）

> **v1.1 变更**：原计划 1 周接 RocksDB，现 0.5 周实现 H2 + 后台清理线程。

| 项 | 内容 |
|---|---|
| 工作量 | 2.5 天 |
| 依赖 | P2.0 |
| 风险 | 低 |
| 验收 | inflight 消息 30s TTL 清理；客户端重连可重放 |
| 涉及文件 | `cluster/store/InflightStore.java`, `cluster/store/InflightTtlCleaner.java` |
| 设计参考 | storage v1.2 §4.3 |

**子任务**：

| 子任务 | 工作量 | 备注 |
|---|---|---|
| 2.3.1 `InflightStore` 接口 + `H2InflightStore` 实现 | 1 天 | 共用 P2.0 的 H2 文件，不同 Map |
| 2.3.2 `InflightTtlCleaner` 后台线程（30s 周期） | 0.5 天 | `ScheduledExecutorService` |
| 2.3.3 异步有序写队列（不阻塞发送） | 0.5 天 | 单线程保证同 packet 的 put → PUBREL → ACK 删除不乱序 |
| 2.3.4 滞后堆积告警（>10w 条告警） | 0.5 天 | 接 P0.4 metrics |

**P2.3 验收**：
- [x] TTL 准确：30s 周期清理，滞后 < 60s
- [x] 重放正确：客户端重连后 inflight 全部重发
- [x] 不阻塞发送路径：put 异步有序执行

### P2.4 Retain 持久化 + 内存索引（1 周）

| 项 | 内容 |
|---|---|
| 工作量 | 5 天 |
| 依赖 | P2.0, P2.1 |
| 风险 | 中（涉及通配查询性能） |
| 验收 | retain 消息可持久化；通配查询 < 1ms（10w retain） |
| 涉及文件 | `cluster/store/RetainIndex.java`（新建） |
| 设计参考 | storage v1.2 §4.2.4 |

**子任务**：

| 子任务 | 工作量 | 备注 |
|---|---|---|
| 2.4.1 `mqtt_retain` 表 schema | 0.5 天 | topic / payload / qos / createdAt / replicas |
| 2.4.2 `RetainIndex` 内存 Skiplist 索引 | 1.5 天 | `ConcurrentSkipListMap` |
| 2.4.3 通配查询 `match(topicFilter)` 实现 | 1.5 天 | 提取字面量前缀 + subMap 过滤 |
| 2.4.4 启动时从 H2 重建内存索引 | 0.5 天 | `retainTable.forEach` |
| 2.4.5 性能测试（10w retain 通配查询） | 1 天 | P99 < 5ms |

**P2.4 风险点**：
- 内存索引占用：`ConcurrentSkipListMap` 每个 entry 约 100B，10w 条 = 10MB，可接受
- 写入路径：先 H2 后内存（H2 成功是事实，内存丢失可重建）
- 大消息 retain（>1MB）：value 字节数组可能撑爆内存，需加 maxSize 限制

### P2.5 Retain 分片复制（1 周，可选）

| 项 | 内容 |
|---|---|
| 工作量 | 5 天 |
| 依赖 | P2.4 |
| 风险 | 高（涉及集群分片） |
| 验收 | 10w retain 不会全节点内存爆炸；读路径仍能查本地 + 跨节点 |
| 涉及文件 | `cluster/store/RetainShardRouter.java`, 2 个新消息类 |
| 设计参考 | storage v1.2 §4.2.2 |

> **P2.5 可选原因**：分片方案主要解决"10w+ retain 内存爆炸"问题，对大多数 IoT 场景（<1w retain）不必要。建议先 P2.4 上线，监控 retain 增长再决定。

---

## 5. 阶段 P3：V2 + V3 集成测试（1 周，A+B 共同）

> **目标**：V2 dispatcher 与 V3 持久化层组合验证，确保 dispatcher 状态变更能正确持久化与恢复。

### P3.1 集成测试用例

| 用例 | 验证点 |
|---|---|
| 3 节点全 V3 + dispatcher 模式 | 共享订阅无重复投递 + 节点宕机后状态不丢 |
| 5 节点混合 V1 + V3 | V1 节点兼容；V3 节点持久化生效 |
| QoS 1 飞行消息 + 节点宕机 | inflight 重放正确 |
| Retain 通配查询 + 节点重启 | 内存索引正确重建 |
| Shared Sub owner 切换 | backup 升级，零消息真空 |
| Session 跨节点接管 | sticky 失败后自动接管 |

### P3.2 性能基线

| 指标 | 目标 | 测量方式 |
|---|---|---|
| 单节点 TPS | > 5k | 1k 客户端 / 100 topic 订阅 |
| 集群扩展性 | 5 节点 × 1.2 | 每节点加 20% |
| Session 接管延迟 | < 1s | sticky 失败到接管完成 |
| 节点宕机恢复 | < 5s | kill -9 到可服务 |
| H2 文件大小（1w session 1 天） | < 500MB | 监控 |

### P3.3 兼容性测试

| 场景 | 期望行为 |
|---|---|
| V1 节点 + V2 节点混合集群 | V2 节点共享订阅走 dispatcher；V1 节点收到 SHARED_DISPATCH_TO_CLIENT 忽略 |
| V1 节点 + V3 节点混合集群 | V3 节点持久化生效；V1 节点不报错 |
| 协议版本向下兼容 | 旧版本节点收到新消息类型仅 warning |

---

## 6. 阶段 P4：生产就绪（1 周，A 或 B）

### P4.1 监控与告警

| 指标 | 告警阈值 |
|---|---|
| L2 文件大小 | > 1GB |
| Inflight 滞后堆积 | > 10w 条 |
| Session 接管失败率 | > 1% |
| QoS 1/2 丢消息率 | > 0.1% |
| Retain 复制延迟 | > 5s |
| H2 启动耗时 | > 5s |

### P4.2 文档与迁移指南

| 文档 | 内容 |
|---|---|
| 用户文档 | V2/V3 配置示例（YAML + Java API） |
| 迁移指南 | V1 → V2/V3 升级步骤、回滚方案 |
| 运维手册 | 备份/恢复、监控告警、扩容缩容 |
| 性能调优 | 内存配置、磁盘 IO、网络带宽 |

### P4.3 发布策略

| 阶段 | 灰度比例 | 观察期 | 回滚预案 |
|---|---|---|---|
| 1 | 5% 节点 | 1 周 | 关闭 storage.enabled |
| 2 | 25% 节点 | 1 周 | 同上 |
| 3 | 50% 节点 | 1 周 | 同上 |
| 4 | 100% 节点 | 持续 | 配置回退到 V1 |

---

## 7. 任务清单总览（甘特图 + 责任人）

| ID | 任务 | 工作量 | 依赖 | 责任人 | 起始 | 结束 |
|---|---|---|---|---|---|---|
| P0.1 | ClusterMessageType 扩展 | 0.5d | - | A | W1D1 | W1D1 |
| P0.2 | LocalKvStore 接口 | 1d | - | A | W1D2 | W1D3 |
| P0.3 | 配置扩展 | 0.5d | P0.1, P0.2 | A | W1D4 | W1D4 |
| P0.4 | ClusterMetrics 埋点 | 1d | - | A | W1D4 | W1D5 |
| P0.5 | MqttBroker 入口集成 | 1d | P0.1~P0.4 | A | W1D5 | W1D5 |
| P1.0 | strategy 接口 + 5 实现 | 2d | P0.1, P0.4 | A | W2D1 | W2D2 |
| P1.1 | dispatcher 重构 | 5d | P1.0 | A | W2D3 | W3D2 |
| P1.2 | 边界场景 | 3d | P1.1 | A | W3D3 | W4D1 |
| P1.3 | 性能压测 | 2d | P1.2 | A | W4D2 | W4D3 |
| P2.0 | H2 引擎基础 | 5d | P0.2, P0.5 | B | W1D1 | W1D5 |
| P2.1 | Session 跨节点接管 | 5d | P2.0 | B | W2D1 | W2D5 |
| P2.2 | Shared Sub 持久化 | 5d | P2.0 | B | W3D1 | W3D5 |
| P2.3 | QoS 飞行 + TTL 清理 | 2.5d | P2.0 | B | W4D1 | W4D3 |
| P2.4 | Retain 持久化 + 内存索引 | 5d | P2.0, P2.1 | B | W4D3 | W5D2 |
| P2.5 | Retain 分片复制（可选） | 5d | P2.4 | B | W5D3 | W6D2 |
| P3.1 | 集成测试用例 | 3d | P1.3, P2.4 | A+B | W6D3 | W7D1 |
| P3.2 | 性能基线 | 1d | P3.1 | A+B | W7D2 | W7D2 |
| P3.3 | 兼容性测试 | 1d | P3.2 | A+B | W7D3 | W7D3 |
| P4.1 | 监控告警 | 1d | P3.3 | A | W8D1 | W8D1 |
| P4.2 | 文档迁移指南 | 2d | P3.3 | A 或 B | W8D2 | W8D3 |
| P4.3 | 发布策略与灰度 | 2d | P4.1, P4.2 | A 或 B | W8D4 | W8D5 |

**总计**：8 周（2 人并行）

---

## 8. 风险登记表

| 风险 ID | 描述 | 概率 | 影响 | 缓解措施 |
|---|---|---|---|---|
| R1 | P1.1 dispatcher 重构引入消息丢失 | 中 | 严重 | 100% 单元测试覆盖；灰度发布；监控消息丢失率 |
| R2 | P2.1 接管时锁死锁 | 低 | 严重 | H2 全局锁改为分段锁；超时机制 |
| R3 | P2.2 owner 选举脑裂 | 中 | 严重 | Quorum 机制（3 节点需 2 同意）；版本号防 split-brain |
| R4 | H2 启动慢（>10s） | 中 | 中 | 预热；异步加载；metrics 告警 |
| R5 | P2.4 内存索引内存爆炸 | 低 | 中 | max-in-memory 限制；超出降级为 H2 全表扫 |
| R6 | 集群协议扩展破坏 V1 节点 | 低 | 中 | 旧消息类型保留；新消息类型 V1 节点 warning 忽略 |
| R7 | 团队对 H2 MVStore 事务不熟 | 中 | 中 | P2.0 先做技术验证 PoC；代码 review |
| R8 | 2 人并行引入代码冲突 | 中 | 低 | P0 阶段抽接口解耦；定期 merge |

---

## 9. MVP 路径（如果资源紧张）

> **场景**：团队只有 1 人 / 2 周时间。需要快速产出可演示的功能。

### 最小可用版本（2 周）

| 周 | 任务 | 产出 |
|---|---|---|
| W1 | P0.1 + P0.2 + P0.4 + P1.0 + P2.0 | 接口与基础设施 + H2 基础 |
| W2 | P1.1.1~3 + P1.1.7 + P2.3 + P4.2 | dispatcher 基础 + 飞行消息持久化 + 文档 |

**MVP 验收**：
- [x] 共享订阅无重复投递（自动化分发测试；独立 JVM 验收覆盖协议与成员故障）
- [x] QoS 1 飞行消息持久化（kill -9 重启后状态恢复；重放链路由单元测试覆盖）
- [x] 单一 H2 引擎，无 RocksDB 依赖
- [x] 文档完整（用户视角）

**MVP 不做**：
- Session 跨节点接管（P2.1）
- Shared Sub 持久化（P2.2）
- Retain 持久化（P2.4）
- 分片（P2.5）
- 性能压测（P1.3 / P3.2）
- 兼容性测试（P3.3）

**MVP 后续**：剩余 6 周按 P2.1 → P2.2 → P2.4 → P3 → P4 顺序补齐。

---

## 10. 关键不变量（实施中必须遵守）

> 来自 storage v1.2 §2.4 + cluster v3.0 §3.3

| ID | 不变量 | 验证方式 |
|---|---|---|
| INV-1 | Session 写入必须在 CONNACK 前同步落 H2 | 单元测试 + 集成测试 |
| INV-2 | Retain 写入 L2 落盘后才异步广播 | 单元测试 |
| INV-3 | 共享订阅状态变更需 L2 落盘 + 广播 + 副本 ACK | 集成测试 |
| INV-4 | 节点宕机重启后 L1 必须从 L2 完整恢复 | kill -9 测试 |
| INV-5 | L2 写入失败 → 重试 3 次 → 转异步队列 | 注入故障测试 |
| INV-6 | L2 启动失败 → 允许 Broker 启动但降级为纯内存 | 故障注入 |
| INV-7 | V2/V3 协议消息 V1 节点收到仅 warning 忽略 | 兼容性测试 |
| INV-8 | Inflight TTL 滞后 < 60s | 监控 + 测试 |
| INV-9 | Retain 通配查询 P99 < 5ms（10w 条） | 压测 |

---

## 11. 与三份设计文档的对照表

| 任务 | cluster v3.0 | routing v1.2 | storage v1.2 |
|---|---|---|---|
| P0.1 消息枚举 | §3.2 | §5 | §5 |
| P0.2 LocalKvStore | - | - | §3.1 |
| P1.0 strategy | - | §3.4 | - |
| P1.1 dispatcher | §3.4 | §3.5, §4 | - |
| P1.2 边界场景 | - | §7 阶段 2 | - |
| P2.0 H2 引擎 | - | - | §3.2, §3.3 |
| P2.1 Session 接管 | §3.3 | - | §4.1 |
| P2.2 Shared Sub 持久化 | - | §9.4 | §4.4 |
| P2.3 Inflight + TTL | - | - | §4.3 |
| P2.4 Retain 持久化 | - | - | §4.2.4 |
| P2.5 Retain 分片 | - | - | §4.2.2 |
| P3 集成 | §6 | §7 阶段 3 | §7 |
| P4 生产就绪 | §6.6 | §8.4 | §9.3 |

---

## 12. 总结

| 维度 | 数值 |
|---|---|
| 任务总数 | 21 个（含 P0/P1/P2/P3/P4） |
| 总工作量 | 8 周（2 人并行）/ 14 周（1 人串行） |
| MVP 路径 | 2 周（1 人） |
| 关键路径 | P0.5 → P1.0 → P1.1 → P3.1 → P4.3 |
| 最大风险 | P1.1 dispatcher 重构 + P2.1 接管协议 |
| 最关键依赖 | H2 MVStore 库选型（已确定） |
| 最大产出 | 共享订阅去重 + 节点宕机不丢状态 |

**推荐执行顺序**：
1. W1：P0 + P2.0 并行（2 人）
2. W2-W3：P1.1 + P2.1 并行
3. W4：P1.2/P1.3 + P2.2/P2.3
4. W5：P2.4
5. W6：P2.5（可选）+ P3 集成
6. W7-W8：P4 生产就绪 + 灰度

---

**文档版本**：v1.1
**更新日期**：2026-06-22
**状态**：执行中

**v1.1 变更摘要**（以代码实现为准全面对齐 cluster v3.0）：
- P0.1 代码示例修正：协议号 9-11 → 11-13，补充 WILL_MESSAGE(9)/RETAIN_MESSAGE(10)，移除不存在的 SHARED_SUBSCRIBE_FORWARD/SHARED_PUBLISH_FORWARD
- P1.0 策略接口签名修正：与代码对齐 `pick(String, List, String, Message)`
- P2.1 协议号修正：SESSION_TAKEOVER 12-15 → 14-17
- P2.2 协议号修正：SHARED_SUB_STATE_SYNC 16→18，SHARED_SUB_TAKEOVER 17→19

**配套文档**：
- `docs/todo/mqtt-server-cluster.md` (v3.0)
- `docs/todo/mqtt-server-cluster-routing.md` (v1.2)
- `docs/todo/mqtt-server-cluster-storage.md` (v1.2)
