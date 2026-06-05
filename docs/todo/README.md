# mica-mqtt-broker 集群设计文档索引

> mica-mqtt-broker 集群化设计的完整文档体系。从基础到进阶共 4 个文档，按依赖顺序阅读。

---

## 文档结构

```
docs/todo/
├── README.md                          # 本文档: 索引与导读
├── mqtt-server-cluster.md             # [1] 基础集群 (v2.8): 拓扑、消息协议、V1 实现
├── mqtt-server-cluster-routing.md     # [2] 路由 (v1.1): V2 dispatcher + 共享订阅
├── mqtt-server-cluster-storage.md     # [3] 存储 (v1.1): V3 H2 MVStore 持久化层
└── mqtt-server-cluster-tasks.md       # [4] 任务清单 (v1.0): V2/V3 实施分解
```

## 阅读顺序

### 第一遍：理解全貌

| 顺序 | 文档 | 时间 | 解决的问题 |
|---|---|---|---|
| 1 | `mqtt-server-cluster.md` | 30 min | 集群拓扑、节点发现、消息广播、状态同步 |
| 2 | `mqtt-server-cluster-routing.md` | 40 min | 共享订阅 `$share` 怎么去重投递 |
| 3 | `mqtt-server-cluster-storage.md` | 30 min | 节点宕机后状态怎么恢复 |
| 4 | `mqtt-server-cluster-tasks.md` | 20 min | 怎么把 V2/V3 排进迭代 |

### 按角色阅读

| 角色 | 重点文档 |
|---|---|
| **架构师** | 全读，重点 §1 §2 §3 |
| **后端开发** | cluster §3, routing §3, storage §3-§4, tasks §2-§6 |
| **运维** | cluster §5, storage §6 §8, tasks §6 |
| **测试** | cluster §6, routing §7, storage §7, tasks §3-§5 |
| **PM / TL** | tasks 全文 |

---

## 四份文档的依赖关系

```
mqtt-server-cluster.md          (基础, v2.8, 已实现)
        |
        ├─────────────────────────┐
        v                         v
mqtt-server-cluster-routing.md  mqtt-server-cluster-storage.md
  (V2 dispatcher, v1.1)            (V3 H2 持久化, v1.1)
        │                          │
        └──────────┬───────────────┘
                   v
        mqtt-server-cluster-tasks.md
          (V2/V3 实施任务分解, v1.0)
```

- **routing** 与 **storage** 是**正交**的两条演进线：可以只做 routing（V2 共享订阅去重），也可以只做 storage（V3 节点宕机恢复），推荐两个都做
- **tasks** 依赖前三份设计文档，是排期与执行视图

---

## 核心问题与对应文档

| 问题 | 答案在 |
|---|---|
| 集群怎么搭？3 节点最小拓扑？ | `mqtt-server-cluster.md` §2.1, §4 |
| 客户端断线后 session 还在吗？ | `mqtt-server-cluster-storage.md` §4.1 |
| 共享订阅怎么避免重复消费？ | `mqtt-server-cluster-routing.md` §1.2, §3.2 |
| QoS 1 消息节点挂了会丢吗？ | `mqtt-server-cluster-storage.md` §4.3 |
| retain 消息怎么跨节点共享？ | `mqtt-server-cluster-storage.md` §4.2 |
| 节点宕机多久能恢复服务？ | `mqtt-server-cluster-storage.md` §4.1.3 (session 接管) |
| 共享订阅怎么选订阅者？ | `mqtt-server-cluster-routing.md` §3.3-§3.5 |
| 用什么存储引擎？ | `mqtt-server-cluster-storage.md` §1.3, §1.3.1 |
| 怎么排期做 V2/V3？ | `mqtt-server-cluster-tasks.md` §1, §7 |
| 怎么回滚？ | `mqtt-server-cluster-tasks.md` §6.3 (灰度发布) |

---

## 演进路线（推荐阅读顺序）

```
V1 (cluster 文档, 已实现)
  └─ 纯内存 + 全量广播
  └─ 共享订阅有 bug (重复投递)
  │
  ├──► V2 (routing 文档)
  │    └─ EMQX dispatcher 模型
  │    └─ 共享订阅去重
  │    └─ 仍是纯内存
  │
  └──► V3 (storage 文档)
       └─ H2 MVStore (session / retain / shared sub / 飞行消息)
       └─ 节点宕机不再丢状态
       └─ 单一 H2 引擎, 无 RocksDB 依赖

任务分解: tasks 文档 (V2/V3 各 2.5-5 周)
```

---

## 关键设计决策（速查表）

| 决策 | 选择 | 理由 |
|---|---|---|
| 集群通信 | mica-net (TCP) | 已有依赖, 低延迟 |
| 集群消息协议 | 自研 | mica-net 不提供 MQTT 语义 |
| 路由表同步 | 全量复制 | 简单, 决策本地化 |
| 共享订阅路由 | dispatcher 模型 | 消除单点, 复用 V1 全量表 |
| Session 存储 | H2 MVStore | 嵌入式, ACID, 2MB |
| QoS 飞行消息 | H2 MVStore + 30s TTL 线程 | 替代 RocksDB, 单一引擎 |
| Retain 索引 | 内存 Skiplist + H2 持久化 | 零第三方依赖, 10w 级 < 1ms |
| Retain 复制 | 分片 (RF=2) | 避免全节点内存爆炸 |
| 节点身份 | 启动随机 ID | 简单 (一致哈希) |
| 脑裂处理 | 暂不处理 | 部署规范约束 + 重选兜底 |

---

## 实施里程碑

| 里程碑 | 文档章节 | 工作量 | 任务清单 |
|---|---|---|---|
| 集群基础 (V1) | cluster §3-§4 | 已有 | - |
| V2 routing 共享订阅去重 | routing §7 | ~2 周 | tasks P1.0-P1.3 |
| V3 storage 持久化层 | storage §7 | ~3.5 周 | tasks P2.0-P2.4 |
| V2+V3 集成测试 | tasks §5 | ~1 周 | tasks P3.x |
| 生产就绪 + 灰度 | tasks §6 | ~1 周 | tasks P4.x |
| **总计** | | **~8 周**（2 人并行）| 21 个任务 |

MVP 路径（1 人 / 2 周）：tasks §9

---

## 版本与状态

| 文档 | 版本 | 状态 | 更新日期 |
|---|---|---|---|
| `mqtt-server-cluster.md` | v2.8 | 已实现（含已知问题）+ V2/V3 演进路线 | 2026-06-05 |
| `mqtt-server-cluster-routing.md` | v1.1 | 设计稿，待评审 | 2026-06-05 |
| `mqtt-server-cluster-storage.md` | v1.1 | 设计稿，待评审 | 2026-06-05 |
| `mqtt-server-cluster-tasks.md` | v1.0 | 执行中 | 2026-06-05 |
| `README.md` | v1.1 | 索引 | 2026-06-05 |

---

## 反馈

- 发现设计 bug: 请提交 issue 关联对应文档章节
- 建议新的策略实现: 参考 `mqtt-server-cluster-routing.md` §3.4 的接口
- 部署问题: 参考各文档的"注意事项"小节
- 排期问题: 参考 `mqtt-server-cluster-tasks.md` §7, §8

