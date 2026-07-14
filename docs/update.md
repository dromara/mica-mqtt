# 升级指南

**mica-mqtt** 尽量减少对 api 的改动已保证老版本的平滑升级，但是有些大版本不得不改动。希望此文档对大家有所帮助。

## 迁移到 mica-mqtt 2.6.2

注意：2.6.2 升级 mica-net 到 2.0.1，将所有 `org.tio.*` 包迁移到 `net.dreamlu.mica.net.*`，避免跟原版 `t-io` 包冲突。

**作用范围：** mica-net 内部包名调整会传导到 mica-mqtt 全部依赖该网络框架的模块。

**变更示例：**
- `org.tio.core.Tio` → `net.dreamlu.mica.net.core.Tio`
- `org.tio.server.ServerTioConfig` / `org.tio.server.TioServerConfig` → `net.dreamlu.mica.net.server.TioServerConfig`
- `org.tio.client.ClientTioConfig` / `org.tio.client.ClientChannelContext` → `net.dreamlu.mica.net.client.*`
- `org.tio.utils.buffer.ByteBufferUtil` → `net.dreamlu.mica.net.utils.buffer.ByteBufferUtil`
- `org.tio.utils.mica.HexUtils` / `PayloadEncode` → `net.dreamlu.mica.net.utils.mica.*`

如果你项目中直接引用过上述 `org.tio.*` 包，**推荐做法是直接删除代码里的旧 `import` 语句**，然后将鼠标停在报红的类名上，使用 IDEA 的 `Alt + Enter`（或快捷键）触发 “Add import for ...”，由 IDE 自动选择并导入 `net.dreamlu.mica.net.*` 下对应的类，比手动全局替换更安全（避免误改字符串、注释等）。

## 迁移到 mica-mqtt 2.5.x

### 2.5.4 不兼容变更

1. **注解包路径统一**
   - `@MqttServerFunction`、`@MqttClientSubscribe` 统一迁移到 `mica-mqtt-common` 包。
   - `MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN` 常量定义移到 `@MqttClientSubscribe`。

   **推荐做法：** 直接把代码里旧的 `import` 语句删除，将鼠标停到 `@MqttServerFunction` / `@MqttClientSubscribe` 上，使用 IDEA 的 `Alt + Enter` 触发 “Add import for ...”，由 IDE 自动选择并导入 `org.dromara.mica.mqtt.common.annos.*` 下新的注解类，比手动改 import 更安全。

   ```java
   // 升级后由 IDE 自动导入的目标路径
   import org.dromara.mica.mqtt.common.annos.MqttServerFunction;
   import org.dromara.mica.mqtt.common.annos.MqttClientSubscribe;
   ```

2. **codec 模块调整**
   - `MqttCodecUtil.isValidPublishTopicName()` 被移除，统一改用 `isTopicFilter()` 校验发布主题是否包含通配符。
   - codec 包结构调整：类名、方法名、MQTT 消息构建器类有重命名重构，升级后如编译报错请按 IDE 提示重新选择新类/方法（鼠标停到报红的方法上 `Alt + Enter` 让 IDEA 自动 import 新类）。

3. **`TopicUtil.validateTopicFilter`** 移除了对空白字符的校验（emqx 支持，mosquitto 不支持）。

### 2.5.0 不兼容变更

1. **移除 `mica-mqtt-broker` 模块**（未来重构），原 broker 用户暂留 2.5.0 之后该依赖 404。
2. **`IMqttClientMessageIdGenerator` 接口合并**到 `IMqttClientSession` 接口。
3. **客户端默认版本改为 MQTT 5.0**，`cleanSession` 改名为 `cleanStart`，相关配置字段一并调整。
4. **参数命名统一**：`userName` 统一为 `username`（含配置项、API 参数）。
5. **移除 `EventMqttMessageListener`**（不推荐使用），请改用 `IMqttMessageListener`。

## 迁移到 mica-mqtt 2.4.2

注意：2.4.2 将 MqttServerCustomizer 和 MqttClientCustomizer 抽到 mica-mqtt-server、mica-mqtt-client。Spring Boot 和 Solon 插入如果有使用到，**推荐做法是直接把老引用报红的 `import` 语句删除，鼠标停在类名上，使用 IDEA 的 `Alt + Enter` 触发 “Add import for ...”**，由 IDE 自动选择并导入新包下的类，避免手动指定路径出错。

**客户端替换包导入：**
- 替换成 `import org.dromara.mica.mqtt.core.client.MqttClientCustomizer;`

**服务端替换包导入：**
- 替换成 `import org.dromara.mica.mqtt.core.server.MqttServerCustomizer;`

## 迁移到 mica-mqtt 2.4.x 以上版本

- :truck: 调整 maven groupId `net.dreamlu` 到新的 `org.dromara.mica-mqtt`。
- :truck: 调整包名 `net.dreamlu.iot.mqtt` 到新的 `org.dromara.mica.mqtt`，其他均保持不变。
- :truck: 切换到 central sonatype，central sonatype 不支持快照版，mica-mqtt 不再发布快照版。

## 迁移到 mica-mqtt 2.3.x

### 2.3.0 不兼容变更

`MqttQoS` 枚举值更名（注意是枚举值 `name`，不是 `ordinal`）：

```java
// 老用法
MqttQoS.qos0
MqttQoS.qos1
MqttQoS.qos2

// 新用法
MqttQoS.QOS0
MqttQoS.QOS1
MqttQoS.QOS2
```

如果是序列化（如 JSON、DB）存储过 `MqttQoS`，请同步做一次数据迁移。

## 迁移到 mica-mqtt 2.2.x

### 2.2.0 不兼容变更

`MqttPublishMessage` payload 参数由 `java.nio.ByteBuffer` 改为 `byte[]`，简化使用。

升级后业务回调中如使用 `ByteBuffer` 接收消息，需做一次转换：

```java
// 老用法
ByteBuffer payload = message.getPayload();

// 新用法
byte[] payload = message.getPayload();
```

注意 `IMqttMessageListener#onMessage`、`IMqttClientMessageListener#onMessage` 等接口签名已同步调整。

## 迁移到 mica-mqtt 2.1.x

> ⚠️ 注：`org.tio.*` 包路径为 mica-net 2.0.x 之前的命名，从 mica-mqtt **2.6.2** 起已经迁移到 `net.dreamlu.mica.net.*`，
> 详见 [## 迁移到 mica-mqtt 2.6.2] 章节。

- `mica-mqtt-core` 拆分成了 `mica-mqtt-client` 和 `mica-mqtt-server`，避免一些依赖引用问题。
- `ByteBufferUtil` 由 `org.dromara.mica.mqtt.codec.ByteBufferUtil` 移动到了 `org.tio.utils.buffer.ByteBufferUtil`。
- `HexUtil` 由 `org.dromara.mica.mqtt.core.util.HexUtil` 移动到了 `org.tio.utils.mica.HexUtils`。

### 1. 客户端

#### 1.1 订阅回调接口调整
注意：`mica-mqtt-client-spring-boot-starter` 使用注解订阅可以直升。

`IMqttClientMessageListener#onMessage(ChannelContext context, String topic, MqttPublishMessage message, ByteBuffer payload)` 方法统一添加 `context`、`message` 参数。

订阅系列方法需要调整：
```java
// 消息订阅，同类方法 subxxx
client.subQos0("/test/#", (context, topic, message, payload) -> {
    logger.info(topic + '\t' + ByteBufferUtil.toString(payload));
});
```

#### 1.2 SSL 双向认证支持
```yaml
mica:
  client:
    ssl:
      enabled: false            # 是否开启 ssl 认证，2.1.0 开始支持双向认证
      keystore-path:            # 可选参数：ssl 双向认证 keystore 目录，支持 classpath:/ 路径。
      keystore-pass:            # 可选参数：ssl 双向认证 keystore 密码
      truststore-path:          # 可选参数：ssl 双向认证 truststore 目录，支持 classpath:/ 路径。
      truststore-pass:          # 可选参数：ssl 双向认证 truststore 密码
```

注意： ssl 存在三种情况

| 服务端开启ssl                            | 客户端                                        |
| ---------------------------------------- | --------------------------------------------- |
| ClientAuth 为 NONE（不需要客户端验证）   | 仅仅需要开启 ssl 即可不用配置证书             |
| ClientAuth 为 OPTIONAL（与客户端协商）   | 需开启 ssl 并且配置 truststore 证书           |
| ClientAuth 为 REQUIRE (必须的客户端验证) | 需开启 ssl 并且配置 truststore、 keystore证书 |

### 2. 服务端

#### 2.1 IMqttMessageListener 调整

`IMqttMessageListener` onMessage 参数也做了调整，添加了 topic、qoS，message 改为了原始 MqttPublishMessage，方便自行获取 mqtt5.x 的属性。
```java
/**
 * 监听到消息
 *
 * @param context  ChannelContext
 * @param clientId clientId
 * @param topic    topic
 * @param qoS      MqttQoS
 * @param message  Message
 */
void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qoS, MqttPublishMessage message);
```

#### 2.2 ssl 双向认证支持
```yaml
mica:
  server:
    ssl:                        # mqtt tcp ssl 认证
      enabled: false            # 是否开启 ssl 认证，2.1.0 开始支持双向认证
      keystore-path:            # 必须参数：ssl keystore 目录，支持 classpath:/ 路径。
      keystore-pass:            # 必选参数：ssl keystore 密码
      truststore-path:          # 可选参数：ssl 双向认证 truststore 目录，支持 classpath:/ 路径。
      truststore-pass:          # 可选参数：ssl 双向认证 truststore 密码
      client-auth: NONE         # 是否需要客户端认证（双向认证），默认：NONE（不需要）
```

## 迁移到 mica-mqtt 2.0.x

### 2.0.0 重要变更

切换到自维护的 java8 t-io，注意 t-io 部分类名有变更，涉及 socket、Aio 系列 API 的直接调用者：

```text
org.tio.core.Aio -> org.tio.core.Tio  (部分 API 迁移)
org.tio.server.AioServer -> org.tio.server.TioServer
```

由于 mica-mqtt 已封装底层网络 API，**普通用户**通常无需关注，本次升级主要是为了支持 Java 8 环境。
