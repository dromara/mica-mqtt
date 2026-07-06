# mica-mqtt MQTT 5.0 新特性梳理与待实现清单

> **本文档定位**：对照 MQTT 5.0 协议规范（[MQTT v5.0 specification](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)），梳理 mica-mqtt 各模块对 5.0 特性的当前实现情况，列出待实现特性的清单、工作量、依赖、风险与验收标准，作为 MQTT 5.0 兼容性的迭代排期依据。
>
> **配套文档**：
> - `mqtt-server-cluster.md` (v3.0) — 集群基础
> - `mqtt-server-cluster-routing.md` (v1.2) — V2 路由层
> - `mqtt-server-cluster-storage.md` (v1.2) — V3 持久化层
> - `mqtt-server-cluster-tasks.md` (v1.1) — 集群能力任务清单
> - **本文档** (v1.0) — MQTT 5.0 特性梳理与待办

---

## 1. 背景与现状

mica-mqtt 默认连接协议为 `MQTT_5`（参见 [MqttClientProperties](file:///e:/codes/gitee/mica-mqtt/starter/mica-mqtt-client-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/client/config/MqttClientProperties.java#L131)），但 5.0 的**运行时语义**仅实现了一部分。下面按"协议层"和"运行时层"分别梳理。

### 1.1 现状速览

| 层级 | 状态 | 说明 |
|---|---|---|
| 编解码层（`mica-mqtt-codec`） | ✅ 基本完整 | 全部 27 类 Property、10 类 Reason Code、AUTH/DISCONNECT 报文均已能编解码 |
| 服务端运行时（`mica-mqtt-server`） | 🚧 部分实现 | No Local、Message Expiry、Shared Subscription、Session Expiry 已部分生效；其余仅在报文中透传，业务不处理 |
| 客户端运行时（`mica-mqtt-client`） | 🚧 部分实现 | subscribe / publish 支持 properties 参数，但 Topic Alias、Receive Maximum 等流控机制未生效 |
| 集群层（`mica-mqtt-broker`） | 🚧 透传为主 | 集群消息协议 V1 已实现 10 类，Session / Subscription Options 序列化待扩展 |
| HTTP API（`mica-mqtt-server`） | ❌ 薄弱 | 5.0 Properties 透传能力弱 |

---

## 2. MQTT 5.0 协议特性全景

按协议规范章节整理为 6 大类。

### 2.1 报文层能力

| 类别 | 特性 | 作用 |
|---|---|---|
| 报文 | **Reason Code（原因码）** | 所有 ACK / DISCONNECT / AUTH 都带原因码，精确反馈成功或失败原因 |
| 报文 | **Properties（属性）** | 在可变头后追加扩展属性，承载认证、流控、过期、会话等元数据 |
| 报文 | **AUTH 报文** | 新增的认证交换报文，配合扩展认证（如 SCRAM、Kerberos、OAuth） |
| 报文 | **DISCONNECT 报文携带 Reason Code + Properties** | 客户端也能优雅告知服务器断开原因 |

### 2.2 连接与会话

| 类别 | 特性 | 作用 |
|---|---|---|
| 连接 | **Clean Start（替代 Clean Session）** | MQTT 5.0 区分连接是否干净启动 |
| 连接 | **Session Expiry Interval（会话过期）** | 服务器侧会话保留时长，配合持久会话 |
| 连接 | **Receive Maximum（接收上限）** | 双向流量控制，对端允许同时处理的 QoS1/QoS2 未确认报文上限 |
| 连接 | **Maximum Packet Size（最大包大小）** | 限制客户端/服务端可处理的报文上限 |
| 连接 | **Topic Alias Maximum（主题别名上限）** | 协商客户端/服务端支持的最大别名数 |
| 连接 | **Server Keep Alive（服务器接管心跳）** | 服务器可下发自定义心跳覆盖客户端值 |
| 连接 | **Assigned Client Identifier** | 服务器为空 clientId 时分配 |
| 连接 | **Response Information + Request Response Information** | 配合请求/响应模式（request/response pattern） |
| 认证 | **Authentication Method / Authentication Data** | 扩展认证握手，CONNECT/AUTH 报文中传递 |
| 认证 | **Request Problem Information** | 客户端控制是否希望服务器返回 Reason String |

### 2.3 发布消息

| 类别 | 特性 | 作用 |
|---|---|---|
| 发布 | **Message Expiry Interval（消息过期）** | 发布时携带消息有效期 |
| 发布 | **Payload Format Indicator** | 标识 payload 是字节还是 UTF-8 |
| 发布 | **Content Type** | 描述 payload 类型（如 `application/json`） |
| 发布 | **Response Topic** | 请求/响应模式的目标 topic |
| 发布 | **Correlation Data** | 请求/响应模式的关联数据 |
| 发布 | **Topic Alias（主题别名）** | 用 2 字节整数代替长 topic 字符串，节省带宽 |

### 2.4 订阅

| 类别 | 特性 | 作用 |
|---|---|---|
| 订阅 | **Subscription Identifier（订阅标识符）** | 客户端可给每次订阅打标记，服务器转发消息时回传 |
| 订阅 | **Subscription Options（订阅选项）** | 三个布尔位：`No Local`（不收自己发的）、`Retain As Published`（按原样发保留）、`Retain Handling`（如何处理保留消息） |
| 订阅 | **Shared Subscriptions（共享订阅 `$share/{group}/...`）** | 集群内负载均衡，一条消息只投递给组内一个客户端 |
| 订阅 | **Wildcard Subscription Available** | 协商是否允许通配符订阅 |

### 2.5 错误反馈

| 类别 | 特性 | 作用 |
|---|---|---|
| 错误 | **PUBACK / PUBREC / PUBREL / PUBCOMP / SUBACK / UNSUBACK / DISCONNECT 携带 Reason Code** | 之前 MQTT 3.x 失败无应答，5.0 可显式告知 |

### 2.6 报文头改进

| 类别 | 特性 | 作用 |
|---|---|---|
| 头 | **Reason String** | 人类可读的失败原因 |
| 头 | **User Property** | 自定义 K-V 元数据，任意报文都可携带 |

---

## 3. 已实现特性清单（✅）

> 以下特性在 mica-mqtt 中已有可用实现（不仅有编解码，还有运行时语义）。

### 3.1 编解码层（`mica-mqtt-codec`）

| 特性 | 实现位置 | 备注 |
|---|---|---|
| 全部 27 类 Property 编解码 | [MqttPropertyType](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/properties/MqttPropertyType.java) / [MqttEncoder](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttEncoder.java#L463-L543) / [MqttDecoder](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttDecoder.java) | 单/双/四字节、变长整数、UTF-8 字符串、二进制均支持 |
| 全部 10 类 Reason Code | `org.dromara.mica.mqtt.codec.codes.*` | Auth/Connect/ConnAck/Disconnect/PubAck/PubComp/PubRec/PubRel/SubAck/UnSubAck |
| AUTH 报文类型 | [MqttAuthBuilder](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/message/builder/MqttAuthBuilder.java) + [MqttAuthReasonCode](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/codes/MqttAuthReasonCode.java) | 类型定义完整，待运行时处理 |
| Reason Code + Properties 条件编码 | [MqttEncoder](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttEncoder.java#L372-L383) | 仅在 Reason Code 非 0x00 或 Properties 非空时编码，对 MQTT 3.x 兼容良好 |
| 各报文 Properties POJO | `org.dromara.mica.mqtt.codec.message.properties.*` | Connect/ConnAck/Publish/WillPublish/Subscribe/UnSubscribe 等 |

### 3.2 运行时层

| 特性 | 实现位置 | 备注 |
|---|---|---|
| **No Local**（订阅选项） | [DefaultMqttServerProcessor#processSubscribe](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/support/DefaultMqttServerProcessor.java#L360) + [SubscriptionForwardHandler](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L149) | 订阅时记录，发布时过滤 |
| **Message Expiry Interval**（过期 + 递减） | [SubscriptionForwardHandler#rewriteProperties](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L92-L132) | 检查过期 + 剩余时间递减 |
| **Shared Subscription** `$share/{group}/...` | [TrieTopicManager](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/session/TrieTopicManager.java) | 分组共享订阅（`$queue` + `$share`） |
| **Session Expiry Interval**（客户端侧） | [MqttClientProperties](file:///e:/codes/gitee/mica-mqtt/starter/mica-mqtt-client-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/client/config/MqttClientProperties.java#L145) | CONNECT 时下发，服务端有接口签名但被注释 |
| **Subscription Options 编解码** | [MqttEncoder](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttEncoder.java#L226-L240) | RetainAsPublished / RetainHandling / No Local / QoS 都能编解码，但运行时仅 No Local 生效 |

---

## 4. 待实现特性清单（❌ / 🚧）

### 4.1 状态矩阵

| 特性 | codec | server | client | broker | HTTP API |
|---|---|---|---|---|---|
| PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code | ✅ | ❌ | ❌ | ❌ | n/a |
| DISCONNECT Reason Code + Properties（双向） | ✅ | ❌ | ❌ | 🚧 | n/a |
| SUBACK / UNSUBACK Reason Code | ✅ | 🚧 | 🚧 | 🚧 | n/a |
| Retain As Published / Retain Handling（运行时） | ✅ | ❌ | n/a | ❌ | n/a |
| CONNACK Properties 完善（能力位告知） | ✅ | ❌ | ❌ | ❌ | n/a |
| Server Keep Alive | ✅ | ❌ | ❌ | ❌ | n/a |
| Receive Maximum（运行时） | ✅ | ❌ | ❌ | ❌ | n/a |
| Will Properties / Will Delay Interval | ✅ | ❌ | ✅（部分） | ❌ | n/a |
| Subscription Identifier（运行时） | ✅ | ❌ | ❌ | ❌ | n/a |
| Topic Alias（运行时） | ✅ | ❌ | ❌ | ❌ | n/a |
| Assigned Client Identifier | ✅ | ❌ | ❌ | ❌ | n/a |
| Request Problem Information | ✅ | ❌ | ❌ | ❌ | n/a |
| Payload Format Indicator + Content Type | ✅ | 🚧（透传） | 🚧（透传） | 🚧 | ❌ |
| Response Topic + Correlation Data | ✅ | 🚧（透传） | 🚧（透传） | 🚧 | ❌ |
| AUTH 报文处理 + 扩展认证 | ✅ | ❌ | ❌ | ❌ | n/a |
| Server Reference（服务端重定向） | ✅ | ❌ | n/a | ❌ | n/a |
| Response Information（请求/响应模式） | ✅ | ❌ | n/a | ❌ | n/a |
| Clean Start + 完整 Session Expiry Interval | ✅ | 🚧（注释中） | 🚧 | 🚧 | n/a |
| Maximum Packet Size 校验 | ✅ | ❌ | ❌ | ❌ | n/a |
| QoS2 完整 Reason Code（PUBACK/PUBREC/PUBREL/PUBCOMP） | ✅ | ❌ | ❌ | ❌ | n/a |
| Shared Subscription Available 协商 | ✅ | ❌ | ❌ | 🚧 | n/a |
| 集群节点间 Session Expiry / Subscriptions 同步 | n/a | n/a | n/a | 🚧 | n/a |

> 图例：✅ 已实现  🚧 部分实现（仅透传或仅框架）  ❌ 未实现  n/a 不适用

### 4.2 优先级分层（按业务价值 × 实现复杂度）

#### 🟢 第一梯队：高价值 / 低-中复杂度（1-2 周可全部完成）

| # | 特性 | 复杂度 | 收益 | 关键模块 |
|---|---|---|---|---|
| 1 | PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code + Properties | ⭐ | 高 | `DefaultMqttServerProcessor` |
| 2 | DISCONNECT Reason Code + Properties（双向） | ⭐⭐ | 高 | `MqttClient#disconnect` / `DefaultMqttServerProcessor#processDisConnect` |
| 3 | SUBACK / UNSUBACK Reason Code | ⭐ | 高 | `DefaultMqttServerProcessor#processSubscribe` |
| 4 | Retain As Published / Retain Handling（运行时） | ⭐⭐ | 中 | `RetainMessageHandler` / `SubscriptionForwardHandler` |
| 5 | CONNACK Properties 完善（能力位告知） | ⭐ | 中 | `DefaultMqttServerProcessor#connAckByReturnCode` |
| 6 | Server Keep Alive | ⭐ | 中 | `DefaultMqttServerProcessor#processConnect` |
| 7 | Receive Maximum（server → client 方向） | ⭐⭐ | 中 | `MqttPendingPublish` 计数器 + 挂起队列 |
| 8 | Will Properties / Will Delay Interval | ⭐⭐ | 中 | `DefaultMqttServerProcessor` 持久化 + `taskService` 延迟调度 |

**第 1 步总预估工作量**：1-2 周，1 人。

#### 🟡 第二梯队：中价值 / 中高复杂度（2-3 周）

| # | 特性 | 复杂度 | 收益 | 关键模块 |
|---|---|---|---|---|
| 9 | Subscription Identifier（运行时） | ⭐⭐ | 中 | `IMqttSessionManager` 增加 subscribeId 字段 |
| 10 | Topic Alias（运行时） | ⭐⭐ | 中 | 客户端维护 `Map<Integer,String>` |
| 11 | Assigned Client Identifier | ⭐ | 中 | `MqttServerProcessor` 钩子 + CONNACK 回填 |
| 12 | Request Problem Information | ⭐ | 低 | `MqttConnectProperties` 读取，全局开关 |
| 13 | Payload Format Indicator + Content Type（业务透传） | ⭐ | 低 | HTTP API + 示例 |
| 14 | Response Topic + Correlation Data（HTTP API 透传） | ⭐⭐ | 中 | HTTP API 增加字段 |
| 15 | AUTH 报文处理 + 扩展认证骨架 | ⭐⭐⭐ | 高 | `MqttServerAioHandler` 加 AUTH 分支 + `IMqttServerAuthHandler` 多轮接口 |
| 16 | Server Reference（服务端重定向） | ⭐⭐ | 低 | `MqttDisconnectProperties` + DNS/集群信息 |
| 17 | Response Information（请求/响应模式） | ⭐ | 低 | CONNACK 回填 |

**第 2 步总预估工作量**：2-3 周，1 人。

#### 🔴 第三梯队：复杂 / 价值需评估（2-3 周+）

| # | 特性 | 复杂度 | 收益 | 关键模块 |
|---|---|---|---|---|
| 18 | Clean Start + 完整 Session Expiry Interval | ⭐⭐⭐ | 高 | `IMqttSessionManager.expire` + SessionPresent + 重投逻辑 |
| 19 | Receive Maximum（双向完整） | ⭐⭐⭐ | 中 | 第 7 项基础上扩展服务端 in-flight 计数 |
| 20 | Maximum Packet Size 校验 | ⭐⭐ | 中 | `MqttDecoder` 检查 + 越界回 `PACKET_TOO_LARGE` |
| 21 | QoS2 完整 Reason Code | ⭐⭐ | 中 | 业务侧按错误路径构造 reasonCode |
| 22 | Shared Subscription 负载均衡策略抽象 | ⭐⭐ | 低 | `mica-mqtt-broker` 策略接口扩展 |
| 23 | TLS 1.3 + x509 属性提取 | ⭐⭐⭐ | 低 | TLS 证书信息映射为 MQTT 属性 |
| 24 | 集群节点间 Session Expiry / Subscriptions 同步 | ⭐⭐⭐⭐ | 高 | `ClusterMessage` 扩展序列化 |

**第 3 步总预估工作量**：2-3 周+。

---

## 5. 实施任务清单

> 按"补齐基础 → 增强体验 → 高级特性"三步走编排。每个任务独立 PR 可回滚。

### 5.1 任务依赖图

```
[1] Reason Code 全链路
[2] DISCONNECT Reason
[3] SUBACK/UNSUBACK Reason
   ├──► [4] Retain Options
   └──► [5] CONNACK Properties 完善
           ├──► [6] Server Keep Alive
           └──► [7] Receive Maximum（单向）
                  ├──► [8] Will Properties
                  └──► [9] Subscription Identifier
                         ├──► [10] Topic Alias
                         └──► [11] Assigned Client Identifier
                                └──► [12] Request Problem Information
                                       └──► [13] AUTH 报文 + 扩展认证
                                              ├──► [14] Clean Start + Session Expiry
                                              └──► [15] Receive Maximum（双向）
                                                     └──► [16] Maximum Packet Size
                                                            └──► [17] 集群扩展
```

### 5.2 P1 第一梯队（1-2 周）

| 任务 | 标题 | 工作量 | 依赖 | 风险 |
|---|---|---|---|---|
| **P1.1** | PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code + Properties | 2 天 | 无 | 低 |
| **P1.2** | DISCONNECT Reason Code + Properties（双向） | 2 天 | 无 | 低 |
| **P1.3** | SUBACK / UNSUBACK Reason Code | 1 天 | 无 | 低 |
| **P1.4** | CONNACK Properties 完善（能力位告知） | 1 天 | 无 | 低 |
| **P1.5** | Retain As Published / Retain Handling（运行时） | 2 天 | 无 | 中 |
| **P1.6** | Server Keep Alive | 0.5 天 | P1.4 | 低 |
| **P1.7** | Receive Maximum（server → client 方向） | 2 天 | 无 | 中 |
| **P1.8** | Will Properties / Will Delay Interval | 2 天 | 无 | 中 |

### 5.3 P2 第二梯队（2-3 周）

| 任务 | 标题 | 工作量 | 依赖 | 风险 |
|---|---|---|---|---|
| **P2.1** | Subscription Identifier（运行时） | 3 天 | P1.4 | 中 |
| **P2.2** | Topic Alias（运行时） | 2 天 | P1.4 | 中 |
| **P2.3** | Assigned Client Identifier | 1 天 | P1.4 | 低 |
| **P2.4** | Request Problem Information | 0.5 天 | 无 | 低 |
| **P2.5** | Payload Format Indicator + Content Type（透传） | 1 天 | 无 | 低 |
| **P2.6** | Response Topic + Correlation Data（HTTP API 透传） | 2 天 | P2.5 | 中 |
| **P2.7** | AUTH 报文处理 + 扩展认证骨架 | 5 天 | P1.4 | 高 |
| **P2.8** | Server Reference（服务端重定向） | 2 天 | P2.7 | 中 |
| **P2.9** | Response Information（请求/响应模式） | 1 天 | P1.4 | 低 |

### 5.4 P3 第三梯队（2-3 周+）

| 任务 | 标题 | 工作量 | 依赖 | 风险 |
|---|---|---|---|---|
| **P3.1** | Clean Start + 完整 Session Expiry Interval | 5 天 | P2.x | 高 |
| **P3.2** | Receive Maximum（双向完整） | 3 天 | P1.7 + P3.1 | 中 |
| **P3.3** | Maximum Packet Size 校验 | 2 天 | 无 | 低 |
| **P3.4** | QoS2 完整 Reason Code（PUBACK/PUBREC/PUBREL/PUBCOMP） | 2 天 | P1.1 | 中 |
| **P3.5** | Shared Subscription 负载均衡策略抽象 | 3 天 | 无 | 中 |
| **P3.6** | TLS 1.3 + x509 属性提取 | 5 天 | 无 | 中 |
| **P3.7** | 集群节点间 Session Expiry / Subscriptions 同步 | 1 周+ | P3.1 | 高 |

---

## 6. 关键设计决策

### 6.1 Reason Code 全链路（P1.1 / P1.2 / P1.3）

**现状**：`MqttPubReplyMessageVariableHeader` 已支持 reasonCode + properties；`MqttEncoder` 已在 reasonCode 非 0x00 或 properties 非空时编码。

**改造点**：服务端 `DefaultMqttServerProcessor` 各 process 方法的错误分支：

```java
// 例：PUBACK 错误应答
MqttMessage messageAck = MqttPubAckMessage.builder()
    .packetId(packetId)
    .reasonCode(MqttPubAckReasonCode.QUOTA_EXCEEDED.value())
    .reasonString("client quota exceeded")
    .build();
```

**关键 Reason Code 映射**：

| 业务场景 | Reason Code | 值 |
|---|---|---|
| 发布权限拒绝 | `QUOTA_EXCEEDED` | 0x97 |
| 主题无效 | `TOPIC_NAME_INVALID` | 0x90 |
| 报文过大 | `PACKET_TOO_LARGE` | 0x95 |
| 报文速率超限 | `PACKET_RATE_EXCEEDED` | 0x96 |
| 客户端鉴权失败（订阅） | `NOT_AUTHORIZED` | 0x87 |
| 订阅者接管 | `SESSION_TAKEN_OVER` | 0x8E |

### 6.2 Receive Maximum（P1.7）

**现状**：`MqttPendingPublish` 已存在，每次发 QoS1/QoS2 时自增 in-flight 计数；收到 PUBACK/PUBCOMP 时递减。**只是没有做上限拦截。**

**改造点**：
1. CONNACK 读取 server 的 Receive Maximum，写入 `IMqttClientSession`
2. 每次 publish 前判断 in-flight ≥ 上限则挂起到 `pendingPublishQueue`，收到 PUBACK 后重发
3. 默认 65535（协议默认值），可通过配置项调整

### 6.3 AUTH 报文 + 扩展认证（P2.7）

**现状**：`MqttAuthBuilder` + `MqttAuthReasonCode` 已定义；`MqttServerAioHandler` 的 `default` 分支忽略 AUTH 报文。

**改造点**：
1. `MqttServerAioHandler.handler` 增加 `case AUTH`
2. `MqttServerProcessor` 增加 `processAuth(context, MqttAuthMessage)`
3. 新增 `IMqttServerAuthHandler` 多轮扩展认证接口：
   ```java
   public interface IMqttServerAuthHandler {
       AuthPhase onAuthenticate(ChannelContext ctx, String authMethod, byte[] authData);
       // AuthPhase { CONTINUE(回 AUTH), SUCCESS(回 CONNACK), FAILURE(回 DISCONNECT) }
   }
   ```
4. 实现 SCRAM-SHA-256 作为参考实现

**风险**：状态机复杂度高，建议先用 PR 跑通空 AUTH 流程，再叠加真实算法。

### 6.4 Clean Start + Session Expiry（P3.1）

**现状**：
- `IMqttSessionManager.expire(clientId, sessionExpirySeconds)` 接口签名已有，但未在 CONNACK 配合
- `DefaultMqttServerProcessor#processConnect` 第 153-162 行注释中保留了 Session Expiry 的处理占位

**改造点**：
1. CONNECT 读取 Clean Start 标志 + Session Expiry Interval
2. CONNACK 返回 Session Present（基于 cleanStart + expiry）
3. `taskService` 增加过期扫描，触发 `expire(clientId, interval)`
4. 重连时若 expiry 未到，从 H2 / Redis 恢复 session + pending inflight
5. QoS1/QoS2 未确认消息按原 session 状态重投

**依赖**：与 `mqtt-server-cluster-storage.md` 的 V3 持久化强耦合。

### 6.5 HTTP API 升级

**现状**：[mica-mqtt-server-spring-boot-starter](file:///e:/codes/gitee/mica-mqtt/starter/mica-mqtt-server-spring-boot-starter) 与 [mica-mqtt-server-solon-plugin](file:///e:/codes/gitee/mica-mqtt/starter/mica-mqtt-server-solon-plugin) 的 HTTP API 对 5.0 properties 透传能力弱。

**改造点**：所有 publish / subscribe 接口增加 `properties` JSON 参数（透传到 `MqttProperties`）。

---

## 7. 验收标准

每个任务需满足：

1. **功能验收**：
   - 对应 5.0 协议规范章节的所有场景已覆盖
   - 与 MQTT 3.x 客户端兼容性无回归
2. **测试验收**：
   - 单元测试覆盖率 ≥ 80%（核心逻辑）
   - 集成测试通过（启动 broker + MQTTX / Paho 客户端实测）
3. **文档验收**：
   - JavaDoc 完整
   - CHANGELOG.md 同步更新
   - HTTP API 文档同步更新（`docs/http/http-api.md`）
4. **示例验收**：
   - `example/` 模块增加 demo
   - 关键特性附 README 说明

---

## 8. 兼容性矩阵

实施过程中需保持以下兼容性：

| 场景 | 必须保持 |
|---|---|
| MQTT 3.1 / 3.1.1 客户端接入 | 完全兼容，所有 5.0 特性不能导致 3.x 客户端报错 |
| mica-mqtt 旧版本客户端互通 | 完全兼容 |
| EMQX / HiveMQ / VerneMQ 互通 | 主要 Reason Code 对齐 |
| HTTP API | 旧接口签名不能破坏性变更，新功能用可选参数 |

---

## 9. 风险评估

| 风险 | 影响 | 应对 |
|---|---|---|
| 集群消息体积膨胀（特别是 Session Expiry 同步） | 集群带宽 | V3 持久化方案见 storage 文档，避免广播全部 session |
| Topic Alias 状态同步 | 集群内路由错乱 | Topic Alias 不跨节点，集群消息不携带 alias |
| AUTH 状态机复杂度 | 实现 bug | 先跑通空流程再叠加算法；引入状态机测试 |
| 集群 Session Expiry 与单机不一致 | 接管错乱 | 严格按 `mqtt-server-cluster-storage.md` §4.1.3 协议实现 |
| 现有 3.x 客户端被错误返回 5.0 ACK | 兼容性破坏 | `MqttEncoder` 已做条件编码，需在测试矩阵中加入 3.x 客户端验证 |

---

## 10. 版本与状态

| 文档 | 版本 | 状态 | 更新日期 |
|---|---|---|---|
| **mqtt5-features.md** | v1.0 | 初稿 | 2026-07-06 |

### v1.0 变更摘要

- 初版：梳理 MQTT 5.0 协议特性全景，按 mica-mqtt 各模块对照现状
- 列出 4 个待实现矩阵 + 24 个具体待实现特性
- 按 P1（1-2 周）/ P2（2-3 周）/ P3（2-3 周+）三梯队排期
- 设计决策章节给出 Reason Code、Receive Maximum、AUTH、Clean Start、HTTP API 5 个核心改造方案

---

## 11. 反馈

- 发现协议兼容 bug: 提交 issue 关联具体特性编号（如 "特性 1 PUBACK Reason Code"）
- 协议规范疑问: 参考 [OASIS MQTT v5.0](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)
- 设计讨论: 在 PR 中标记 `@L.cm`（dreamlu）review

---

**相关源码导航**：

- 编解码：[mica-mqtt-codec](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec)
- 服务端：[mica-mqtt-server](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-server)
- 客户端：[mica-mqtt-client](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-client)
- 集群：[mica-mqtt-broker](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-broker)
- 启动器：[starter](file:///e:/codes/gitee/mica-mqtt/starter)