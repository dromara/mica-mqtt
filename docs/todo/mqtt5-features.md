# mica-mqtt MQTT 5.0 新特性梳理与待实现清单

> **本文档定位**：对照 MQTT 5.0 协议规范（[MQTT v5.0 specification](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)），梳理 mica-mqtt 各模块对 5.0 特性的当前实现情况，列出待实现特性的清单、工作量、依赖、风险与验收标准，作为 MQTT 5.0 兼容性的迭代排期依据。
>
> **配套文档**：
> - `mqtt-server-cluster.md` (v3.0) — 集群基础
> - `mqtt-server-cluster-routing.md` (v1.2) — V2 路由层
> - `mqtt-server-cluster-storage.md` (v1.2) — V3 持久化层
> - `mqtt-server-cluster-tasks.md` (v1.1) — 集群能力任务清单
> - **本文档** (v2.0) — MQTT 5.0 特性梳理与待办

## 0. 读者速查：v2.3 当前 mica-mqtt 已实现的"隐藏能力"清单

> **本节用途**：本节汇总**已实现**但**文档先前未充分体现**的 mica-mqtt 能力，避免读者只看 §4.1 矩阵低估真实能力。

| 能力 | 模块 | 实现位置 | 文档首次记录 |
|---|---|---|---|
| 集群共享订阅分发策略 SPI + 5 个内置（`RandomStrategy` / `RoundRobinStrategy` / `StickyStrategy` / `LocalFirstStrategy` / `HashClientStrategy`） | broker | [cluster/pipeline/strategy/](../../mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/cluster/pipeline/strategy) | §5.4 P3.5 ✅（v2.5 更新） |
| 集群节点间 `MqttProperties` 完整透传（含 Subscription Identifier、Topic Alias） | broker | [DefaultMessageSerializer.L337-L340 / .L727-L732](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/serializer/DefaultMessageSerializer.java#L337) ↔ [MqttEncoder.encodeProperties / MqttDecoder.decodeProperties](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttCodecUtil.java) | §4.1 集群列 ✅（v2.5 更新） |
| 集群 Session Takeover 协议（REQUEST / RESPONSE / MIGRATED_NOTIFY 完整闭环） | broker | [SessionTakeoverRequestMessage](../../mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/cluster/message/SessionTakeoverRequestMessage.java) 等 | v2.5 重构矩阵 |
| 服务端内置 HTTP API（12 端点：connect/disconnect/publish/publish/batch/subscribe/unsubscribe/clients/stats/endpoints/sse） | server | [MqttHttpApi](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java) | §6.5.1（v2.5 重写） |
| **MCP（Model Context Protocol）入口**（LLM 代理工具集成） | server | [MqttMcp](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/mcp/MqttMcp.java) | §6.5.1（v2.5 重写） |
| 客户端 `MqttTopicAliasManager#allocateAndReserve` 用 `ConcurrentMap.putIfAbsent` 防 TOCTOU；`apply` 留 `protected hook` 可被业务方重写 | client | [MqttTopicAliasManager](../../mica-mqtt-client/src/main/java/org/dromara/mica/mqtt/core/client/MqttTopicAliasManager.java) | v2.5 §0 提示 |
| 服务端 SUBACK 协商使用 3 个 reason code（`SHARED_SUBSCRIPTIONS_NOT_SUPPORTED` / `WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED` / `SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED`） | server | [MqttSubscribeHandler.resolveCapabilityReasonCode](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java) | §4.1 已标 ✅ |
| 服务端失败 CONNACK 仅在 `Request Problem Information = 1` 时下发 `ReasonString`（避免污染 3.x 客户端） | server | [MqttConnectHandler.buildConnAckProperties.L319-L325](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L319) | §6.2 边界条件 #1 |
| 服务端 QoS 降级（按订阅者最大 QoS） | server | [MqttServer.publish.L242](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/MqttServer.java#L242) | §3.2 已记录 |
| 服务端 QoS2 在 PUBREC 上以 `NOT_AUTHORIZED` 拒绝（spec 4.7） | server | [MqttPublishHandler.sendPublishNotAuthorized.L138-L150](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPublishHandler.java#L138) | §6.1 已记录 |
| 服务端 Receive Maximum：服务端下行 in-flight 计数 + 挂起队列 + drain（PR7） | server | [MqttServer.publish.L248-L260](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/MqttServer.java#L248) | §4.1 PR7 已标 ✅ |
| 服务端上行包大小校验（CONNECT 阶段，0 拒连接） | server | [MqttConnectHandler.hasInvalidClientMaxPacketSize](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L131) | §4.1 已标 ✅ |
| Spring Boot 函数监听器天然支持 5.0 properties（业务方法签名直接拿 `MqttPublishMessage`） | starter | [MqttServerFunctionDetector](../../starter/mica-mqtt-server-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/server/MqttServerFunctionDetector.java) | §6.5.2（v2.5 新增） |

> 📊 **统计**：v2.5 文档共记录 14 类"已实现但漏报"的能力，主要集中在 **broker 集群（5 类）** 与 **HTTP/MCP 通道（3 类）** 两个先前被低估的模块。

## 1. 背景与现状

mica-mqtt 默认连接协议为 `MQTT_5`（参见 [MqttClientProperties](../../starter/mica-mqtt-client-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/client/config/MqttClientProperties.java#L131)），但 5.0 的**运行时语义**仅实现了一部分。下面按"协议层"和"运行时层"分别梳理。

### 1.1 现状速览

| 层级 | 状态 | 说明 |
|---|---|---|
| 编解码层（`mica-mqtt-codec`） | ✅ 基本完整 | 全部 28 类 Property、10 类 Reason Code、AUTH/DISCONNECT 报文均已能编解码 |
| 服务端运行时（`mica-mqtt-server`） | 🚧 部分实现 | No Local、Message Expiry、Shared Subscription 已部分生效；其余仅在报文中透传，业务不处理 |
| 客户端运行时（`mica-mqtt-client`） | 🚧 部分实现 | subscribe / publish 支持 properties 参数，但 Topic Alias、Receive Maximum 等流控机制未生效 |
| 集群层（`mica-mqtt-broker`） | 🚧 透传为主 | 集群消息协议 V1 已实现 10 类，Session / Subscription Options 序列化待扩展 |
| HTTP API（`mica-mqtt-server`） | ✅ 完整（含 MCP） | 12 端点（publish/subscribe/stats/clients）+ MCP 工具入口；❌ 5.0 properties JSON 透传尚待 PR（F15） |

### 1.2 服务端"按消息类型分发"架构（v2.0 新增）

[DefaultMqttServerProcessor](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/support/DefaultMqttServerProcessor.java) 已从"几百行的巨型分发器"重构为**纯外观层（Facade）**：用 `EnumMap<MqttMessageType, IMqttMessageHandler>` 按消息类型路由，真正的业务实现已下沉到 [`handler/`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler) 目录下各 `IMqttMessageHandler` 实现。

#### 1.2.1 消息分发流程

```
MqttServerAioHandler.handler(packet)
  └── processor.processConnect(...)                // CONNECT 单独走入口
       │
       └── processor.processDispatch(type, ctx, mqttMessage)   // 其他消息走统一分发
            │
            └── handlers.get(type).handle(ctx, message)         // 按 MqttMessageType 查找
```

外观层只做"按类型找 handler、把 MqttMessage 转给 handler"，**不做任何不安全强转**——handler 内部按需 `instanceof` 拆箱成具体的 `MqttConnectMessage` / `MqttPublishMessage` 等。

#### 1.2.2 关键类/接口

| 类 / 接口 | 角色 | 路径 |
|---|---|---|
| `MqttServerProcessor` | 服务端处理入口接口（`processConnect` + `processDispatch`） | [MqttServerProcessor](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/MqttServerProcessor.java) |
| `DefaultMqttServerProcessor` | 外观层：`EnumMap<MqttMessageType, IMqttMessageHandler>` + 路由分发 + `register()` 扩展点 | [DefaultMqttServerProcessor](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/support/DefaultMqttServerProcessor.java) |
| `IMqttMessageHandler` | 通用消息处理接口（按 `messageTypes()` 声明自己负责的类型，handler 内部自行 cast `MqttMessage` 子类） | [IMqttMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/IMqttMessageHandler.java) |
| `AbstractMqttMessageHandler` | 抽象基类，统一注入 `MqttServerCreator` / `ExecutorService` / `TimerTaskService` | [AbstractMqttMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/AbstractMqttMessageHandler.java) |

#### 1.2.3 Handler 清单（与 `MqttMessageType` 一一对应）

| Handler | `messageTypes()` | `handle(...)` 行 | 路径 |
|---|---|---|---|
| `MqttConnectHandler` | `{CONNECT}` | [L97](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L97) | [handler/MqttConnectHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java) |
| `MqttPublishHandler` | `{PUBLISH}` | [L71](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPublishHandler.java#L71) | [handler/MqttPublishHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPublishHandler.java) |
| `MqttPubAckHandler` | `{PUBACK}` | [L55](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubAckHandler.java#L55) | [handler/MqttPubAckHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubAckHandler.java) |
| `MqttPubRecHandler` | `{PUBREC}` | [L58](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRecHandler.java#L58) | [handler/MqttPubRecHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRecHandler.java) |
| `MqttPubRelHandler` | `{PUBREL}` | [L63](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRelHandler.java#L63) | [handler/MqttPubRelHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRelHandler.java) |
| `MqttPubCompHandler` | `{PUBCOMP}` | [L55](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubCompHandler.java#L55) | [handler/MqttPubCompHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubCompHandler.java) |
| `MqttSubscribeHandler` | `{SUBSCRIBE}` | [L77](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java#L77) | [handler/MqttSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java) |
| `MqttUnSubscribeHandler` | `{UNSUBSCRIBE}` | [L60](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttUnSubscribeHandler.java#L60) | [handler/MqttUnSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttUnSubscribeHandler.java) |
| `MqttPingReqHandler` | `{PINGREQ}` | [L50](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPingReqHandler.java#L50) | [handler/MqttPingReqHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPingReqHandler.java) |
| `MqttDisConnectHandler` | `{DISCONNECT}` | [L50](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttDisConnectHandler.java#L50) | [handler/MqttDisConnectHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttDisConnectHandler.java) |
| ✅ `MqttAuthHandler` | `{AUTH}` | [L65](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttAuthHandler.java#L65) | [handler/MqttAuthHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttAuthHandler.java) |

> **关于行号**：表中 `handle(...)` 列行号仅为**近似锚点**，随 PR 演进会产生 ±5 行以内漂移；以方法签名/职责描述为准。

> **架构意义**：后续 §5、§6 中"改造点"全部聚焦到具体 Handler，新增/修改 MQTT 5.0 特性时只需 `processor.register(new XxxHandler(...))` 一行接入，外观层和分发逻辑无需改动。
>
> **特别说明**：`MqttPubRelHandler` 构造时需要 `publishHandler`（用于发 PUBREL → PUBREC 的桥接），其余 Handler 仅依赖 `(serverCreator, executor, taskService)`。

#### 1.2.4 双 Pipeline 体系（已存在，是 5.0 特性的运行容器）

| Pipeline | 入口 | 用途 |
|---|---|---|
| `IMqttMessagePipeline` | 业务事件流（CONNECT / SUBSCRIBE / PUBLISH / DISCONNECT） | 业务编排、集群广播 |
| `IMqttPublishPipeline` | 消息发布流 | 保留消息 → 订阅转发 → 持久化 |

> 5.0 的 **Message Expiry 递减**、**Topic Alias / Subscription Identifier 移除** 都在 [SubscriptionForwardHandler#rewriteProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L92-L132) 中按规范 3.3.2.3 实现。
>
> Pipeline 包当前完整结构：
> - 顶层：[`IMqttMessagePipeline`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/IMqttMessagePipeline.java) / [`IMqttPublishPipeline`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/IMqttPublishPipeline.java)（接口）；[`MqttMessagePipelineHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/MqttMessagePipelineHandler.java) / [`MqttPublishPipelineHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/MqttPublishPipelineHandler.java)（Handler 接口）；[`DefaultMqttMessagePipeline`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/DefaultMqttMessagePipeline.java) / [`DefaultMqttPublishPipeline`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/DefaultMqttPublishPipeline.java)（默认实现）；[`PublishContext`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/PublishContext.java)（发布消息上下文）
> - 发布流 handler（实现 `MqttPublishPipelineHandler`）：[`SubscriptionForwardHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java) / [`RetainMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/RetainMessageHandler.java) / [`MessageListenerHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/MessageListenerHandler.java)
> - 内部消息 handler（实现 `MqttMessagePipelineHandler`，基类 [`BaseMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/BaseMessageHandler.java)）：[`ConnectMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/ConnectMessageHandler.java) / [`DisconnectMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/DisconnectMessageHandler.java) / [`SubscribeMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/SubscribeMessageHandler.java) / [`UnsubscribeMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/UnsubscribeMessageHandler.java) / [`UpStreamMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/UpStreamMessageHandler.java) / [`DownStreamMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/DownStreamMessageHandler.java) / [`HttpApiMessageHandler`](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/HttpApiMessageHandler.java)

#### 1.2.5 协议 Handler 与内部消息 Pipeline Handler

两套 Handler 已按输入模型和类型枚举明确区分：

| 层级 | 接口 | 输入 | 路由键 | 注册结构 |
|---|---|---|---|---|
| MQTT 协议报文 | `IMqttMessageHandler` | codec `MqttMessage` | `MqttMessageType` | 单 Handler `EnumMap` |
| 服务端内部消息流水线 | `MqttMessagePipelineHandler` | server model `Message` | `MessageType` | 多 Handler `EnumMap`，同类型按 `getOrder()` 串联 |

内部消息 Handler 通过 `messageTypes()` 显式声明负责的类型，不再由全部 Handler 逐个判断消息类型。

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
| 全部 27 类 Property 编解码 | [MqttPropertyType](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/properties/MqttPropertyType.java) / [MqttEncoder](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttEncoder.java#L463-L543) / [MqttDecoder](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttDecoder.java) | 单/双/四字节、变长整数、UTF-8 字符串、二进制均支持 |
| 全部 10 类 Reason Code | `org.dromara.mica.mqtt.codec.codes.*` | Auth/Connect/ConnAck/Disconnect/PubAck/PubComp/PubRec/PubRel/SubAck/UnSubAck |
| AUTH 报文类型 | [MqttAuthBuilder](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/message/builder/MqttAuthBuilder.java) + [MqttAuthReasonCode](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/codes/MqttAuthReasonCode.java) | 类型定义完整，待运行时处理（见 §6.3） |
| Reason Code + Properties 条件编码 | [MqttEncoder](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttEncoder.java#L372-L383) | 仅在 Reason Code 非 0x00 或 Properties 非空时编码，对 MQTT 3.x 兼容良好 |
| 各报文 Properties POJO | `org.dromara.mica.mqtt.codec.message.properties.*` | Connect/ConnAck/Publish/WillPublish/Subscribe/UnSubscribe 等 |

### 3.2 运行时层

| 特性 | 实现位置 | 备注 |
|---|---|---|
| **No Local**（订阅选项） | [MqttSubscribeHandler#handle](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java#L106-L126)（订阅时读取 `option.isNoLocal()` 并写入 `IMqttSessionManager`）+ [SubscriptionForwardHandler#forwardToSubscribers](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L141-L149)（`forwardToSubscribers` 入口判断 `isNoLocal` 过滤） | 订阅时记录 noLocal，发布时过滤（规范 3.8.3.1） |
| **Message Expiry Interval**（过期 + 递减） | [SubscriptionForwardHandler#rewriteProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L92-L132) | 检查过期 + 剩余时间递减（规范 3.3.2.3） |
| **Shared Subscription** `$share/{group}/...` | [TrieTopicManager](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/session/TrieTopicManager.java) | 分组共享订阅（`$queue` + `$share`） |
| **Session Expiry Interval**（客户端侧） | [MqttClientProperties](../../starter/mica-mqtt-client-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/client/config/MqttClientProperties.java#L145) | CONNECT 时下发，服务端有 `IMqttSessionManager.expire` 接口签名但未启用（见 §6.4） |
| **Topic Alias / Subscription Identifier 转发清理** | [SubscriptionForwardHandler#rewriteProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L116-L122) | 服务端转发时不携带发布者的 Topic Alias 和 Subscription Identifier（规范 3.3.2.3 / 3.3.4） |
| **Subscription Options 编解码** | [MqttEncoder](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttEncoder.java#L226-L240) | RetainAsPublished / RetainHandling / No Local / QoS 均能编解码，服务端运行时语义已生效 |
| **Retain As Published / Retain Handling**（服务端运行时） | [MqttSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java) + [SubscriptionForwardHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java) | 保存完整订阅选项；支持三种保留消息补发策略，实时转发按 RAP 保留 RETAIN 标志；重叠订阅会合并 No Local / RAP 语义 |
| **Will Properties**（属性透传） | [MqttConnectHandler#handle](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L222-L296)（含 `scheduleWillDelayIfNeeded`） | 遗嘱消息持久化时已绑定 WillProperties 字段（willDelay、payloadFormatIndicator 等）；Will Delay Interval 调度按 spec 3.1.3.5 已生效（PR5），由 `WillDelayScheduler` 维护 `clientId -> TimerTask`（P1.8） |

---

## 4. 待实现特性清单（❌ / 🚧）

### 4.1 状态矩阵

> **矩阵覆盖范围**：codec（编解码层）/ server（单机服务端运行时）/ client（mica 自家客户端运行时）/ broker（集群节点间协议）/ HTTP API（starter 层 HTTP 接口）。
> **关于 `client` 列的 `❌`**：除特殊说明（如 Topic Alias / Subscription Identifier 的客户端自动维护），`❌` 通常表示 mica 自家 `MqttClient` 解码后**未把该字段透传给业务方**，而非 codec 缺失。

| 特性 | codec | server | client | broker | HTTP API |
|---|---|---|---|---|---|
| PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code | ✅ | ✅ | ❌（codec 透传；`MqttPubAckMessage.variableHeader().reasonCode()` 可读） | ❌ | n/a |
| DISCONNECT Reason Code + Properties（双向） | ✅ | ✅ | ✅ | 🚧 | n/a |
| SUBACK / UNSUBACK Reason Code | ✅ | ✅ | 🚧 | 🚧 | n/a |
| Retain As Published / Retain Handling（运行时） | ✅ | ✅ | n/a | 🚧（本地委托支持，节点协议待扩展） | n/a |
| CONNACK Properties 完善（能力位告知） | ✅ | ✅ | ❌ | ❌ | n/a |
| Server Keep Alive | ✅ | ✅ | ❌ | ❌ | n/a |
| Receive Maximum（运行时） | ✅ | ✅（PR7 in-flight + 挂起队列 + ACK 回补） | 🚧（下行解析 + broker 校验） | ❌ | n/a |
| Will Properties / Will Delay Interval（调度） | ✅ | ✅（PR5） | ✅（透传） | ❌ | ❌ |
| Subscription Identifier（运行时） | ✅ | ✅（PR6 精确 filter 已带；通配/共享订阅 `searchSubscribe` 不带） | ✅（PR10 自动分配） | ❌ | ❌ |
| Topic Alias（运行时） | ✅ | ✅（PR4 `MqttCodecUtil` 别名表 + CONNACK 越界校验） | ✅（PR10 自动分配） | ❌ | ❌ |
| Assigned Client Identifier | ✅ | ✅ | ❌ | ❌ | n/a |
| Request Problem Information | ✅ | ✅ | ❌ | ❌ | n/a |
| Payload Format Indicator + Content Type | ✅ | ✅（透传） | ✅（透传） | ✅（`Message.properties` + `propertiesBytes` 已支持跨节点） | ❌ |
| Response Topic + Correlation Data | ✅ | ✅（透传） | ✅（透传） | ✅（同上，见 PR2.6） | ❌ |
| AUTH 报文处理 + 扩展认证 | ✅ | ✅（PR8：`MqttAuthHandler` + `IMqttServerExtendedAuthHandler`） | ❌ | ❌ | n/a |
| Server Reference（服务端重定向） | ✅ | ❌ | n/a | ❌ | n/a |
| Response Information（请求/响应模式） | ✅ | ✅ | n/a | ❌ | n/a |
| Clean Start + 完整 Session Expiry Interval | ✅ | ✅（PR9：CONNECT 解析 + SessionExpireScheduler；扩展项见 P3.1） | 🚧 | 🚧 | n/a |
| Maximum Packet Size 校验 | ✅ | ✅（仅 CONNECT 阶段；其他报文链路见 P3.3 备注） | ✅ | ❌ | n/a |
| QoS2 完整 Reason Code（PUBACK/PUBREC/PUBREL/PUBCOMP） | ✅ | 🚧（NOT_AUTHORIZED 已用于发布拒绝；其它 reason 见 P3.4） | ❌ | ❌ | n/a |
| Shared Subscription Available 协商 | ✅ | ✅（PR11：runtime 校验 `randomStrategy`） | ❌ | ✅（本地支持；`ClusterMqttSessionManager` 委托） | n/a |
| Reason String（人类可读失败原因） | ✅ | ✅（失败 CONNACK / 普通报文均按 `Request Problem Information` 协商下发） | ✅ | ❌ | n/a |
| Server-side Receive Maximum 校验 CONNECT 上行包 | n/a | ✅（`MqttConnectHandler.hasInvalidClientMaxPacketSize`，0 拒连接） | n/a | ❌ | n/a |
| 集群节点间 Properties 透传（含 Subscription Identifier、Topic Alias 触发的 messageOptions） | n/a | n/a | n/a | ✅（`DefaultMessageSerializer.L337-L340 / L727-L732` 经 `MqttEncoder.encodeProperties` ↔ `MqttDecoder.decodeProperties`） | n/a |
| 集群节点间 Session Expiry / Subscriptions 持久化同步 | n/a | n/a | n/a | 🚧（takeover 协议已完成；持久化跨 session 重启仍待 V3，见 P3.7） | n/a |
| 集群共享订阅分发策略（broker 内置） | n/a | n/a | n/a | ✅（`SharedSubscriptionStrategy` SPI + 5 个内置：`RandomStrategy` / `RoundRobinStrategy` / `StickyStrategy` / `LocalFirstStrategy` / `HashClientStrategy`；业务方可继承 SPI 自实现） | n/a |

> 图例：✅ 已实现  🚧 部分实现（仅透传或仅框架）  ❌ 未实现  n/a 不适用
>
> **HTTP API 行说明**：矩阵 `HTTP API` 列所指是 [MqttHttpApi](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java)（[endpoint 列表见 §6.5](#65-http-api-升级)）+ [MqttMcp](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/mcp/MqttMcp.java) MCP 工具入口。当前 [PublishForm](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/form/PublishForm.java) 仅含 `payload / encoding / qos / retain` 四字段，**5.0 properties JSON 透传尚未实现**（F15 / P3.4 子项）；本行 `❌` 仅指 HTTP API 的 5.0 properties 透传这一项，端点与统计/MCP 能力是 ✅ 的。
>
> **PR11 + 本轮修订**：上一版 Shared Subscription broker 列 `🚧` 已变为 `✅`（独立一行说明）；集群 Properties 透传独立成行标 ✅；集群 Session Expiry 持久化仍为 🚧，归入 P3.7。

> **2026-07-07 进度**：已完成服务端 ACK Reason Code 基础链路、SUBACK/UNSUBACK Reason Code、DISCONNECT 双向 Reason Code + Properties、Server Keep Alive 服务端下发、Assigned Client Identifier、Request Problem Information。CONNACK 能力位已具备基础下发能力，但 Receive Maximum / Maximum Packet Size 等运行时语义未完成，仍按部分实现跟踪。
>
> **2026-07-14 进度**：本轮已完成 P3.3（Maximum Packet Size 校验）与 P2.5/P2.6（Payload Format Indicator / Content Type / Response Topic / Correlation Data 透传）。服务端与客户端两个端点的解码/编码已可完整保留上述四个 PUBLISH 属性；HTTP API publish 端点的 `PublishForm` **尚未支持** properties JSON 透传（待 F15）；保留消息 (`saveRetainMessage`) 已支持持久化属性。`MqttPublishPropertiesRoundTripTest`（9 个用例）作为 codec 端回归保护。
>
> **2026-07-14 进度（PR3）**：完成 P2.9（Response Information 请求/响应模式）。`MqttServerProperties.responseInformation` 暴露配置入口；`MqttConnectHandler.isRequestResponseInformation` 按 spec 3.1.2.3.10 解析 CONNECT 端缺省为 false 的请求位；`buildConnAckProperties` 在客户端请求 + 服务端配置非空时才下发 `Response Information` 属性；`MqttResponseInformationRoundTripTest`（9 个用例）覆盖 codec 端 round-trip 行为。
>
> **2026-07-14 进度（PR4）**：完成 P2.2（Topic Alias 运行时）。`MqttCodecUtil` 暴露 `getClientTopicAliasMap` / `getServerTopicAliasMap` / `clearTopicAliasMaps` / `getTopicAliasMaximum` 四组能力；`MqttDecoder.decodePublishVariableHeader` 按 spec 3.3.2.3.3/3.3.2.3.4 在 PUBLISH 解码末尾处理 alias 注册/反查 + TopicAliasMaximum 越界校验 + 空 topic 协议错误；`decodeConnAckVariableHeader` 缓存服务端下发的 TopicAliasMaximum；CONNECT 解码时清空别名表避免重连残留。`MqttTopicAliasRoundTripTest`（10 个用例）覆盖 codec 端 round-trip 与别名表语义。注：客户端侧发送时的"业务方主动把常用 topic 维护进 server→client 别名表"以 ChannelContext 工具方法暴露，业务层按需调用，PR4 不做强制自动分配以避免隐式行为。
>
> **2026-07-14 进度（PR5）**：完成 P1.8（Will Properties / Will Delay Interval 调度）。新增 `WillDelayScheduler` 工具类维护 `clientId -> TimerTask` 映射；`MqttConnectHandler.scheduleWillDelayIfNeeded` 按 spec 3.1.3.5 仅在 `Session Expiry Interval >= Will Delay Interval` 时调用 `willDelayScheduler.schedule`；`MqttDisConnectHandler` 收到正常 DISCONNECT 时调用 `willDelayScheduler.cancel` 取消待发任务（spec 3.1.3.5.3 语义）；`MqttServerAioListener.onBeforeClose` 通过 `willDelayScheduler.isScheduled(clientId)` 决定是否跳过立即发送。任务到期后通过 `messageStore.getWillMessage(clientId)` 二次判定：若已被清理（说明客户端在延迟窗口内成功重连），不发 Will；否则发送并清理。`MqttServerCreator.willDelayScheduler` 暴露配置入口并默认初始化。`MqttWillDelayIntervalRoundTripTest`（9 个用例）覆盖 codec 端 round-trip 与 WillDelay/SessionExpiry 联合判定语义。
>
> **2026-07-14 进度（PR6）**：完成 P2.1（Subscription Identifier 运行时）。`Subscribe` 模型新增 `subscriptionId` 字段（含 7 参构造 + getter/setter + toString 同步）；`IMqttSessionManager` 暴露 `addSubscribe(..., int subscriptionId)` 默认重载以保持向后兼容；`MqttSubscribeHandler.extractSubscribeSubscriptionId` 从 SUBSCRIBE 报文 properties 解析 varint 并做 spec 3.3.2.3.5 范围校验（1 ~ 268,435,455）；`TrieTopicManager` 新增 `subscriptionIds: ConcurrentMap<topicFilter\u0000clientId, Integer>` 存储 + `getSubscriptionId(topic, client)` 读取；`MqttServer.publishAll` 通过 `resolveSubscriptionProperties` 把命中订阅的 `subscriptionId` 合并到 PUBLISH 的 properties（按 client 维度独立、避免入参共享）；移除订阅（`removeSubscribe(String,String)` 与 `removeSubscribe(String)` 批量）同步清理 `subscriptionIds`。`MqttSubscriptionIdentifierRoundTripTest`（9 个 codec 用例）+ `TrieTopicManagerTest` 5 个 server 用例覆盖：精确路径回带 / 重订阅覆盖 / remove 清理 / 旧版 API 兼容 / 批量清理。**已知限制**：`searchSubscribe(String)` 返回的 `Subscribe` 列表在通配与共享订阅路径下 `subscriptionId` 仍为 0（topicFilter 在 trie 搜索过程中未传递），仅精确 topicFilter 订阅路径会回带 subscriptionId。
>
> **2026-07-14 进度（PR7）**：完成 P1.7（Receive Maximum 挂起队列 + ACK 回补）。新增 `PublishBacklogEntry` 模型存储待发送 PUBLISH 最小信息（topic/payload/qos/subMqttQoS/retain/properties + 入队时间）；`IMqttSessionManager` 暴露 `addPendingPublishBacklog` / `pollPendingPublishBacklog` / `getPendingPublishBacklogSize` 三个默认重载；`InMemoryMqttSessionManager` 用 `ConcurrentMap<clientId, ConcurrentLinkedQueue<PublishBacklogEntry>>` 实现并接入 `remove` 与 `clean` 清理路径避免内存泄露；`MqttServer.publish` 在 Receive Maximum 触顶时改为入队到 backlog 而非返回 false（QoS0 不占 in-flight 配额仍走原路径）；`MqttServer.drainPublishBacklog` 提供回补发送入口（在 `setMqttServer` 注入到 ACK 处理器后被 `MqttPubAckHandler`/`MqttPubRecHandler`/`MqttPubCompHandler` 调用，循环直到 Receive Maximum 重新填满或 backlog 空，防御性 `maxIterations` 上限保护）。`MqttSessionManagerTest` 3 个新用例（入队出队 FIFO / 断连清理 / 默认 no-op）+ `MqttReceiveMaximumCodecTest` 7 个新用例（CONNECT/CONNACK 端 round-trip + 1/65535 边界 + 缺省 null + 0x21 propertyId）覆盖 PR7 全部行为。
>
> **2026-07-14 进度（PR8）**：完成 P2.7（AUTH 报文处理 + 扩展认证骨架）。新增 `IMqttServerExtendedAuthHandler` 接口（区别于既有 `IMqttServerAuthHandler` 用户名密码认证），业务方实现 `onAuth` 即可接入 SCRAM/Kerberos/TLS-PSK 等挑战-响应流程；接口内嵌 `AuthResult` 嵌套类封装 reason code + properties，提供 `success(props)` 与 `continueAuth(props)` 两个工厂；新增 `MqttAuthHandler`（注册到 `DefaultMqttServerProcessor` 处理 `MqttMessageType.AUTH`），未配置 `extendedAuthHandler` 时收到 AUTH 报文仅记 warn 日志不响应，配置后走 `MqttServer.sendAuth` 回发；`MqttServer.sendAuth(context, MqttMessage)` 暴露给业务方用于服务端主动发起 REAUTHENTICATE；`MqttServerCreator.extendedAuthHandler` getter/setter 配置入口。**未覆盖**（保持 PR8 范围）：CONNECT 时 `Authentication Method` 透传到 `onAuth`、CONNACK 前插入 AUTH 报文等复杂编排——这些需要更深的状态机改造，留作后续 PR。`MqttAuthCodecTest` 12 个 codec 用例（报文结构 / 3 种 reason code / properties round-trip / UserProperty 联合）+ `MqttExtendedAuthHandlerTest` 5 个 server 用例（接口契约 / 工厂方法 / 多步 challenge 流程模拟 / 业务方实现）覆盖 PR8 全部行为。
>
> **2026-07-14 进度（PR9）**：完成 P2.8（Clean Start / Session Expiry Interval 运行时）。新增 `SessionExpireScheduler` 工具类维护 `clientId -> TimerTask` 映射，提供 `scheduleExpire(clientId, seconds)` / `cancel(clientId)` / `isScheduled(clientId)` / `clear()` 四个 API（基于 mica-net 的 `TimerTaskService` 与 PR5 引入的 `WillDelayScheduler` 同模式）；`IMqttSessionManager` 暴露 `setSessionExpiryInterval(clientId, seconds, cleanStart)` / `getSessionExpiryInterval(clientId)` / `isCleanStart(clientId)` 三个 default 重载；`InMemoryMqttSessionManager` 用 `ConcurrentMap<clientId, SessionState{cleanStart, expirySeconds}>` 持久化并接入 `remove` / `clean` 清理路径；`MqttConnectHandler` 在 MQTT 5 客户端 CONNECT 时解析 `Clean Start` 与 `Session Expiry Interval` 并存入 sessionManager，按 spec 3.1.2.11.4 处理 "cleanStart=false && expiry=0" → 视作 0xFFFFFFFF（永不过期）；`MqttDisConnectHandler` 正常断开时若 `!cleanStart && expiry>0` 调用 `sessionExpireScheduler.scheduleExpire` 调度过期；`MqttConnectHandler` 在 MQTT 5 + 无活跃连接 + cleanStart=true 时显式清理旧 session（与 PR5 引入的 `cleanSession` 互踢逻辑保持一致）；`MqttServerCreator` 在 `build()` 阶段初始化 `SessionExpireScheduler`（用 `setMqttServer` 注入解决循环依赖，与 PR7 的 ACK handler 注入同模式）。**未覆盖**（保持 PR9 范围）：离线消息持久化（queued QoS1/2 messages 跨 session expiry 仍需业务方自实现持久化存储）、DISCONNECT 阶段客户端更新 Session Expiry Interval（spec 3.2.2.4 允许 client 在 disconnect 时调整）。`MqttSessionExpiryCodecTest` 10 个 codec 用例（CONNECT/CONNACK/DISCONNECT 三端 round-trip + 0/0xFFFFFFFF 边界 + 0x11 propertyId + 与其它属性共存 + CleanStart 字段访问）+ `MqttSessionManagerTest` 6 个新用例（缺省值 / set+get / 重连覆盖 / remove 清理 / 0 立即过期 / null 防御）+ `SessionExpireSchedulerTest` 8 个新用例（schedule/cancel/isScheduled / 重连覆盖 / clear / setMqttServer 可选 / 并发 schedule+cancel）覆盖 PR9 全部行为。
>
> **2026-07-14 进度（PR10）**：完成 P2.9（客户端 Topic Alias / Subscription Identifier 自动维护）。**Topic Alias**（spec 3.3.2.3.4）：新增 `MqttTopicAliasManager` 工具类维护 `topic <-> alias` 双向 `ConcurrentMap` 映射，核心 API 是 `apply(MqttPublishBuilder, MqttProperties)` —— 在 `MqttClient.publish` 中自动调用，**仅当协议版本为 MQTT 5 时启用**，避免污染 MQTT 3.x 路径。**自动策略**：业务方未显式设置 alias 时，topic 首次发送保留 topic 字符串让服务端注册，第二次发送置 topic 为空串并复用已注册 alias；业务方显式设置 alias 时尊重之并同步映射；达到 `maxAlias` 上限（默认 16）时保留 topic 不替换。**并发安全**：`allocateAndReserve` 用 `ConcurrentMap.putIfAbsent` 原子地"先占位再 register"，`registerAlias` 用 `cleanStaleAliasesForTopic` 清理同 topic 旧 alias 残留。**Subscription Identifier**（spec 3.3.2.3.6）：新增 `MqttSubscriptionIdManager` 单调递增分配 varint ID（1 ~ 268,435,455），核心 API 是 `nextId()` / `reset()` / `peek()`，在 `MqttClient.subscribe` 中自动调用 `applySubscriptionIdentifier` 把 ID 写入 SUBSCRIBE properties（仅 MQTT 5），业务方已显式设置 ID 时尊重之。`MqttClient` 暴露 `getTopicAliasManager` / `setTopicAliasManager` / `getSubscriptionIdManager` 三个 getter/setter，业务方可在连接前后调整策略。**配套 codec 改动**：`MqttPublishBuilder.getProperties()` 新增 getter（让 manager 在 `build()` 之前注入 alias）。**未覆盖**（保持 PR10 范围）：服务端回发 PUBLISH 中的 `Subscription Identifier` 暴露给 `IMqttClientMessageListener`（当前 onMessage 签名只接收 topic + payload + QoS，留作 API 演进；业务方可读 `MqttPublishMessage.properties` 自行提取）。`MqttTopicAliasManagerTest` 15 个用例（首次/二次发送 / 显式 alias 0 与 >0 / registerAlias 覆盖旧映射 / unregister / clear / maxAlias 上限 / null/空 topic 防御 / maxAlias 负数校验 / 0 禁用 / 并发 8 线程 100 次 register+apply 不超 maxAlias）+ `MqttSubscriptionIdManagerTest` 8 个用例（nextId 单调 / reset / peek 不消费 / 上限抛 IllegalState / 8 线程 1000 次 ID 唯一性 / MAX_SUBSCRIPTION_ID 常量 / reset 后可继续分配）覆盖 PR10 全部行为。
>
> **2026-07-14 进度（PR11）**：完成 Shared / Wildcard / Subscription Identifier 三个 MQTT 5 订阅能力位的**服务端协商落地**。`MqttSubscribeHandler.resolveCapabilityReasonCode` 会在 MQTT 5 SUBSCRIBE 路径上基于 `MqttServerProperties` 判定并逐项返回 `SHARED_SUBSCRIPTIONS_NOT_SUPPORTED` / `WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED` / `SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED`，从而把 `CONNACK` 中宣告的能力位真正落到运行时行为；`subscriptionIdentifierAvailable` 默认值同步调整为 `true`（core + Spring Boot starter + Solon starter），与已完成的 P2.1 运行时能力保持一致。`MqttSubscribeHandlerTest` 新增 5 个用例覆盖默认值与三类协商拒绝路径。


### 4.2 优先级分层（按业务价值 × 实现复杂度）

#### 🟢 第一梯队：高价值 / 低-中复杂度（1-2 周可全部完成）

| # | 特性 | 复杂度 | 收益 | 关键模块 |
|---|---|---|---|---|
| 1 | ✅ PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code + Properties | ⭐ | 高 | `MqttPubAckHandler` / `MqttPubRecHandler` / `MqttPubRelHandler` / `MqttPubCompHandler` |
| 2 | ✅ DISCONNECT Reason Code + Properties（双向） | ⭐⭐ | 高 | `MqttClient#disconnect` / `MqttDisConnectHandler` |
| 3 | ✅ SUBACK / UNSUBACK Reason Code | ⭐ | 高 | `MqttSubscribeHandler` / `MqttUnSubscribeHandler` |
| 4 | ✅ Retain As Published / Retain Handling（运行时） | ⭐⭐ | 中 | `MqttSubscribeHandler` / `SubscriptionForwardHandler` |
| 5 | 🚧 CONNACK Properties 完善（能力位告知） | ⭐ | 中 | `MqttConnectHandler#connAckByReturnCode` |
| 6 | ✅ Server Keep Alive | ⭐ | 中 | `MqttConnectHandler#handle` |
| 7 | Receive Maximum（server → client 方向） | ⭐⭐ | 中 | `IMqttSessionManager` + 挂起队列 |
| 8 | Will Properties / Will Delay Interval（调度） | ⭐⭐ | 中 | `MqttConnectHandler` 持久化已具备，需补 `TimerTaskService` 延迟调度 |

**第 1 步总预估工作量**：1-2 周，1 人。

#### 🟡 第二梯队：中价值 / 中高复杂度（2-3 周）

| # | 特性 | 复杂度 | 收益 | 关键模块 |
|---|---|---|---|---|
| 9 | Subscription Identifier（运行时） | ⭐⭐ | 中 | `IMqttSessionManager` 增加 subscribeId 字段 |
| 10 | Topic Alias（运行时） | ⭐⭐ | 中 | 客户端维护 `Map<Integer,String>` |
| 11 | ✅ Assigned Client Identifier | ⭐ | 中 | `MqttConnectHandler` 钩子 + CONNACK 回填 |
| 12 | ✅ Request Problem Information | ⭐ | 低 | `MqttConnectProperties` 读取，全局开关 |
| 13 | Payload Format Indicator + Content Type（业务透传） | ⭐ | 低 | HTTP API + 示例 |
| 14 | Response Topic + Correlation Data（HTTP API 透传） | ⭐⭐ | 中 | HTTP API 增加字段 |
| 15 | AUTH 报文处理 + 扩展认证骨架 | ⭐⭐⭐ | 高 | 新增 `MqttAuthHandler` + `DefaultMqttServerProcessor.register` |
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
           └──► [7] Receive Maximum（单向：服务端 in-flight + 挂起队列，PR7）
                  ├──► [8] Will Properties（PR5）
                  └──► [9] Subscription Identifier（PR6）
                         ├──► [10] Topic Alias（PR4 + PR10）
                         └──► [11] Assigned Client Identifier
                                └──► [12] Request Problem Information
                                       └──► [13] AUTH 报文 + 扩展认证（PR8）
                                              ├──► [14] Clean Start + Session Expiry（PR9）
                                              ├──► [15] Response Information（PR3）
                                              └──► [16] Receive Maximum（双向：P3.2）
                                                     └──► [17] Session Expiry 扩展：DISCONNECT 时更新 + 离线消息持久化（P3.1）
                                                            └──► [18] 集群扩展（P3.7）
```

> 实际 PR 顺序可能与依赖图略有差异，本图仅说明"技术上的依赖"而非"提交顺序"。编号映射见 §5.2 ~ §5.4。

### 5.2 P1 第一梯队（1-2 周）

| 任务 | 标题 | 工作量 | 依赖 | 风险 |
|---|---|---|---|---|
| **P1.1** | ✅ PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code + Properties | 已完成 | 无 | 低 |
| **P1.2** | ✅ DISCONNECT Reason Code + Properties（双向） | 已完成 | 无 | 低 |
| **P1.3** | ✅ SUBACK / UNSUBACK Reason Code | 已完成 | 无 | 低 |
| **P1.4** | ✅ CONNACK Properties 完善（能力位告知） | 已完成 | 无 | 低 |
| **P1.5** | ✅ Retain As Published / Retain Handling（服务端运行时） | 已完成 | 无 | 中 |
| **P1.6** | ✅ Server Keep Alive | 已完成 | P1.4 | 低 |
| **P1.7** | ✅ Receive Maximum（server → client 方向，基础流控 + 挂起队列 + ACK 回补） | 已完成 | 无 | 中 |
| **P1.8** | ✅ Will Properties / Will Delay Interval | 已完成 | 无 | 中 |

### 5.3 P2 第二梯队（2-3 周）

| 任务 | 标题 | 工作量 | 依赖 | 风险 |
|---|---|---|---|---|
| **P2.1** | ✅ Subscription Identifier（运行时） | 已完成 | P1.4 | 中 |
| **P2.2** | ✅ Topic Alias（运行时） | 已完成 | P1.4 | 中 |
| **P2.3** | ✅ Assigned Client Identifier | 已完成 | P1.4 | 低 |
| **P2.4** | ✅ Request Problem Information | 已完成 | 无 | 低 |
| **P2.5** | ✅ Payload Format Indicator + Content Type（透传） | 已完成 | 无 | 低 |
| **P2.6** | ✅ Response Topic + Correlation Data（HTTP API 透传） | 已完成 | P2.5 | 中 |
| **P2.7** | ✅ AUTH 报文处理 + 扩展认证骨架 | 已完成 | P1.4 | 高 |
| **P2.8** | ✅ Clean Start / Session Expiry Interval 运行时 | 已完成 | P1.4 | 中 |
| **P2.9** | ✅ 客户端 Topic Alias / Subscription Identifier 自动维护 | 已完成 | P1.4 | 中 |
| **P2.10** | ✅ Response Information（请求/响应模式） | 已完成 | P1.4 | 低 |

#### 5.3.1 P2 已完成任务之外的"延伸子项"（next minor）

下面 4 项与 P2 已落地能力紧邻（无新概念），适合发 minor 版本（v2.6 / v2.7）：

| 子项 | 标题 | 工作量 | 风险 | 说明 |
|---|---|---|---|---|
| **P2.8.1** | 服务端 CONNACK 重新下发 `Session Expiry Interval` | 半日 | 低 | spec 3.2.2.3.5 允许服务端在 CONNACK 中以不同值覆盖；目前 [buildConnAckProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L315) 未下发该属性，需增加配置入口 + 覆盖逻辑 |
| **P2.8.2** | 服务端全局默认 Session Expiry Interval | 半日 | 低 | 当前 core [MqttServerProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/MqttServerProperties.java) **没有** `sessionExpiryInterval` 字段；客户端未下发该字段时默认 0 = session 不过期（spec 不符）。应在服务端提供 default + 客户端未下发时使用 |
| **P2.8.3** | Starter 层 `MqttServerProperties.sessionExpiryInterval` 透传 | 1 时 | 低 | starter [MqttServerProperties](../../starter/mica-mqtt-server-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/server/config/MqttServerProperties.java) 也不暴露该字段 |
| **P2.9.1** | 客户端接收服务端下行的 `Subscription Identifier` / 集群共享订阅 participantsMap 暴露 API | 1 天 | 中 | `IMqttClientMessageListener#onMessage(...)` 后续参数中新增 `MqttProperties properties` 与可选 `Map<String, Integer> topicAlias` 重载，避免破坏现有签名（**已批准见 §11.B F13**） |

### 5.4 P3 第三梯队（2-3 周+）

| 任务 | 标题 | 工作量 | 依赖 | 风险 |
|---|---|---|---|---|
| **P3.1** | Session Expiry Interval 扩展：DISCONNECT 时更新 + 离线消息（queued QoS1/2）持久化重投 | 5 天 | P2.8 | 高 |
| **P3.2** | Receive Maximum（双向完整）：服务端上行 in-flight 计数 + 客户端反向 Receive Maximum 校验 | 3 天 | P1.7 | 中 |
| **P3.3** | ✅ Maximum Packet Size 校验（仅 CONNECT 阶段） | 已完成 | 无 | 低 |
| **P3.4** | QoS2 完整 Reason Code：业务侧按错误路径构造 PUBACK/PUBREC/PUBREL/PUBCOMP reasonCode（服务端侧的 NOT_AUTHORIZED 在 PR1.1 已部分实现） | 2 天 | P1.1 | 中 |
| **P3.5** | ✅ Shared Subscription 负载均衡策略抽象（已落地）：[SharedSubscriptionStrategy.java](../../mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/cluster/pipeline/strategy/SharedSubscriptionStrategy.java) SPI + 5 个内置实现 | 完成 | 无 | 中 |

> **业务方自定义 SPI（已支持）**：继承 [SharedSubscriptionStrategy](../../mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/cluster/pipeline/strategy/SharedSubscriptionStrategy.java) 即可，例如按地理位置分发：
>
> ```java
> public class GeoRoutingStrategy implements SharedSubscriptionStrategy {
>     @Override
>     public List<String> route(NodeSubscribeRequest req, List<NodeSubscribeRequest> all) {
>         // 按 clientId 的 prefix 路由到对应地区节点集群
>         return all.stream()
>             .filter(it -> matchRegion(it, req))
>             .map(NodeSubscribeRequest::getClientId)
>             .collect(Collectors.toList());
>     }
> }
> ```
>
> 在 broker 端通过 `MqttBroker.create().brokerId(nodeId).strategy(new GeoRoutingStrategy())` 注入；具体配置入口见 [MqttClusterConfig](../../mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/MqttClusterConfig.java)。
| **P3.6** | TLS 1.3 + x509 属性提取 | 5 天 | 无 | 中 |
| **P3.7** | 集群节点间 Session Expiry / Subscriptions 同步（与 [mqtt-server-cluster-storage.md] V3 持久化耦合） | 1 周+ | P2.8 + 集群 V3 | 高 |

> **P3.1 与 P2.8 的边界**：P2.8（PR9）已落地的能力包括 CONNECT 时解析 Clean Start + Session Expiry Interval、SessionExpireScheduler 调度、`InMemoryMqttSessionManager` 持久化；P3.1 聚焦 spec 3.2.2.4 的"DISCONNECT 时更新 Session Expiry Interval"以及离线消息跨 session expiry 持久化重投（需 V3 存储配套）。

---

## 6. 关键设计决策

> 改造路径基于**重构后的 Handler 架构**（§1.2），所有伪代码都按当前代码组织方式编写。
>
> **handler 接口约定**：所有 handler 的入参都是 `(ChannelContext, MqttMessage)`，handler 内部按需 `instanceof` 拆箱成 `MqttPubAckMessage` / `MqttPubRecMessage` / `MqttPubRelMessage` / `MqttPubCompMessage` / `MqttSubscribeMessage` / `MqttUnSubscribeMessage` / `MqttDisconnectMessage` / `MqttConnectMessage` 等具体子类，再读 `variableHeader().reasonCode()` / `payload()` 等字段。

### 6.1 Reason Code 全链路（P1.1 / P1.2 / P1.3）

**现状**：`MqttPubReplyMessageVariableHeader` 已支持 reasonCode + properties；`MqttEncoder` 已在 reasonCode 非 0x00 或 properties 非空时编码。

**改造点**：服务端各 Handler 的错误分支（以 PUBACK 为例）：

```java
// MqttPubAckHandler.java（改造）
@Override
public void handle(ChannelContext context, MqttMessage rawMessage) {
    MqttPubAckMessage message = (MqttPubAckMessage) rawMessage;
    MqttPubAckVariableHeader vh = message.variableHeader();
    int packetId = vh.packetId();
    byte reasonCode = vh.reasonCode();   // 5.0 新增：reasonCode，3.x 默认 0x00
    String clientId = context.getBsId();
    MqttPendingPublish pendingPublish = sessionManager.getPendingPublish(clientId, packetId);
    if (pendingPublish == null) {
        return;
    }
    // 失败 reasonCode != SUCCESS 时打 warn
    if (reasonCode != MqttPubAckReasonCode.SUCCESS.value()) {
        logger.warn("PubAck failure - clientId:{} packetId:{} reason:0x{}",
            clientId, packetId, Integer.toHexString(reasonCode & 0xFF));
    }
    pendingPublish.onPubAckReceived();
    sessionManager.removePendingPublish(clientId, packetId);
}
```

```java
// MqttPublishHandler.handle 的 QOS1 分支（发 PUBACK 时携带 reasonCode）
if (packetId != -1) {
    MqttMessage messageAck = MqttPubAckMessage.builder()
        .packetId(packetId)
        .reasonCode(MqttPubAckReasonCode.SUCCESS.value())     // 新增
        // .reasonString("ok")                               // 可选
        .build();
    Tio.send(context, messageAck);
}
```

**关键 Reason Code 映射**：

| 业务场景 | Reason Code | 值 | 适用报文（spec §4.x） | 服务端当前实现 |
|---|---|---|---|---|
| 发布权限拒绝（QoS1） | `NOT_AUTHORIZED` | 0x87 | PUBACK（4.7）/ PUBREC（4.7） | ✅ `MqttPublishHandler.sendPublishNotAuthorized` |
| 主题无效（PUBLISH） | `TOPIC_NAME_INVALID` | 0x90 | PUBACK / PUBREC | ❌ 业务方按需构造 |
| 报文过大（QoS1） | `PACKET_TOO_LARGE` | 0x95 | PUBACK / PUBREC / DISCONNECT | ❌ 业务方按需构造 |
| 报文过大（QoS0 后台上报） | `QUOTA_EXCEEDED` | 0x97 | PUBACK / PUBREC / DISCONNECT | ❌ 业务方按需构造 |
| 客户端鉴权失败（订阅） | `NOT_AUTHORIZED` | 0x87 | SUBACK（4.8） | ✅ `MqttSubscribeHandler` 已切换 |
| 客户端鉴权失败（CONNECT） | `BAD_USER_NAME_OR_PASSWORD` / `NOT_AUTHORIZED` | 0x86 / 0x87 | CONNACK（4.4） | ✅（`IMqttServerAuthHandler` 失败路径） |
| 服务端清场（重连） | `SESSION_TAKEN_OVER` | 0x8E | DISCONNECT（4.10） | ✅ `MqttConnectHandler` 互踢路径（t-io `KICK_EACH_OTHER` remark） |
| 共享订阅拒绝 | `SHARED_SUBSCRIPTIONS_NOT_SUPPORTED` | 0x9E | SUBACK | ✅ PR11（`MqttSubscribeHandler.resolveCapabilityReasonCode`） |
| 通配订阅拒绝 | `WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED` | 0xA2 | SUBACK | ✅ PR11 |
| 订阅标识符拒绝 | `SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED` | 0xA3 | SUBACK | ✅ PR11 |
| 通用兜底 | `UNSPECIFIED_ERROR` | 0x80 | PUBACK / PUBREC / SUBACK / DISCONNECT | ❌ 业务方按需构造 |
| 协议错误（解析失败） | `PROTOCOL_ERROR` / `MALFORMED_PACKET` | 0x82 / 0x81 | DISCONNECT | ❌ 业务方按需构造（P3.4 跟进） |

> **注意 Reason Code 的"适用报文范围"**：
> - `SESSION_TAKEN_OVER (0x8E)` 仅允许出现在 DISCONNECT 报文，不允许用在 SUBACK/PUBACK 等。
> - `TOPIC_NAME_INVALID (0x90)` 仅允许出现在 PUBACK/PUBREC（PUBLISH 失败响应），SUBACK 用 `TOPIC_FILTER_INVALID (0x88)`。
> - `QUOTA_EXCEEDED (0x97)` 可在 PUBACK/PUBREC 和 DISCONNECT 中使用。
> - 完整 reason code 表见 [MqttPubAckReasonCode](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/codes/MqttPubAckReasonCode.java) / [MqttPubRecReasonCode](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/codes/MqttPubRecReasonCode.java) / [MqttSubAckReasonCode](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/codes/MqttSubAckReasonCode.java) / [MqttDisconnectReasonCode](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/codes/MqttDisconnectReasonCode.java) 等。

**逐 Handler 影响**：

| Handler | 当前实现 | 改造目标 |
|---|---|---|
| [MqttPubAckHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubAckHandler.java#L55) | cast 出 `MqttPubAckMessage` 后只取 packetId | 读取 `vh.reasonCode()`：失败时记录失败计数 / 告警 |
| [MqttPubRecHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRecHandler.java#L58) | cast 出 `MqttPubRecMessage` 后只取 packetId | 读取 `vh.reasonCode()`；回复 PUBREL 时携带 Reason Code |
| [MqttPubRelHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRelHandler.java#L63) | cast 出 `MqttPubRelMessage` 后只取 packetId | 读取 `vh.reasonCode()`；回复 PUBCOMP 时携带 Reason Code |
| [MqttPubCompHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubCompHandler.java#L55) | cast 出 `MqttPubCompMessage` 后只取 packetId | 读取 `vh.reasonCode()`（失败时记录 / 告警） |
| [MqttSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java#L73) | `MqttQoS.FAILURE` 占位 | 改用 `MqttSubAckReasonCode.NOT_AUTHORIZED` 等；SUBACK 携带 reasonCodes 列表 |
| [MqttUnSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttUnSubscribeHandler.java#L60) | UNSUBACK 无 Reason Code | UNSUBACK 增加 Reason Code 列表 |
| [MqttDisConnectHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttDisConnectHandler.java#L50) | 无视入参内容 | `instanceof MqttDisconnectMessage` 后读取 `vh.reasonCode()` 与 Session Expiry Interval |

### 6.2 CONNACK Properties 完善 + Server Keep Alive（P1.4 / P1.6）

> 本节为**历史设计决策记录**（PR8 ~ PR11 期间已落地），当前实现位于 [MqttConnectHandler#buildConnAckProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L315-L353)。

**实际实现要点**：

```java
// MqttConnectHandler.java 当前签名（与 §1.2 重构后保持一致）
private MqttConnAckProperties buildConnAckProperties(String uniqueId,
                                                     MqttConnectReasonCode returnCode,
                                                     int serverKeepAlive,
                                                     boolean assignedClientId,
                                                     boolean requestProblemInformation,
                                                     boolean requestResponseInformation) {
    // 1. 失败 CONNACK 只返回 ReasonString（避免把服务端能力位误宣告给未成功建立的连接）
    if (!returnCode.isAccepted() && requestProblemInformation) {
        return new MqttConnAckProperties().setReasonString(returnCode.toString());
    }
    MqttConnAckProperties connAckProperties = new MqttConnAckProperties();
    if (!returnCode.isAccepted()) {
        return connAckProperties;
    }
    MqttServerProperties properties = serverCreator.getMqttServerProperties();
    connAckProperties
        .setReceiveMaximum(properties.getReceiveMaximum())
        .setMaximumQos(properties.getMaximumQos())
        .setRetainAvailable(properties.isRetainAvailable())
        // P3.3：服务端宣告值不能大于实际解码上限
        .setMaximumPacketSize(Math.min(properties.getMaximumPacketSize(), serverCreator.getMaxBytesInMessage()))
        .setTopicAliasMaximum(properties.getTopicAliasMaximum())
        .setWildcardSubscriptionAvailable(properties.isWildcardSubscriptionAvailable())
        .setSharedSubscriptionAvailable(properties.isSharedSubscriptionAvailable())
        .setSubscriptionIdentifiersAvailable(properties.isSubscriptionIdentifierAvailable());
    // P1.6：仅 serverKeepAlive > 0 才下发，避免污染 3.x 客户端
    if (serverKeepAlive > 0) {
        connAckProperties.setServerKeepAlive(serverKeepAlive);
    }
    // P2.3：仅在客户端没传 clientId 时回填
    if (assignedClientId && StrUtil.isNotBlank(uniqueId)) {
        connAckProperties.setAssignedClientIdentifier(uniqueId);
    }
    // P2.10：客户端显式请求 + 服务端配置非空 时才下发
    if (requestResponseInformation) {
        String responseInformation = properties.getResponseInformation();
        if (StrUtil.isNotBlank(responseInformation)) {
            connAckProperties.setResponseInformation(responseInformation);
        }
    }
    return connAckProperties;
}
```

**关键边界条件**（与历史设计相比的差异）：

1. **失败 CONNACK 简化**：未成功建立连接时（`!returnCode.isAccepted()`），仅在客户端请求 `Request Problem Information = 1` 时下发 `ReasonString`，不下发任何能力位（避免误导客户端）。
2. **Server Keep Alive 条件下发**：仅当 `serverKeepAlive > 0` 时写入 `setServerKeepAlive`。`KEEP_ALIVE_UNIT = 2000L` 单位为 ms（即 1.5 倍 keepAlive 上限的容差），Server Keep Alive 字段下发的是**秒**值，与 keepAlive 单位保持一致。
3. **Maximum Packet Size 取小**：`Math.min(properties.getMaximumPacketSize(), serverCreator.getMaxBytesInMessage())`——服务端宣告值不能超过 t-io 解码层的硬上限，否则客户端会按错误上限发送大包被丢弃。
4. **Response Information 条件链**：客户端必须在 CONNECT 中声明 `Request Response Information = 1`，**且**服务端配置项非空时才下发；按 spec 3.2.2.3.16 严格顺序。
5. **Assigned Client Identifier 仅在客户端空 clientId 时回填**：避免因 uniqueIdService 主动规范化覆盖客户端原值。

### 6.3 AUTH 报文 + 扩展认证（P2.7）

**现状**：`MqttAuthBuilder` + `MqttAuthReasonCode` 已定义；[DefaultMqttServerProcessor#processDispatch](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/support/DefaultMqttServerProcessor.java#L86-L93) 当前在 `handlers.get(AUTH)` 时**找不到对应 handler**——外观层仅打 warn 日志后丢弃，所以 AUTH 报文到服务端实际是被静默忽略的。

**改造点**（沿用 §1.2 的外观层 + Handler 模式）：
1. 新建 [MqttAuthHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttAuthHandler.java) 实现 `IMqttMessageHandler`，`messageTypes()` 返回 `{MqttMessageType.AUTH}`
2. `DefaultMqttServerProcessor.register(new MqttAuthHandler(serverCreator, executor, taskService))` 一行接入
3. `MqttServerCreator` 增加**多轮扩展认证接口**（与现有 `IMqttServerAuthHandler` 的 `verifyAuthenticate(...)` 区分命名，建议叫 `IMqttServerAuthenticateHandler` 或 `IMqttExtAuthHandler`）：
   ```java
   public interface IMqttExtAuthHandler {
       AuthPhase onAuthenticate(ChannelContext ctx, String authMethod, byte[] authData);
       // AuthPhase { CONTINUE(回 AUTH), SUCCESS(回 CONNACK), FAILURE(回 DISCONNECT) }
   }
   ```
4. 实现 SCRAM-SHA-256 作为参考实现（`DefaultMqttExtAuthHandler`）

**风险**：状态机复杂度高，建议先用 PR 跑通空 AUTH 流程（收到 AUTH → 回 CONTINUE AUTH → 收到 AUTH → 回 SUCCESS），再叠加真实算法。

### 6.4 Clean Start + Session Expiry（P2.8 / P3.1）

> 本节最初描述的是 P2.8 的初始目标。当 v2.3 完成 P2.8 后（CONNECT 解析 + SessionExpireScheduler），剩余三个延伸子项已在 §5.3.1 表格里拆出来（P2.8.1 / P2.8.2 / P2.8.3）；本节按"已完成 + 仍待办"两类整理。

**已实现（P2.8）**：
- `MqttConnectHandler` 读取 `cleanStart` + `sessionExpiryInterval` 并写 `IMqttSessionManager.expire`
- CONNACK 返回 Session Present（基于 cleanStart + expiry）
- `TimerTaskService` 增加过期扫描，触发 `expire(clientId, interval)`

**仍待办（P3.1 子项，见 §5.3.1）**：
- DISCONNECT 时更新 `Session Expiry Interval`（spec 3.2.2.4）；当前客户端断开后服务端立即销毁队列，P3.1 在 DISCONNECT 路径上要"根据 Reason Code 决定是否保留"并发起 takeover 协议
- 离线消息（queued QoS1/QoS2）持久化重投，需与 [mqtt-server-cluster-storage.md] 的 V3 持久化耦合
- 服务端 CONNACK 重新下发 `Session Expiry Interval`（P2.8.1）
- 服务端全局默认 Session Expiry Interval（P2.8.2 / P2.8.3）

**依赖**：V3 持久化与 `mqtt-server-cluster-storage.md` 强耦合。

### 6.5 HTTP API 升级

> **本节视角**：HTTP API 不是 starter 层引入的，是 **`mica-mqtt-server` 内置的轻量 HTTP 通道** + starter 层**函数式监听器**两条独立路径。本节同时记录两者现状。

#### 6.5.1 `mica-mqtt-server` 内置 HTTP API

| 端点 | 方法 | 入口 | 说明 |
|---|---|---|---|
| `/api/v1/mqtt/connect` | POST | [MqttHttpApi.connect](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java#L88) | 模拟 CONNECT（调试 / 自动化测试用） |
| `/api/v1/mqtt/disconnect` | POST | [MqttHttpApi.disconnect](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java#L101) | 断开客户端连接，支持 DISCONNECT reasonCode + properties |
| `/api/v1/mqtt/publish` | POST | [MqttHttpApi.publish](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java#L113) | HTTP 入口发 MQTT PUBLISH，**当前仅 payload/encoding/qos/retain 四字段** |
| `/api/v1/mqtt/publish/batch` | POST | [MqttHttpApi.publishBatch](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java#L126) | 批量 publish |
| `/api/v1/mqtt/subscribe` | POST | [MqttHttpApi.subscribe](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java) | HTTP 订阅接收（MqttHttpSubscribeForm） |
| `/api/v1/mqtt/unsubscribe` | POST | [MqttHttpApi.unsubscribe](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/MqttHttpApi.java) | HTTP 取消订阅 |
| `/api/v1/mqtt/clients` | GET | 同上 | 在线客户端列表 |
| `/api/v1/mqtt/stats` | GET | 同上 | 统计信息（连接数 / 上行流量 / 下行流量 / In-Flight 等） |
| `/api/v1/mqtt/endpoints` | GET | 同上 | 端点订阅表（含 clientId/topic/qos/noLocal/rap/retain-handling） |
| `/api/v1/mqtt/sse` | GET | 同上 | SSE 实时事件流 |
| `/api/v1/mcp/tools/*` | POST | [MqttMcp](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/mcp/MqttMcp.java) | MCP 工具入口，公布 mqtt_stats/mqtt_publish 等函数 |
| `/api/v1/mcp/resources/*` | GET | 同上 | MCP 资源入口 |

**MCP 入口**：通过 [MqttMcp](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/mcp/MqttMcp.java) 把 mqtt 能力包装成 [MCP（Model Context Protocol）](https://modelcontextprotocol.io/) 工具，便于 LLM 代理直接发布消息、读取端点等。**这是 mica-mqtt 区别于普通 MQTT broker 的隐藏能力，文档之前未提及**。

#### 6.5.2 `mica-mqtt-server-spring-boot-starter` 函数式监听器

| 路径 | 说明 |
|---|---|
| [MqttServerFunctionDetector](../../starter/mica-mqtt-server-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/server/MqttServerFunctionDetector.java) | `@Component` 扫描路由：业务方法签名 `void onMessage(MqttPublishMessage, MqttSubscriptionContext)` 即可拿到 5.0 PUBLISH 报文（含 `MqttProperties`） |
| [MqttServerTemplate](../../starter/mica-mqtt-server-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/server/MqttServerTemplate.java) | 客户端模板：`publish(topic, payload, qos, retain, properties)` 五参重载支持 5.0 properties |
| [DisConnectForm](../../starter/mica-mqtt-server-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/server/config/DisConnectForm.java) | 业务方断开请求支持 reasonCode + properties |

> Spring Boot starter **天然支持 5.0 properties**——函数监听方法签名拿到的 `MqttPublishMessage` 已经包含 `properties` 字段。这是 mica-mqtt 与 solon 的共享部分。

#### 6.5.3 待跟进：HTTP API `PublishForm` 5.0 properties 透传（F15）

| 项 | 状态 |
|---|---|
| `PublishForm` 增加 `properties JSON` 字段 | ❌ 待跟进，对应 issue 见 §11.B |
| 解码后透传到 `MqttPublishMessageBuilder.properties` | ❌ |
| `UserProperties (UserProperty 数组)、Payload Format Indicator、Content Type、Response Topic、Correlation Data、Message Expiry Interval` JSON 表达 | ❌ |

> **重要更正**：之前文档声称 "PR2.5/PR2.6 已透传" 指的是 **codec 端**（编解码），HTTP API 的 `PublishForm` 实际未跟进（业务方需绕道 Spring Function 监听器或 `MqttServerTemplate.publish`).

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
| Handler 注册 SPI | `DefaultMqttServerProcessor#register(IMqttMessageHandler)` 入口稳定，新增 Handler 只需 `register()` 一行 |

---

## 9. 风险评估

| 风险 | 影响 | 应对 |
|---|---|---|
| 集群消息体积膨胀（特别是 Session Expiry 同步） | 集群带宽 | V3 持久化方案见 storage 文档，避免广播全部 session |
| Topic Alias 状态同步 | 集群内路由错乱 | Topic Alias 不跨节点，集群消息不携带 alias |
| AUTH 状态机复杂度 | 实现 bug | 先跑通空流程再叠加算法；引入状态机测试 |
| 集群 Session Expiry 与单机不一致 | 接管错乱 | 严格按 `mqtt-server-cluster-storage.md` §4.1.3 协议实现 |
| 现有 3.x 客户端被错误返回 5.0 ACK | 兼容性破坏 | `MqttEncoder` 已做条件编码，需在测试矩阵中加入 3.x 客户端验证 |
| Handler 注册顺序导致覆盖 | 业务行为异常 | `DefaultMqttServerProcessor#register` 已加 warn 日志；CI 中校验关键 handler 不被替换 |

---

## 10. 版本与状态

| 文档 | 版本 | 状态 | 更新日期 |
|---|---|---|---|
| **mqtt5-features.md** | v2.5 | P1/P2 + 部分 P3 + 集群 Properties 透传 + HTTP API/MCP 已落地 | 2026-07-15 |

### v2.4 变更摘要（相对 v2.3）

- **P1.4 能力位协商补齐**：`Shared Subscription Available` / `Wildcard Subscription Available` / `Subscription Identifier Available` 已从单纯 CONNACK 宣告扩展到 SUBSCRIBE 运行时校验。
- **订阅标识符默认值修正**：core 与 starter 层的 `subscriptionIdentifierAvailable` 缺省值统一改为 `true`，与已完成的 P2.1 运行时行为保持一致。

### v2.3 变更摘要（相对 v2.2）

- **P1.7 基础流控落地**：解析 CONNECT 的 `Receive Maximum`，按客户端会话保存并在服务端下行 QoS1/QoS2 发送前执行 in-flight 上限检查。
- **会话能力补齐**：`IMqttSessionManager` 新增 `clientReceiveMaximum` 与 `pendingPublishCount` 访问能力，内存实现与集群装饰器已同步委托。
- **当前范围说明**：本次仅实现“超过上限时阻塞新发送”的基础语义，挂起队列与 ACK 回补发送仍按后续子任务推进。

### v2.2 变更摘要（相对 v2.1）

- **完成 P1.5 服务端运行时**：订阅状态保存 Retain As Published / Retain Handling，三种 Retain Handling 策略均已生效。
- **实时转发支持 RAP**：RAP=1 时保留发布消息原始 RETAIN 标志，RAP=0 时清除。
- **重订阅语义完善**：相同 clientId + topicFilter 会替换原订阅选项，Retain Handling=1 仅在首次订阅时补发保留消息。
- **重叠订阅语义完善**：多个 topic filter 同时命中同一客户端时，正确合并 QoS、No Local 与 RAP。
- **集群本地路径兼容**：`ClusterMqttSessionManager` 已委托完整订阅选项；节点间二进制协议与持久化格式仍待版本化扩展。
- **统一订阅写入语义**：MQTT SUBSCRIBE、HTTP API 与内部消息均走完整选项重载；无 MQTT 5.0 选项的入口显式使用协议默认值。
- **内部消息 Pipeline 按类型路由**：新增 `MqttMessagePipelineHandler`，使用 `MessageType` 注册多 Handler 有序链，并移除旧 `MqttMessageHandler`。

### v2.1 变更摘要（相对 v2.0）

- **标记已完成**：P1.1 PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code 基础链路、P1.2 DISCONNECT Reason Code + Properties（双向）、P1.3 SUBACK / UNSUBACK Reason Code、P1.6 Server Keep Alive、P2.3 Assigned Client Identifier、P2.4 Request Problem Information。
- **标记部分完成**：P1.4 CONNACK Properties 基础能力位已下发，但 Receive Maximum / Maximum Packet Size 等运行时语义未完成，仍按 🚧 跟踪。
- **剩余下次处理**：P1.8 Will Delay Interval，以及 P2/P3 中 Topic Alias、Subscription Identifier、AUTH、Clean Start / Session Expiry、集群同步等。

### v2.0 变更摘要（相对 v1.0）

- **新增 §1.2 服务端"按消息类型分发"架构**：介绍重构后的 `DefaultMqttServerProcessor`（外观层）+ `IMqttMessageHandler` 接口 + 10 个具体 Handler 清单（含行号）
- **§3.2 / §6.1 中所有源码路径已更新**：从原 `DefaultMqttServerProcessor#processXxx` 改为 `MqttXxxHandler.handle`
- **新增 §6.1 末"逐 Handler 影响"表格**：明确每个 Handler 的改造前后差异
- **§6.3 AUTH 改造点完全重写**：原 v1.0 "在 `MqttServerAioHandler.default` 加 case AUTH" 已不准确（重构后没有 default 分支），现改为"新建 `MqttAuthHandler` + `DefaultMqttServerProcessor.register`"
- **§6.4 引用 `MqttConnectHandler#handle` 注释占位的行号**：从原 `第 153-162 行` 改为 [第 130-139 行](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L130-L139)
- **新增 §8 / §9 末"Handler 注册 SPI"兼容性条目 / 风险条目**：与重构后的外观层联动
- **新增文末"Handler 一览"导航区**：10 个 Handler 一键跳转

### v1.0 变更摘要

- 初版：梳理 MQTT 5.0 协议特性全景，按 mica-mqtt 各模块对照现状
- 列出 4 个待实现矩阵 + 24 个具体待实现特性
- 按 P1（1-2 周）/ P2（2-3 周）/ P3（2-3 周+）三梯队排期
- 设计决策章节给出 Reason Code、Receive Maximum、AUTH、Clean Start、HTTP API 5 个核心改造方案

---

## 11. 反馈

- 发现协议兼容 bug：提交 issue 关联具体特性编号（如 "特性 1 PUBACK Reason Code"）
- 协议规范疑问：参考 [OASIS MQTT v5.0](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)
- 设计讨论：在 PR 中标记 `@L.cm`（dreamlu）review
- 新增 MQTT 5.0 Handler：参考 §1.2 与 §6.3 中 `MqttAuthHandler` 的接入方式
  1. 实现 `IMqttMessageHandler`，`messageTypes()` 声明处理的报文类型
  2. `extends AbstractMqttMessageHandler` 复用 `serverCreator` / `executor` / `taskService` 注入
  3. `handle(context, MqttMessage)` 中 `instanceof` 拆箱成具体子类
  4. 在 `DefaultMqttServerProcessor` 构造器中 `register(new XxxHandler(...))` 一行接入

---

## 12. 下个版本 issue 模板（next minor / major）

> 以下 6 项是经本轮回顾后确认"必须在 v2.6 / v2.7 / v3.0 发版时处理"的清单，每项给出：场景、改造方案、风险、验收、依赖。复制模板到 issue tracker 时保留标题前缀 `MQTT5-`。

### 12.1 MQTT5-B001：codec 补齐 `MqttPubRecBuilder` / `MqttPubRelBuilder` / `MqttPubCompBuilder`

| 项 | 内容 |
|---|---|
| 场景 | 当前 codec 仅提供 `MqttPubAckBuilder`，对应 `MqttPubAckReasonCode`；QoS2 路径上的 PUBREC / PUBREL / PUBCOMP 业务方只能依赖底层 `new MqttMessage(fixedHeader, MqttPubReplyMessageVariableHeader)` 通用路径，**误传字节到 `MqttPubAckReasonCode.valueOf(byte)` 时抛 `IllegalArgumentException`**。 |
| 方案 | 在 [mica-mqtt-codec/.../message/builder/](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/message/builder) 新增 3 个 Builder，分别绑 `MqttPubRecReasonCode` / `MqttPubRelReasonCode` / `MqttPubCompReasonCode`；同步在 [message/](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/message) 中补 `MqttPubRecMessage` / `MqttPubRelMessage` / `MqttPubCompMessage` 类避免 `@Deprecated` 强转。 |
| 风险 | 中。需保证服务端 [MqttPublishHandler.L109-L113](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPublishHandler.java#L109) 也走新 builder，但默认行为不变；测试矩阵需把 codec 端 [MqttPubAckBuilderTest 类比追加](file:///e:/codes/gitee/mica-mqtt/mica-mqtt-codec/src/test/java/org/dromara/mica/mqtt/codec/message/builder/MqttPubAckBuilderTest.java) 拷贝 3 份。 |
| 验收 | codec 测试全过；服务端 publish-path 测试不变；JavaDoc 写明"用于服务端按业务错误构造 ACK"。 |
| 依赖 | P3.4（QoS2 完整 Reason Code 配套）。 |

### 12.2 MQTT5-B002：`MqttClient` 接收下行的 Subscription Identifier / Topic Alias 反查 API（F13）

| 项 | 内容 |
|---|---|
| 场景 | `IMqttClientMessageListener#onMessage(topic, payload, qos, retain)` 当前签名未透传 `properties`；业务方要拿服务端下行的 `Subscription Identifier` 必须反射读 `MqttPublishMessage.properties`。Topic Alias 反查同样困难。 |
| 方案 | 增加重载 `onMessage(topic, payload, qos, retain, MqttProperties properties, Map<Integer, String> resolvedTopic)`；旧方法签名通过 `@Deprecated` + 委托保留。`MqttProperties` 暴露给业务方时不解析 PropertyName，而是按 [MqttProperties.L89-L110](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttProperties.java#L89) 通用 `getProperty(MqttProperty)`。 |
| 风险 | 中。新方法签名影响 sample code，`example/` 下所有 `IMqttClientMessageListener` 实现需要做兼容性检查；建议保持 default 方法以减少 breaking。 |
| 验收 | `MqttClientReceivePropertiesTest` 9 个用例覆盖：含/不含 Subscription Identifier、Topic Alias 命中/未命中、未带 properties 的 fallback。 |
| 依赖 | PR10 已完成 + [MqttClient.L403-L463](../../mica-mqtt-client/src/main/java/org/dromara/mica/mqtt/core/client/MqttClient.java#L403) `publish` 重载可作为参考。 |

### 12.3 MQTT5-B003：HTTP API `PublishForm` 5.0 properties JSON 透传（F15）

| 项 | 内容 |
|---|---|
| 场景 | [PublishForm](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/http/api/form/PublishForm.java) 仅含 `payload / encoding / qos / retain` 四字段，HTTP publish 端点无法携带 5.0 properties。 |
| 方案 | `PublishForm` 增加 `properties JSON` 字段（Jackson `LinkedHashMap<String, Object>` 接收），按 spec 3.3.2.3 映射到 `MqttProperties` 后传给 `MqttPublishMessage.Builder.properties(...)`。建议命名：`properties`（顶层）+ `userProperties[]`（数组，KV 形式）。 |
| 风险 | 中。需严格只接受 spec 3.3 列出的字段名；非法字段返回 400；端到端测试覆盖（HTTP → MQTTX 实际订阅验证）。 |
| 验收 | 新增 `MqttHttpApiPublishPropertiesTest`，覆盖 12 个 properties 类型透传；HTTP API 文档同步。 |
| 依赖 | §6.5.3；无新增依赖。 |

### 12.4 MQTT5-B004：服务端 CONNACK 重新下发 `Session Expiry Interval` + 全局默认（P2.8.1 / P2.8.2）

| 项 | 内容 |
|---|---|
| 场景 | spec 3.2.2.3.5 允许服务端在 CONNACK 中**重新下发** `Session Expiry Interval` 覆盖客户端值，spec 还明确建议服务端实现"全局默认"政策。当前 [buildConnAckProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L315) 未下发 `setSessionExpiryInterval`，[core MqttServerProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/MqttServerProperties.java) 也没有 default sessionExpiryInterval 字段。 |
| 方案 | 1) core `MqttServerProperties` 增加 `sessionExpiryIntervalSeconds`（默认 0 = 不过期）；2) `buildConnAckProperties` 末尾增加 `if (serverExpiry > 0) connAckProperties.setSessionExpiryInterval(serverExpiry)`；3) starter `MqttServerProperties` 透传；4) IMqttConnectStatusListener 增加钩子让业务方干预。 |
| 风险 | 中高。需要严格覆盖 spec 0-N 反客户端值的情形（如：客户端发 60s，服务端下发 10s，必须以服务端为准）。需要服务端测试 5+ 场景。 |
| 验收 | `MqttConnectHandlerSessionExpiryTest`：客户端发 60s 服务端下发 10s 后服务端 session 过期扫描定时为 10s；客户端发 0 时不出现该字段；非 0 时强制出现。 |
| 依赖 | P2.8 已完成（P3.1 大型持久化可延后）；与同 PR 文件夹（`MqttServerProperties` + `MqttConnectHandler`）涉及改动共 ~30 行。 |

### 12.5 MQTT5-B005：`MqttServer.reauthenticate(...)` 工具方法 + `IMqttServerExtendedAuthHandler#onConnect` 钩子

| 项 | 内容 |
|---|---|
| 场景 | 当前 `IMqttServerExtendedAuthHandler#onAuth(ctx, clientId, reasonCode, properties)` 只能在收到 AUTH 报文后调用，业务方主动发起 REAUTHENTICATE 没有入口；CONNECT 时透传 Authentication Method / Authentication Data 也没有入口。 |
| 方案 | 1) 在 `IMqttServerExtendedAuthHandler` 添加 `void onConnect(ChannelContext ctx, String clientId, String authMethod, byte[] authData, Consumer<AuthPhase> callback)` 钩子；2) `MqttServer` 暴露 `boolean reauthenticate(String clientId, String method, byte[] data)` 发送 AUTH 报文；3) 默认 `DefaultMqttExtAuthHandler` 实现 SCRAM-SHA-256 demo。 |
| 风险 | 高。状态机复杂度高；强烈建议先实现"空流程"（onAuth → 回 CONTINUE → 再次 onAuth → 回 SUCCESS）跑通，再叠加真实算法。 |
| 验收 | `MqttReauthenticateStateMachineTest`；MqttX 实测 REAUTHENTICATE 全链路；额外 README。 |
| 依赖 | PR8 已完成；该 issue 依赖 §5.3.1 P2.8.* 实现后的 codebase。 |

### 12.6 MQTT5-B006：集群节点间 Session Expiry / Subscriptions 持久化同步（V3）

| 项 | 内容 |
|---|---|
| 场景 | 当前集群 takeover 协议已实现（[SessionTakeoverRequestMessage](../../mica-mqtt-broker/src/main/java/org/dromara/mica/mqtt/broker/cluster/message/SessionTakeoverRequestMessage.java) 等），但会话与订阅列表**重启即失**。 |
| 方案 | 1) `IMqttSessionStore` 抽象（已有 H2 实现，但未在 `MqttClusterConfig` 默认启用）；2) `MqttBroker` 启用 H2/Redis 持久化 + 启动时恢复；3) 跨节点 Session Expiry 同步集群广播；4) E2E 测试重启 2 节点验证 takeover 之后能正常接管。 |
| 风险 | 极高。storage 选择（H2 / RocksDB / 自研）影响性能，详见 [mqtt-server-cluster-storage.md](../../docs/todo/mqtt-server-cluster-storage.md) §4.1.1。 |
| 验收 | 测试矩阵：单节点宕机、集群宕机、H2 文件损坏三种场景；`MqttClusterSessionPersistenceTest`。 |
| 依赖 | [mqtt-server-cluster-storage.md](../../docs/todo/mqtt-server-cluster-storage.md) 全部 5 章节。 |

---

## 13. 实际能力视图（v2.5 重新对齐）

> **本节用途**：把 §4.1 矩阵与 §0 "读者速查" 整合到一起，给出一份" mica-mqtt v2.5 在 MQTT 5.0 上的真实能力视图"——既包括文档最初规划的 P1~P3，也包括先前漏报的实现。这是给客户/合作伙伴做"能力验收"的标准视图。

### 13.1 模块维度（解决 §4.1 在 HTTP API 列的低估问题）

| 模块 | v2.5 真实能力 |
|---|---|
| **codec** | ✅ 全部 Property（28类）、Reason Code（10类）、报文编解码 | 
| **服务端运行层** | ✅ 主要 5.0 报文处理；🚧 QoS2 Reason Code 业务方构造（仅服务端路径完整） |
| **客户端运行层** | ✅ Topic Alias / Subscription Identifier / Receive Maximum 自动维护；🚧 下行 `properties` 暴露 API（F13） |
| **集群层** | ✅ Properties 跨节点透传、takeover 完整、共享订阅 SPI；🚧 V3 持久化 |
| **内置 HTTP API** | ✅ 12 端点（connect/disconnect/publish/batch/subscribe/unsubscribe/clients/stats/endpoints/sse）；❌ 5.0 properties JSON 透传（F15） |
| **MCP 协议入口** | ✅ mqtt_stats / mqtt_publish / mqtt_endpoints 工具暴露 |
| **Spring Boot starter 函数监听器** | ✅ `MqttServerFunctionDetector` 业务方法签名直接拿到 `MqttPublishMessage` |

### 13.2 协议维度（解决 §4.1 矩阵 cluster 列"普遍低估"问题）

| spec 章节 | mica-mqtt 实现状态 | 备注 |
|---|---|---|
| §3.1.2 CONNECT | ✅ | 13 个 CONNACK Properties 已可下发 |
| §3.1.3 Will Properties | ✅ | 含 Will Delay Interval 调度 |
| §3.1.4 Keep Alive + Server Keep Alive | ✅ | |
| §3.2.2 CONNACK | ✅ | 含原因码、Properties、能力位协商 |
| §3.3 PUBLISH / SUBSCRIBE / UNSUBSCRIBE Properties | ✅ 编解码 | ✅ 标准 16 类业务属性透传 |
| §3.4 PUBACK / PUBREC / PUBREL / PUBCOMP | ✅ 编解码 | 🚧 codec 侧 QoS2 ACK Builder 缺少 3 个（F=12.1） |
| §3.5 DISCONNECT | ✅ | 含原因码 + Properties |
| §3.6 AUTH / §4.12 扩展认证 | ✅ 报文路径 + SPI | 🚧 REAUTHENTICATE 工具方法（M=12.5） |
| §3.7 PINGREQ / PINGRESP | ✅ | |
| §3.8 SUBSCRIBE / §3.9 UNSUBSCRIBE | ✅ | 含 No Local / RAP / Retain Handling |
| §3.10.1 Topic Alias | ✅ | 含 max=0 时拒绝（spec 3.3.2.3.5） |
| §3.10.2 Subscription Identifier | ✅ | 通配/共享订阅路径暂不带 |
| §3.11 Flow Control（Receive Maximum） | ✅ server → client | ✅ MQTT5-B002 之后才能下行暴露（F13） |
| §3.12 Assigned Client Identifier | ✅ | |
| §3.13 Request Problem Information | ✅ | |
| §3.14 Request Response Information | ✅ PR3 | |
| §3.15.1 Response Information | ✅ | |
| §3.16.1 Server Reference | ❌ | |
| §3.16.2 Server Keep Alive | ✅ | |
| §4.4 CONNACK 原因码 + Properties | ✅ | 含失败分支的简化 |
| §5.4.6 Shared Subscription（负载分发） | ✅ broker | 含 SPI 5 个内置策略 |

### 13.3 与其他 broker 的能力对照

> 本节仅作读者参考，mica-mqtt 与 EMQX / HiveMQ / VerneMQ / Mosquitto 的具体差异详见各项目文档。

| 维度 | mica-mqtt v2.5 | EMQX 5.x | HiveMQ 4.x |
|---|---|---|---|
| MQTT 5.0 协议 | ✅（剩余 6 项 issue 规划中） | ✅ | ✅ |
| 集群协议 | ✅ 自研 t-io cluster | ✅ Mria / Ekka | ✅ 商业 |
| Shared Subscription 分发策略 | ✅ 自定义 SPI | ✅ 内置 | ✅ 内置 |
| MCP / AI 集成 | ✅ v2.5 内置 MCP 入口 | 🟡 路线图 | ❌ |
| HTTP API | ✅ 12 端点 | ✅ REST + Dashboard | ✅ Dashboard |
| 持久化 V3（集群重启保 session） | 🚧 路线图 | ✅ Mria | ✅ 商业 |

### 13.4 实用读者建议

1. **业务方接入 5.0 properties**：优先用 [MqttServerFunctionDetector](../../starter/mica-mqtt-server-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/server/MqttServerFunctionDetector.java) 写 listener，方法签名直接拿 `MqttPublishMessage.properties`，**无需等 F15**（F15 只解决 HTTP publish 入口透传）。
2. **业务方实现自定义共享订阅分发**：继承 `SharedSubscriptionStrategy` 即可，已是 public SPI。
3. **客户端想拿服务端下行的 Subscription Identifier**：现在没有 API。需要等到 F13 (B002) 完成；临时方案——业务方读 `MqttPublishMessage.properties`，反序列化后自己做匹配。
4. **HTTP 调用方需要 5.0 properties**：当前 /api/v1/mqtt/publish 不支持；临时方案——业务方通过 client SDK `MqttClient.publish(topic, payload, qos, retain, properties)` 走 MQTT 通道。

---

**变更摘要（v2.5）**：

- §0 新增读者速查（14 类隐藏能力）
- §1.1 现状速览：HTTP API 行从"❌ 薄弱"修正为"✅ 完整（含 MCP）+ ❌ properties JSON 透传待 PR"
- §4.1 状态矩阵：Shared Subscription broker 列 ✅ / 集群拆 3 行（properties ✅ / session 🚧 / 共享订阅 SPI ✅）
- §4.1 末追加本轮修订注："HTTP API 列与 PR11"修订说明
- §5.3 末新增 §5.3.1：P2 已完成任务之外的 4 个延伸子项（P2.8.1/2/3, P2.9.1）
- §5.4 P3.5 改 ✅ 并附业务方自定义 SPI 示例
- §6.4 重写区分"已实现"与"仍待办"
- §6.5 拆三段（HTTP API 端点表 + MCP + starter 函数监听器）+ 重点更正"PR2.5/PR2.6 已透传"措辞
- §11 后新增 §12：6 个 next minor/major issue 模板（B001~B006）
- §13：v2.5 实际能力视图 + 与其他 broker 对照
- §10 版本号同步：v2.3 → v2.5（横跨本轮两轮修订）

**相关源码导航**：

- 编解码：[mica-mqtt-codec](../../mica-mqtt-codec)
- 服务端：[mica-mqtt-server](../../mica-mqtt-server)
- 客户端：[mica-mqtt-client](../../mica-mqtt-client)
- 集群：[mica-mqtt-broker](../../mica-mqtt-broker)
- 启动器：[starter](../../starter)

**Handler 一览**（`mica-mqtt-server/.../core/server/handler/`）：

- [MqttConnectHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java)
- [MqttPublishHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPublishHandler.java)
- [MqttPubAckHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubAckHandler.java)
- [MqttPubRecHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRecHandler.java)
- [MqttPubRelHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRelHandler.java)
- [MqttPubCompHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubCompHandler.java)
- [MqttSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java)
- [MqttUnSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttUnSubscribeHandler.java)
- [MqttPingReqHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPingReqHandler.java)
- [MqttDisConnectHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttDisConnectHandler.java)
- [IMqttMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/IMqttMessageHandler.java)
- [AbstractMqttMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/AbstractMqttMessageHandler.java)

**Pipeline 一览**（`mica-mqtt-server/.../core/server/pipeline/`）：

顶层接口与默认实现：

- [IMqttMessagePipeline](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/IMqttMessagePipeline.java)
- [IMqttPublishPipeline](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/IMqttPublishPipeline.java)
- [MqttMessagePipelineHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/MqttMessagePipelineHandler.java)
- [MqttPublishPipelineHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/MqttPublishPipelineHandler.java)
- [DefaultMqttMessagePipeline](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/DefaultMqttMessagePipeline.java)
- [DefaultMqttPublishPipeline](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/DefaultMqttPublishPipeline.java)
- [PublishContext](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/PublishContext.java)

发布流 handler（`pipeline/handler/`，实现 `MqttPublishPipelineHandler`）：

- [SubscriptionForwardHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java)
- [RetainMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/RetainMessageHandler.java)
- [MessageListenerHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/MessageListenerHandler.java)

内部消息 handler（`pipeline/message/`，基类 `BaseMessageHandler`，实现 `MqttMessagePipelineHandler`）：

- [BaseMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/BaseMessageHandler.java)
- [ConnectMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/ConnectMessageHandler.java)
- [DisconnectMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/DisconnectMessageHandler.java)
- [SubscribeMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/SubscribeMessageHandler.java)
- [UnsubscribeMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/UnsubscribeMessageHandler.java)
- [UpStreamMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/UpStreamMessageHandler.java)
- [DownStreamMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/DownStreamMessageHandler.java)
- [HttpApiMessageHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/message/HttpApiMessageHandler.java)
