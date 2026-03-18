# mica-mqtt-broker 集群消息二进制编解码设计

## 1. 设计目标

- **高性能**：使用 ByteBuffer 手写二进制编解码，比 Hessian 快 2-3 倍
- **低带宽**：紧凑的二进制格式，减少网络开销
- **复用现有代码**：直接使用 `Message` 模型，复用 MQTT 消息编解码

---

## 2. 消息格式设计

### 2.1 整体结构

```
+--------+--------+--------+------------------+
| Magic  | Type   | Length (varint) | Body             |
| 0xMC   | 1 byte | 1-4 bytes        | N bytes          |
+--------+--------+--------+------------------+
```

| 字段 | 长度 | 说明 |
|------|------|------|
| Magic | 1 byte | 固定值 `0xMC`（Mica Cluster），用于标识协议 |
| Type | 1 byte | 消息类型枚举值 |
| Length | 1-4 bytes | 消息体长度，使用 MQTT 风格的变长整数 |
| Body | N bytes | 消息体，二进制数据 |

### 2.2 变长整数编码 (Variable Length Integer)

采用 MQTT 协议标准：

```
每个字节的低 7 位存储数值，最高 1 位表示是否有后续字节
```

### 2.3 字符串编码

```
+--------+------------------+
| Length (uint16) | UTF-8 Bytes |
| 2 bytes        | N bytes     |
+--------+------------------+
```

---

## 3. 消息类型与字段定义

### 3.1 消息类型枚举 (MessageType)

| 枚举名 | 值 | 说明 |
|--------|-----|------|
| CLIENT_CONNECT | 1 | 客户端连接通知 |
| CLIENT_DISCONNECT | 2 | 客户端断开通知 |
| SUBSCRIBE_NOTIFY | 3 | 订阅通知 |
| UNSUBSCRIBE_NOTIFY | 4 | 取消订阅通知 |
| PUBLISH_FORWARD | 5 | 消息转发 |
| NODE_LEAVE | 6 | 节点离开 |
| STATE_SYNC_REQUEST | 7 | 状态同步请求 |
| STATE_SYNC_RESPONSE | 8 | 状态同步响应 |

### 3.2 各消息类型字段

| 消息类型 | 字段 | 说明 |
|----------|------|------|
| CLIENT_CONNECT | clientId | 客户端 ID |
| CLIENT_DISCONNECT | clientId | 客户端 ID |
| SUBSCRIBE_NOTIFY | clientId, nodeId, subscriptions | 订阅列表 |
| UNSUBSCRIBE_NOTIFY | clientId, nodeId, topics | 取消主题列表 |
| PUBLISH_FORWARD | message | 直接使用 Message 模型 |
| NODE_LEAVE | (无) | 无额外字段 |
| STATE_SYNC_REQUEST | (无) | 无额外字段 |
| STATE_SYNC_RESPONSE | clientNodeMap, subscriptionMap | 全量状态数据 |

---

## 4. PublishForwardMessage 设计

### 4.1 直接使用 Message 模型

```java
public class PublishForwardMessage extends ClusterMessage {
    private Message message;
}
```

`Message` 类已包含所有必要字段：
- `topic`, `payload`, `qos`, `retain`, `dup`
- `properties` - MQTT5 属性
- `propertiesBytes` - 属性序列化后的字节数组

### 4.2 编解码策略

直接对 `Message` 对象进行二进制序列化/反序列化，复用现有的 MQTT 编解码逻辑。

---

## 5. 接口设计

### 5.1 编解码接口

```java
public interface ClusterMessageCodec {
    byte[] encode(ClusterMessage msg);
    ClusterMessage decode(byte[] data);
}
```

### 5.2 实现类

```
cluster/codec/
├── ClusterMessageCodec.java        # 接口定义
└── BinaryClusterMessageCodec.java  # 二进制编解码实现
```

---

## 6. 实现要点

### 6.1 编码流程

```java
public byte[] encode(ClusterMessage msg) {
    // 1. 写入 Magic (0xMC)
    // 2. 写入 Type (1 byte)
    // 3. 编码 Body 到 ByteBuffer
    // 4. 写入 Length
    // 5. 返回字节数组
}
```

### 6.2 解码流程

```java
public ClusterMessage decode(byte[] data) {
    // 1. 验证 Magic
    // 2. 读取 Type
    // 3. 读取 Length
    // 4. 根据 Type 解码 Body
}
```

---

## 7. MqttClusterManager 集成

```java
public class MqttClusterManager {
    private final ClusterMessageCodec codec = new BinaryClusterMessageCodec();

    private byte[] serialize(ClusterMessage msg) {
        return codec.encode(msg);
    }

    private ClusterMessage deserialize(byte[] data) {
        return codec.decode(data);
    }
}
```

---

## 8. 待完成事项

- [x] 创建 `ClusterMessageCodec` 接口
- [x] 创建 `BinaryClusterMessageCodec` 实现类（完全手写二进制编码，无第三方依赖）
- [x] 实现各消息类型的编码/解码方法
- [x] 集成到 MqttClusterManager
- [x] 单元测试验证编解码正确性
- [x] 删除 hessian-lite 依赖
