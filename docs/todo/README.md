# mica-mqtt-broker 集群设计文档索引

> mica-mqtt-broker 集群化设计的完整文档体系。从基础到进阶共 3 个文档，按依赖顺序阅读。

---

## 文档结构

```
docs/todo/
├── README.md                       # 本文档: 索引与导读
├── mqtt-server-cluster.md          # [1] 基础集群: 拓扑、消息协议、V1 实现
├── mqtt-server-cluster-routing.md  # [2] 路由: 跨节点 PUBLISH 路由 + 共享订阅 (EMQX dispatcher 风格)
└── mqtt-server-cluster-storage.md  # [3] 存储: H2 MVStore + RocksDB 持久化层 (可选升级)
```

## 阅读顺序

### 第一遍：理解全貌

| 顺序 | 文档 | 时间 | 解决的问题 |
|---|---|---|---|
| 1 | `mqtt-server-cluster.md` | 30 min | 集群拓扑、节点发现、消息广播、状态同步 |
| 2 | `mqtt-server-cluster-routing.md` | 40 min | 共享订阅 `$share` 怎么去重投递 |
| 3 | `mqtt-server-cluster-storage.md` | 30 min | 节点宕机后状态怎么恢复 |

### 按角色阅读

| 角色 | 重点文档 |
|---|---|
| **架构师** | 全读，重点 §1 §2 §3 |
| **后端开发** | cluster §3, routing §3, storage §3-§4 |
| **运维** | cluster §5, storage §6 §8 |
| **测试** | cluster §6, routing §7, storage §7 |

---

## 三份文档的依赖关系

```
mqtt-server-cluster.md          (基础，不依赖其他)
        |
        v
mqtt-server-cluster-routing.md  (基于 cluster, 改进共享订阅)
        |
        v
mqtt-server-cluster-storage.md  (可选, 给所有功能加持久化)
```

- **routing** 是 cluster 的进化版（解决 V1 共享订阅重复投递 bug）
- **storage** 是 cluster + routing 的可选加强（解决节点宕机丢状态）

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
| 用 H2 还是 RocksDB？ | `mqtt-server-cluster-storage.md` §1.3, §3 |

---

## 演进路线（推荐阅读顺序）

```
V1 (cluster 文档现状)
  └─ 纯内存 + 全量广播
  └─ 共享订阅有 bug (重复投递)
  |
  v
V2 (routing 文档)
  └─ EMQX dispatcher 模型
  └─ 共享订阅去重
  └─ 仍是纯内存
  |
  v
V3 (storage 文档)
  └─ + H2 MVStore (session / retain / shared sub)
  └─ + RocksDB (QoS 飞行消息, 可选)
  └─ 节点宕机不再丢状态
```

---

## 关键设计决策（速查表）

| 决策 | 选择 | 理由 |
|---|---|---|
| 集群通信 | mica-net (TCP) | 已有依赖, 低延迟 |
| 集群消息协议 | 自研 | mica-net 不提供 MQTT 语义 |
| 路由表同步 | 全量复制 | 简单, 决策本地化 |
| 共享订阅路由 | dispatcher 模型 | 消除单点, 复用 V1 全量表 |
| Session 存储 | H2 MVStore | 嵌入式, ACID |
| QoS 飞行消息 | RocksDB | 高写入, TTL 原生 |
| Retain 复制 | 分片 (RF=2) | 避免全节点内存爆炸 |
| 节点身份 | 启动随机 ID | 简单 (一致哈希) |
| 脑裂处理 | 暂不处理 | 部署规范约束 + 重选兜底 |

---

## 实施里程碑

| 里程碑 | 文档章节 | 工作量 |
|---|---|---|
| 集群基础 (V1) | cluster §3-§4 | 已有 |
| 共享订阅去重 (V2) | routing §7 | ~2 周 |
| 持久化层 (V3) | storage §7 | ~5 周 |
| **总计** | | **~7 周** |

---

## 版本与状态

| 文档 | 版本 | 状态 | 更新日期 |
|---|---|---|---|
| `mqtt-server-cluster.md` | v2.7 | 已实现（含已知问题） | 2026-03-23 |
| `mqtt-server-cluster-routing.md` | v1.0 | 设计稿，待评审 | 2026-06-03 |
| `mqtt-server-cluster-storage.md` | v1.0 | 设计稿，待评审 | 2026-06-03 |
| `README.md` | v1.0 | 索引 | 2026-06-03 |

---

## 反馈

- 发现设计 bug: 请提交 issue 关联对应文档章节
- 建议新的策略实现: 参考 `mqtt-server-cluster-routing.md` §3.4 的接口
- 部署问题: 参考各文档的"注意事项"小节

