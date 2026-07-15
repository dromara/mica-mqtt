# mica-mqtt MQTT 5.0 新特性梳理与待实现清单

> **本文档定位**：对照 MQTT 5.0 协议规范（[MQTT v5.0 specification](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)），梳理 mica-mqtt 各模块对 5.0 特性的当前实现情况，列出待实现特性的清单、工作量、依赖、风险与验收标准，作为 MQTT 5.0 兼容性的迭代排期依据。
>
> **配套文档**：
> - `mqtt-server-cluster.md` (v3.0) — 集群基础
> - `mqtt-server-cluster-routing.md` (v1.2) — V2 路由层
> - `mqtt-server-cluster-storage.md` (v1.2) — V3 持久化层
> - `mqtt-server-cluster-tasks.md` (v1.1) — 集群能力任务清单
> - **本文档** (v2.0) — MQTT 5.0 特性梳理与待办

---

## 1. 背景与现状

mica-mqtt 默认连接协议为 `MQTT_5`（参见 [MqttClientProperties](../../starter/mica-mqtt-client-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/client/config/MqttClientProperties.java#L131)），但 5.0 的**运行时语义**仅实现了一部分。下面按"协议层"和"运行时层"分别梳理。

### 1.1 现状速览

| 层级 | 状态 | 说明 |
|---|---|---|
| 编解码层（`mica-mqtt-codec`） | ✅ 基本完整 | 全部 27 类 Property、10 类 Reason Code、AUTH/DISCONNECT 报文均已能编解码 |
| 服务端运行时（`mica-mqtt-server`） | 🚧 部分实现 | No Local、Message Expiry、Shared Subscription 已部分生效；其余仅在报文中透传，业务不处理 |
| 客户端运行时（`mica-mqtt-client`） | 🚧 部分实现 | subscribe / publish 支持 properties 参数，但 Topic Alias、Receive Maximum 等流控机制未生效 |
| 集群层（`mica-mqtt-broker`） | 🚧 透传为主 | 集群消息协议 V1 已实现 10 类，Session / Subscription Options 序列化待扩展 |
| HTTP API（`mica-mqtt-server`） | ❌ 薄弱 | 5.0 Properties 透传能力弱 |

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
| `MqttConnectHandler` | `{CONNECT}` | [L87](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L87) | [handler/MqttConnectHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java) |
| `MqttPublishHandler` | `{PUBLISH}` | [L72](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPublishHandler.java#L72) | [handler/MqttPublishHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPublishHandler.java) |
| `MqttPubAckHandler` | `{PUBACK}` | [L55](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubAckHandler.java#L55) | [handler/MqttPubAckHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubAckHandler.java) |
| `MqttPubRecHandler` | `{PUBREC}` | [L58](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRecHandler.java#L58) | [handler/MqttPubRecHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRecHandler.java) |
| `MqttPubRelHandler` | `{PUBREL}` | [L63](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRelHandler.java#L63) | [handler/MqttPubRelHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubRelHandler.java) |
| `MqttPubCompHandler` | `{PUBCOMP}` | [L55](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubCompHandler.java#L55) | [handler/MqttPubCompHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPubCompHandler.java) |
| `MqttSubscribeHandler` | `{SUBSCRIBE}` | [L73](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java#L73) | [handler/MqttSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java) |
| `MqttUnSubscribeHandler` | `{UNSUBSCRIBE}` | [L60](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttUnSubscribeHandler.java#L60) | [handler/MqttUnSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttUnSubscribeHandler.java) |
| `MqttPingReqHandler` | `{PINGREQ}` | [L50](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPingReqHandler.java#L50) | [handler/MqttPingReqHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttPingReqHandler.java) |
| `MqttDisConnectHandler` | `{DISCONNECT}` | [L50](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttDisConnectHandler.java#L50) | [handler/MqttDisConnectHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttDisConnectHandler.java) |
| ❌ `MqttAuthHandler` | `{AUTH}` | — | **未实现**（见 §6.3） |

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
| **No Local**（订阅选项） | [MqttSubscribeHandler#handle](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java#L85-L92) + [SubscriptionForwardHandler#forwardToSubscribers](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L141-L166) | 订阅时记录 noLocal，发布时过滤（规范 3.8.3.1） |
| **Message Expiry Interval**（过期 + 递减） | [SubscriptionForwardHandler#rewriteProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L92-L132) | 检查过期 + 剩余时间递减（规范 3.3.2.3） |
| **Shared Subscription** `$share/{group}/...` | [TrieTopicManager](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/session/TrieTopicManager.java) | 分组共享订阅（`$queue` + `$share`） |
| **Session Expiry Interval**（客户端侧） | [MqttClientProperties](../../starter/mica-mqtt-client-spring-boot-starter/src/main/java/org/dromara/mica/mqtt/spring/client/config/MqttClientProperties.java#L145) | CONNECT 时下发，服务端有 `IMqttSessionManager.expire` 接口签名但未启用（见 §6.4） |
| **Topic Alias / Subscription Identifier 转发清理** | [SubscriptionForwardHandler#rewriteProperties](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java#L116-L122) | 服务端转发时不携带发布者的 Topic Alias 和 Subscription Identifier（规范 3.3.2.3 / 3.3.4） |
| **Subscription Options 编解码** | [MqttEncoder](../../mica-mqtt-codec/src/main/java/org/dromara/mica/mqtt/codec/MqttEncoder.java#L226-L240) | RetainAsPublished / RetainHandling / No Local / QoS 均能编解码，服务端运行时语义已生效 |
| **Retain As Published / Retain Handling**（服务端运行时） | [MqttSubscribeHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttSubscribeHandler.java) + [SubscriptionForwardHandler](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/pipeline/handler/SubscriptionForwardHandler.java) | 保存完整订阅选项；支持三种保留消息补发策略，实时转发按 RAP 保留 RETAIN 标志；重叠订阅会合并 No Local / RAP 语义 |
| **Will Properties**（属性透传） | [MqttConnectHandler#handle 第 140-159 行](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L140-L159) | 遗嘱消息持久化时已绑定 WillProperties 字段（willDelay、payloadFormatIndicator 等），Will Delay Interval 调度尚未生效 |

---

## 4. 待实现特性清单（❌ / 🚧）

### 4.1 状态矩阵

| 特性 | codec | server | client | broker | HTTP API |
|---|---|---|---|---|---|
| PUBACK/PUBREC/PUBREL/PUBCOMP Reason Code | ✅ | ✅ | ❌ | ❌ | n/a |
| DISCONNECT Reason Code + Properties（双向） | ✅ | ✅ | ✅ | 🚧 | n/a |
| SUBACK / UNSUBACK Reason Code | ✅ | ✅ | 🚧 | 🚧 | n/a |
| Retain As Published / Retain Handling（运行时） | ✅ | ✅ | n/a | 🚧（本地委托支持，节点协议待扩展） | n/a |
| CONNACK Properties 完善（能力位告知） | ✅ | ✅ | ❌ | ❌ | n/a |
| Server Keep Alive | ✅ | ✅ | ❌ | ❌ | n/a |
| Receive Maximum（运行时） | ✅ | ❌ | ❌ | ❌ | n/a |
| Will Properties / Will Delay Interval（调度） | ✅ | ✅（调度） | ✅（透传） | ❌ | n/a |
| Subscription Identifier（运行时） | ✅ | ✅ | ❌ | ❌ | n/a |
| Topic Alias（运行时） | ✅ | ✅ | ❌ | ❌ | n/a |
| Assigned Client Identifier | ✅ | ✅ | ❌ | ❌ | n/a |
| Request Problem Information | ✅ | ✅ | ❌ | ❌ | n/a |
| Payload Format Indicator + Content Type | ✅ | ✅（透传） | ✅（透传） | 🚧 | ❌ |
| Response Topic + Correlation Data | ✅ | ✅（透传） | ✅（透传） | 🚧 | ❌ |
| AUTH 报文处理 + 扩展认证 | ✅ | ❌ | ❌ | ❌ | n/a |
| Server Reference（服务端重定向） | ✅ | ❌ | n/a | ❌ | n/a |
| Response Information（请求/响应模式） | ✅ | ✅ | n/a | ❌ | n/a |
| Clean Start + 完整 Session Expiry Interval | ✅ | 🚧（注释中） | 🚧 | 🚧 | n/a |
| Maximum Packet Size 校验 | ✅ | ✅ | ✅ | ❌ | n/a |
| QoS2 完整 Reason Code（PUBACK/PUBREC/PUBREL/PUBCOMP） | ✅ | ❌ | ❌ | ❌ | n/a |
| Shared Subscription Available 协商 | ✅ | ✅ | ❌ | 🚧 | n/a |
| 集群节点间 Session Expiry / Subscriptions 同步 | n/a | n/a | n/a | 🚧 | n/a |

> 图例：✅ 已实现  🚧 部分实现（仅透传或仅框架）  ❌ 未实现  n/a 不适用

> **2026-07-07 进度**：已完成服务端 ACK Reason Code 基础链路、SUBACK/UNSUBACK Reason Code、DISCONNECT 双向 Reason Code + Properties、Server Keep Alive 服务端下发、Assigned Client Identifier、Request Problem Information。CONNACK 能力位已具备基础下发能力，但 Receive Maximum / Maximum Packet Size 等运行时语义未完成，仍按部分实现跟踪。
>
> **2026-07-14 进度**：本轮已完成 P3.3（Maximum Packet Size 校验）与 P2.5/P2.6（Payload Format Indicator / Content Type / Response Topic / Correlation Data 透传）。服务端与客户端两个端点的解码/编码已可完整保留上述四个 PUBLISH 属性；HTTP API 透传通道 (`HttpApiMessageHandler` / `MqttServer.sendToClient`) 在构建 `MqttPublishMessage` 时正确携带属性；保留消息 (`saveRetainMessage`) 也已支持持久化属性。`MqttPublishPropertiesRoundTripTest`（9 个用例）作为 codec 端回归保护。
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

### 5.4 P3 第三梯队（2-3 周+）

| 任务 | 标题 | 工作量 | 依赖 | 风险 |
|---|---|---|---|---|
| **P3.1** | Clean Start + 完整 Session Expiry Interval | 5 天 | P2.x | 高 |
| **P3.2** | Receive Maximum（双向完整） | 3 天 | P1.7 + P3.1 | 中 |
| **P3.3** | ✅ Maximum Packet Size 校验 | 已完成 | 无 | 低 |
| **P3.4** | QoS2 完整 Reason Code（PUBACK/PUBREC/PUBREL/PUBCOMP） | 2 天 | P1.1 | 中 |
| **P3.5** | Shared Subscription 负载均衡策略抽象 | 3 天 | 无 | 中 |
| **P3.6** | TLS 1.3 + x509 属性提取 | 5 天 | 无 | 中 |
| **P3.7** | 集群节点间 Session Expiry / Subscriptions 同步 | 1 周+ | P3.1 | 高 |

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

| 业务场景 | Reason Code | 值 |
|---|---|---|
| 发布权限拒绝 | `QUOTA_EXCEEDED` | 0x97 |
| 主题无效 | `TOPIC_NAME_INVALID` | 0x90 |
| 报文过大 | `PACKET_TOO_LARGE` | 0x95 |
| 报文速率超限 | `PACKET_RATE_EXCEEDED` | 0x96 |
| 客户端鉴权失败（订阅） | `NOT_AUTHORIZED` | 0x87 |
| 订阅者接管 | `SESSION_TAKEN_OVER` | 0x8E |

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

**现状**：[MqttConnectHandler#connAckByReturnCode 第 172-183 行](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L172-L183) 仅设置 `returnCode` + `sessionPresent`，未下发任何 5.0 能力位。

**改造点**：构建 `MqttConnAckProperties`：

```java
// MqttConnectHandler.java 新增私有方法
private MqttConnAckProperties buildConnAckProperties(String clientId, String uniqueId) {
    MqttServerConfig serverConfig = serverCreator.getServerConfig();
    return new MqttConnAckProperties()
        .setReceiveMaximum(serverConfig.getReceiveMaximum())     // P1.7
        .setMaximumQos(MqttQoS.QOS2)                             // 服务端支持的最大 QoS
        .setRetainAvailable(true)                                // 服务端支持保留消息
        .setMaximumPacketSize(serverConfig.getMaxPacketSize())   // P3.3
        .setAssignedClientIdentifier(StrUtil.isBlank(clientId) ? uniqueId : null) // P2.3
        .setTopicAliasMaximum(serverConfig.getTopicAliasMax())   // P2.2
        .setServerKeepAlive(serverConfig.getServerKeepAlive())   // P1.6
        .setResponseInformation(responseInfo)                    // P2.9
        .setWildcardSubscriptionAvailable(true)
        .setSharedSubscriptionAvailable(true)                    // P3.5
        .setSubscriptionIdentifierAvailable(true);               // P2.1
}

// connAckByReturnCode 改造
private void connAckByReturnCode(String clientId, String uniqueId,
                                  ChannelContext context, MqttConnectReasonCode returnCode) {
    MqttConnAckMessage message = MqttConnAckMessage.builder()
        .returnCode(returnCode)
        .sessionPresent(false)
        .properties(buildConnAckProperties(clientId, uniqueId).getProperties())   // 新增
        .build();
    Tio.send(context, message);
    // ... 日志逻辑
}
```

**Server Keep Alive**：[MqttConnectHandler#handle 第 123-129 行](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L123-L129) 已读取客户端 keepAlive 但未下发 Server Keep Alive 覆盖值。需：
1. `MqttConnectVariableHeader.properties()` 读取 `SERVER_KEEP_ALIVE`（若存在，用它覆盖 `context.setHeartbeatTimeout`）
2. CONNACK 中带 `serverKeepAliveSeconds` 让客户端知情
3. 当前 `KEEP_ALIVE_UNIT = 2000L` 是个固定 ms 单位（不是 2 倍系数），Server Keep Alive 路径下需明确 `serverKeepAlive * 1000L` 还是 `serverKeepAlive * 2000L`，建议统一为 `* 1000L`，并加 javadoc 说明

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

### 6.4 Clean Start + Session Expiry（P3.1）

**现状**：
- [IMqttSessionManager#expire](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/session/IMqttSessionManager.java#L173) 接口签名已有，但未在 CONNACK 配合
- [MqttConnectHandler#handle 第 130-139 行](../../mica-mqtt-server/src/main/java/org/dromara/mica/mqtt/core/server/handler/MqttConnectHandler.java#L130-L139) 仍有 `cleanSession` 的注释占位（未启用）

**改造点**：
1. `MqttConnectHandler` 读取 `cleanStart` + `sessionExpiryInterval`，写 `IMqttSessionManager.expire`
2. CONNACK 返回 Session Present（基于 cleanStart + expiry）
3. `TimerTaskService` 增加过期扫描，触发 `expire(clientId, interval)`
4. 重连时若 expiry 未到，从 H2 / Redis 恢复 session + pending inflight
5. QoS1/QoS2 未确认消息按原 session 状态重投

**依赖**：与 `mqtt-server-cluster-storage.md` 的 V3 持久化强耦合。

### 6.5 HTTP API 升级

**现状**：[mica-mqtt-server-spring-boot-starter](../../starter/mica-mqtt-server-spring-boot-starter) 与 [mica-mqtt-server-solon-plugin](../../starter/mica-mqtt-server-solon-plugin) 的 HTTP API 对 5.0 properties 透传能力弱。

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
| **mqtt5-features.md** | v2.3 | P1/P2 部分能力已落地 | 2026-07-13 |

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
