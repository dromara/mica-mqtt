# mica-mqtt-server 集群化实施方案

## 1. 方案概述

### 1.1 目标
将现有的 mica-net 集群能力引入到 mica-mqtt-server 中，实现多个 MQTT Broker 实例之间的：
- 节点发现与互联
- 客户端会话信息同步
- 跨节点消息路由与转发
- 订阅信息共享
- 集群状态监控

### 1.2 技术方案
基于 **mica-net core cluster** 提供的 TCP 集群能力，实现轻量级、低延迟的 Broker 集群方案。

**优势：**
- 无需额外依赖（Redis、Kafka 等）
- 直接基于 mica-net 框架，集成简单
- TCP 直连，延迟低
- 适合中小规模集群（3-10 节点）

**适用场景：**
- 局域网或同机房部署
- 需要高性能、低延迟的场景
- 无外部消息中间件的环境

---

## 2. 架构设计

### 2.1 集群拓扑
```
                    ┌─────────────┐
                    │ Seed Node 1 │
                    │  (Master)   │
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼──────┐  ┌─────▼──────┐  ┌─────▼──────┐
    │   Node 2   │  │   Node 3   │  │   Node 4   │
    │  (Worker)  │  │  (Worker)  │  │  (Worker)  │
    └────────────┘  └────────────┘  └────────────┘
```

- **种子节点（Seed Members）**：预配置的集群成员列表，用于初始节点发现
- **动态加入**：新节点启动后，连接任意种子节点即可加入集群
- **全连接拓扑**：所有节点两两连接（适合小规模集群）

### 2.2 核心组件

```
mica-mqtt-server/
└── src/main/java/org/dromara/mica/mqtt/core/server/
    ├── cluster/                          # 新增集群包
    │   ├── MqttClusterConfig.java       # 集群配置
    │   ├── MqttClusterManager.java      # 集群管理器（主入口）
    │   ├── MqttClusterMessageHandler.java # 集群消息处理器
    │   ├── message/                      # 集群消息类型
    │   │   ├── ClusterMessage.java      # 集群消息基类
    │   │   ├── ClientConnectMessage.java # 客户端连接通知
    │   │   ├── ClientDisconnectMessage.java
    │   │   ├── SubscribeNotifyMessage.java # 订阅通知
    │   │   ├── PublishForwardMessage.java  # 消息转发请求
    │   │   └── HeartbeatMessage.java    # 集群心跳
    │   └── dispatcher/
    │       └── ClusterMessageDispatcher.java # 集群消息分发器
    └── MqttServerCreator.java           # 修改：增加集群配置方法
```

### 2.3 消息路由流程

#### 场景 1：客户端 A 发布消息到本地订阅者
```
Client A (Node1)  ─publish─→  Node1  ─local deliver─→  Client B (Node1)
```
**处理：** 本地转发，无需集群通信

#### 场景 2：客户端 A 发布消息到远程订阅者
```
Client A (Node1)  ─publish─→  Node1  ─cluster forward (单次)─→  Node2  ─local deliver─→  Client C, D, E (Node2)
```
**处理步骤（避免网络风暴的 O(1) 路由）：**
1. Node1 收到 Client A 的 PUBLISH 消息。
2. Node1 查询全局路由表，发现 Node2 上有客户端订阅了该 topic。
3. Node1 构造**一条**不携带特定目标 ClientId 的 `PublishForwardMessage`。
4. Node1 通过集群连接向 Node2 发送该消息。
5. Node2 收到后，在 **Node2 本地**查询订阅表，并将消息扇出（Fan-out）分发给 Client C, D, E。

---

## 3. 详细设计

### 3.1 集群配置（MqttClusterConfig）

```java
public class MqttClusterConfig {
	// 是否启用集群
	private boolean enabled = false;

	// 集群监听地址和端口（用于集群节点间通信）
	private String clusterHost = "127.0.0.1";
	private int clusterPort = 9000;

	// 种子节点列表（格式：host:port）
	private List<String> seedMembers = new ArrayList<>();

	// 集群名称（相同集群名称的节点才能互联）
	private String clusterName = "mica-mqtt-cluster";

	// 集群心跳间隔（毫秒）
	private long heartbeatInterval = 5000;

	// 节点失联超时（毫秒）
	private long nodeTimeout = 15000;
}
```

### 3.2 集群消息协议

#### 消息格式
```java
public abstract class ClusterMessage implements Serializable {
	private String messageId;      // 消息唯一ID
	private String sourceNode;     // 源节点名称
	private long timestamp;        // 时间戳
	private MessageType type;      // 消息类型

	public enum MessageType {
		CLIENT_CONNECT,        // 客户端连接通知
		CLIENT_DISCONNECT,     // 客户端断开通知
		SUBSCRIBE_NOTIFY,      // 订阅通知
		UNSUBSCRIBE_NOTIFY,    // 取消订阅通知
		PUBLISH_FORWARD,       // 消息转发
		HEARTBEAT,             // 心跳
		NODE_JOIN,             // 节点加入
		NODE_LEAVE,            // 节点离开
		STATE_SYNC_REQUEST,    // 状态同步请求（新节点加入时请求全局路由表）
		STATE_SYNC_RESPONSE    // 状态同步响应
	}
}
```

#### 关键消息类型

**1. PublishForwardMessage（消息转发）**
```java
public class PublishForwardMessage extends ClusterMessage {
	// 移除 targetClientId，依靠目标节点本地再路由以降低网络开销
	private String topic;           // 主题
	private byte[] payload;         // 消息体
	private int qos;                // QoS 级别
	private boolean retain;         // 是否保留
}
```

**2. SubscribeNotifyMessage（订阅通知）**
```java
public class SubscribeNotifyMessage extends ClusterMessage {
	private String clientId;        // 客户端ID
	private String nodeId;          // 节点ID
	private List<TopicFilter> topics; // 订阅主题列表
}
```

### 3.3 Session 管理器改造

现有 `IMqttSessionManager` 需要支持集群：

```java
public interface IMqttSessionManager {
	// 现有方法...

	// === 新增集群相关方法 ===

	/**
	 * 获取客户端所在节点
	 * @param clientId 客户端ID
	 * @return 节点名称，null 表示客户端不在线
	 */
	String getClientNode(String clientId);

	/**
	 * 注册远程客户端（其他节点的客户端）
	 * @param clientId 客户端ID
	 * @param nodeId 节点ID
	 */
	void registerRemoteClient(String clientId, String nodeId);

	/**
	 * 移除远程客户端
	 * @param clientId 客户端ID
	 */
	void removeRemoteClient(String clientId);

	/**
	 * 节点宕机时级联清理该节点所有客户端及订阅
	 * @param nodeId 节点ID
	 */
	void clearNodeClientsAndSubscriptions(String nodeId);

	/**
	 * 同步远程订阅信息
	 * @param clientId 客户端ID
	 * @param nodeId 所在节点
	 * @param subscriptions 订阅列表
	 */
	void syncRemoteSubscriptions(String clientId, String nodeId, List<Subscribe> subscriptions);
}
```

### 3.4 消息分发器（ClusterMessageDispatcher）

实现跨节点消息路由：

```java
public class ClusterMessageDispatcher {
	private final MqttServer mqttServer;
	private final MqttClusterManager clusterManager;
	private final IMqttSessionManager sessionManager;

	/**
	 * 分发消息（支持本地和远程）
	 */
	public void dispatch(String topic, MqttPublishMessage message) {
		// 1. 查找所有订阅该 topic 的客户端
		List<Subscribe> subscribers = sessionManager.searchSubscribe(topic);

		// 2. 按节点分组
		Map<String, List<Subscribe>> nodeGroups = subscribers.stream()
			.collect(Collectors.groupingBy(sub ->
				sessionManager.getClientNode(sub.getClientId())));

		// 3. 本地投递
		String localNodeId = mqttServer.getServerCreator().getNodeName();
		List<Subscribe> localSubs = nodeGroups.get(localNodeId);
		if (localSubs != null) {
			localSubs.forEach(sub -> publishLocal(sub, message));
		}

		// 4. 远程转发（O(1) 网络开销：每个节点只发一次）
		nodeGroups.keySet().forEach(nodeId -> {
			if (nodeId != null && !nodeId.equals(localNodeId)) {
				forwardToNode(nodeId, message);
			}
		});
	}

	private void forwardToNode(String nodeId, MqttPublishMessage msg) {
		PublishForwardMessage clusterMsg = new PublishForwardMessage();
		clusterMsg.setTopic(msg.variableHeader().topicName());
		clusterMsg.setPayload(msg.payload());
		clusterMsg.setQos(msg.fixedHeader().qosLevel().value());

		clusterManager.sendToNode(nodeId, clusterMsg);
	}
}
```

---

## 4. 实施步骤

### Phase 1: 基础框架搭建（预计 2-3 天）

#### 4.1 创建集群相关类

**任务清单：**
- [x] 创建 `cluster` 包结构
- [ ] 实现 `MqttClusterConfig` 配置类
- [ ] 实现 `MqttClusterMessage` 基类及子类
- [ ] 实现 `MqttClusterMessageHandler`（消息处理器）
- [ ] 实现 `MqttClusterManager`（集群管理器）

**关键代码：**
```java
public class MqttClusterManager {
	private ClusterApi cluster;
	private MqttServer mqttServer;
	private MqttClusterConfig config;

	public void start() throws Exception {
		if (!config.isEnabled()) {
			return;
		}

		ClusterConfig clusterConfig = new ClusterConfig(
			config.getClusterHost(),
			config.getClusterPort(),
			this::handleClusterMessage
		);

		// 添加种子节点
		for (String seed : config.getSeedMembers()) {
			String[] parts = seed.split(":");
			clusterConfig.addSeedMember(parts[0], Integer.parseInt(parts[1]));
		}

		cluster = new ClusterImpl(clusterConfig);
		cluster.start();

		// 定时心跳
		cluster.schedule(() -> {
			broadcast(new HeartbeatMessage(getLocalNodeId()));
		}, config.getHeartbeatInterval());
	}

	private void handleClusterMessage(Message message) {
		// 反序列化集群消息并分发处理
	}
}
```

#### 4.2 集成到 MqttServerCreator

**修改点：**
```java
public class MqttServerCreator {
	// 新增字段
	private MqttClusterConfig clusterConfig;
	private MqttClusterManager clusterManager;

	// 新增配置方法
	public MqttServerCreator clusterConfig(MqttClusterConfig config) {
		this.clusterConfig = config;
		return this;
	}

	public MqttServerCreator enableCluster(String host, int port, String... seedMembers) {
		MqttClusterConfig config = new MqttClusterConfig();
		config.setEnabled(true);
		config.setClusterHost(host);
		config.setClusterPort(port);
		config.setSeedMembers(Arrays.asList(seedMembers));
		return clusterConfig(config);
	}

	public MqttServer build() {
		// ... 现有代码 ...

		// 初始化集群管理器
		if (clusterConfig != null && clusterConfig.isEnabled()) {
			this.clusterManager = new MqttClusterManager(mqttServer, clusterConfig);
		}

		return mqttServer;
	}
}
```

### Phase 2: Session 管理器改造（预计 2 天）

#### 4.3 扩展 InMemoryMqttSessionManager

**任务清单：**
- [ ] 添加客户端节点映射表 `ConcurrentHashMap<String, String> clientNodeMap`
- [ ] 实现 `getClientNode()` 方法
- [ ] 实现 `registerRemoteClient()` 方法
- [ ] 实现 `syncRemoteSubscriptions()` 方法
- [ ] 修改 `searchSubscribe()` 方法，支持返回远程客户端订阅

**关键代码：**
```java
public class InMemoryMqttSessionManager implements IMqttSessionManager {
	// 客户端所在节点映射 (clientId -> nodeId)
	private final ConcurrentHashMap<String, String> clientNodeMap = new ConcurrentHashMap<>();

	@Override
	public String getClientNode(String clientId) {
		return clientNodeMap.get(clientId);
	}

	@Override
	public void registerRemoteClient(String clientId, String nodeId) {
		clientNodeMap.put(clientId, nodeId);
	}

	@Override
	public void addSubscribe(String clientId, Subscribe subscribe) {
		// 原有逻辑...

		// 如果集群已启用，广播订阅信息
		notifyClusterSubscribe(clientId, subscribe);
	}
}
```

### Phase 3: 消息路由改造（预计 3 天）

#### 4.4 实现 ClusterMessageDispatcher

**任务清单：**
- [ ] 创建 `ClusterMessageDispatcher` 类
- [ ] 实现 `dispatch()` 方法（本地+远程分发）
- [ ] 集成到 `MqttServerProcessor.processPublish()` 中

#### 4.5 修改消息发布流程

**修改 `MqttServerProcessor` 或其实现类：**
```java
@Override
public void processPublish(ChannelContext context, MqttPublishMessage message) {
	String topic = message.variableHeader().topicName();

	// ... 权限校验、消息拦截等 ...

	// 使用集群消息分发器
	ClusterMessageDispatcher dispatcher = mqttServer.getClusterDispatcher();
	if (dispatcher != null) {
		dispatcher.dispatch(topic, message);
	} else {
		// 原有本地分发逻辑
		localDispatch(topic, message);
	}
}
```

### Phase 4: 集群事件同步（预计 2 天）

#### 4.6 客户端连接/断开通知

**监听客户端事件：**
```java
public class MqttServerAioListener implements TioServerListener {
	@Override
	public void onAfterConnected(ChannelContext context, boolean isConnected, boolean isReconnect) {
		// 现有逻辑...

		// 广播客户端连接事件
		if (clusterManager != null) {
			String clientId = context.getBsId();
			clusterManager.broadcast(new ClientConnectMessage(clientId));
		}
	}

	@Override
	public void onAfterClose(ChannelContext context, Throwable throwable, String remark, boolean isRemove) {
		// 现有逻辑...

		// 广播客户端断开事件
		if (clusterManager != null) {
			String clientId = context.getBsId();
			clusterManager.broadcast(new ClientDisconnectMessage(clientId));
		}
	}
}
```

#### 4.7 订阅/取消订阅通知

**在订阅处理器中添加：**
```java
public void processSubscribe(ChannelContext context, MqttSubscribeMessage message) {
	// 现有订阅逻辑...

	// 通知其他节点
	if (clusterManager != null) {
		SubscribeNotifyMessage clusterMsg = new SubscribeNotifyMessage();
		clusterMsg.setClientId(clientId);
		clusterMsg.setTopics(subscriptions);
		clusterManager.broadcast(clusterMsg);
	}
}
```

### Phase 5: 配置与测试（预计 2 天）

#### 4.8 添加配置支持

**Spring Boot Starter 配置：**
```yaml
mqtt:
  server:
    cluster:
      enabled: true
      host: 192.168.1.10
      port: 9000
      name: mica-mqtt-cluster
      seed-members:
        - 192.168.1.10:9000
        - 192.168.1.11:9000
        - 192.168.1.12:9000
```

**配置类：**
```java
@ConfigurationProperties(prefix = "mqtt.server.cluster")
public class MqttServerClusterProperties {
	private boolean enabled = false;
	private String host = "127.0.0.1";
	private int port = 9000;
	private String name = "mica-mqtt-cluster";
	private List<String> seedMembers = new ArrayList<>();
}
```

#### 4.9 编写集成测试

**创建测试用例：**
```java
public class ClusterIntegrationTest {
	@Test
	public void testThreeNodeCluster() throws Exception {
		// 启动节点 1
		MqttServer server1 = MqttServer.create()
			.name("node1")
			.nodeName("node1")
			.enableMqtt(1883)
			.enableCluster("127.0.0.1", 9001, "127.0.0.1:9001", "127.0.0.1:9002")
			.start();

		// 启动节点 2
		MqttServer server2 = MqttServer.create()
			.name("node2")
			.nodeName("node2")
			.enableMqtt(1884)
			.enableCluster("127.0.0.1", 9002, "127.0.0.1:9001", "127.0.0.1:9002")
			.start();

		// 客户端 A 连接到节点 1
		MqttClient clientA = MqttClient.create()
			.ip("127.0.0.1")
			.port(1883)
			.clientId("clientA")
			.connect();

		// 客户端 B 连接到节点 2
		MqttClient clientB = MqttClient.create()
			.ip("127.0.0.1")
			.port(1884)
			.clientId("clientB")
			.connect();

		// 客户端 B 订阅 topic
		clientB.subscribe("/test/cluster", (context, topic, message, payload) -> {
			System.out.println("ClientB received: " + new String(payload));
		});

		Thread.sleep(1000);

		// 客户端 A 发布消息（应该通过集群转发到客户端 B）
		clientA.publish("/test/cluster", "Hello from Node1".getBytes());

		Thread.sleep(2000);

		// 断言客户端 B 收到消息
	}
}
```

### Phase 6: 文档与示例（预计 1 天）

#### 4.10 编写文档

**任务清单：**
- [ ] 更新 `mica-mqtt-server/README.md`，添加集群配置章节
- [ ] 创建 `docs/cluster-guide.md` 集群部署指南
- [ ] 更新 `CLAUDE.md`，补充集群相关说明
- [ ] 添加配置示例到 `example` 模块

**文档内容要点：**
- 集群配置参数说明
- 部署架构建议（种子节点选择、网络规划）
- 性能调优建议
- 故障排查指南

---

## 5. 配置示例

### 5.1 Java API 配置

```java
MqttServer server = MqttServer.create()
	.name("mqtt-broker-1")
	.nodeName("node-1")
	.enableMqtt(1883)
	.enableCluster("192.168.1.10", 9000,
		"192.168.1.10:9000",  // 种子节点 1（自己）
		"192.168.1.11:9000",  // 种子节点 2
		"192.168.1.12:9000"   // 种子节点 3
	)
	.start();
```

### 5.2 Spring Boot 配置

**application.yml:**
```yaml
mqtt:
  server:
    name: mqtt-broker-1
    node-name: node-1
    port: 1883
    cluster:
      enabled: true
      host: 192.168.1.10
      port: 9000
      name: mica-mqtt-cluster
      heartbeat-interval: 5000
      node-timeout: 15000
      seed-members:
        - 192.168.1.10:9000
        - 192.168.1.11:9000
        - 192.168.1.12:9000
```

### 5.3 Solon 配置

**application.properties:**
```properties
mqtt.server.cluster.enabled=true
mqtt.server.cluster.host=192.168.1.10
mqtt.server.cluster.port=9000
mqtt.server.cluster.seed-members=192.168.1.10:9000,192.168.1.11:9000
```

---

## 6. 测试计划

### 6.1 功能测试

| 测试项 | 测试场景 | 预期结果 |
|--------|----------|----------|
| 集群启动 | 启动 3 个节点，配置相同种子列表 | 所有节点互联成功 |
| 客户端连接通知 | 客户端连接到节点 1 | 节点 2、3 收到连接通知 |
| 跨节点消息转发 | 客户端 A（节点1）发布，客户端 B（节点2）订阅 | 客户端 B 收到消息 |
| 共享订阅 | 多节点客户端订阅 `$share/group1/topic` | 消息负载均衡分发 |
| 节点下线 | 停止节点 2 | 节点 1、3 检测到节点 2 离线 |
| 节点重连 | 重启节点 2 | 节点 2 重新加入集群 |

### 6.2 性能测试

**测试指标：**
- 消息转发延迟（本地 vs 跨节点）
- 集群吞吐量（对比单节点）
- 内存占用（集群元数据开销）

**测试工具：**
- JMeter MQTT 插件
- 自定义压测脚本

---

## 7. 注意事项与限制

### 7.1 网络要求
- **低延迟网络**：建议同机房部署（延迟 < 5ms）
- **防火墙配置**：开放集群端口（默认 9000-9099）
- **带宽规划**：根据消息吞吐量预估集群间流量

### 7.2 集群规模
- **推荐节点数**：3-7 个节点
- **最大节点数**：不超过 10 个（全连接拓扑限制）
- **超大规模**：建议使用 Redis/Kafka 消息分发方案（见 2.4.x broker 模块）

### 7.3 数据一致性
- **节点初始状态同步**：新节点启动加入集群后，必须先发送 `STATE_SYNC_REQUEST` 向存活节点全量拉取当前订阅树和在线客户端状态，完成后再提供服务。
- **最终一致性**：集群运行期间采用异步广播，存在短暂数据不一致窗口。
- **会话接管（Session Takeover）**：如果 Client ID 发生跨节点重连，新节点必须广播踢除指令，旧节点需强制清理旧连接。
- **保留消息（Retained）**：若追求无外部依赖，保留消息需在集群内广播并由各节点在内存中保存副本快照。

### 7.4 故障恢复
- **节点故障级联清理**：当收到 `NODE_LEAVE` 事件时，存活节点必须原子性清理该宕机节点上的所有远程客户端及对应的订阅信息，避免产生路由黑洞。
- **脑裂问题**：当前方案不处理，建议通过网络隔离避免。
- **QoS 与消息可靠性**：内部集群转发基于可靠的 TCP 通信。QoS 1/2 的发布确认直接由接入节点回复，若目标节点投递失败则依赖目标节点的离线会话重传，避免跨节点的长链路 ACK 等待。

---

## 8. 后续优化方向

### 8.1 功能增强
- [ ] 集群监控 API（节点状态、流量统计）
- [ ] 动态节点发现（ZooKeeper/Consul 集成）
- [ ] 集群配置热更新
- [ ] 客户端会话持久化（支持节点故障转移）

### 8.2 性能优化
- [ ] 消息批量转发（减少网络开销）
- [ ] 订阅表索引优化（加速跨节点查询）
- [ ] 零拷贝消息转发（DirectByteBuffer）

### 8.3 运维工具
- [ ] 集群管理 Web 控制台
- [ ] 集群健康检查脚本
- [ ] 监控指标导出（Prometheus）

---

## 9. 时间计划总览

| 阶段 | 任务 | 预计时间 | 负责人 |
|------|------|----------|--------|
| Phase 1 | 基础框架搭建 | 2-3 天 | - |
| Phase 2 | Session 管理器改造 | 2 天 | - |
| Phase 3 | 消息路由改造 | 3 天 | - |
| Phase 4 | 集群事件同步 | 2 天 | - |
| Phase 5 | 配置与测试 | 2 天 | - |
| Phase 6 | 文档与示例 | 1 天 | - |
| **总计** | - | **12-13 天** | - |

---

## 11. 参考资料

- [t-io 官方文档](https://www.tiocloud.com/)
- [t-io cluster 示例](https://gitee.com/tywo45/t-io/tree/master/src/zoo/cluster)
- [mica-mqtt 现有架构](../../CLAUDE.md)
- [MQTT 3.1.1 协议规范](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/mqtt-v3.1.1.html)

---

## 附录 A：集群消息序列化

建议使用 **MessagePack** 或 **Protobuf** 进行序列化，以减少网络开销。

**示例（MessagePack）：**
```java
public class MessagePackSerializer {
	public byte[] serialize(ClusterMessage message) {
		// MessagePack 序列化
	}

	public ClusterMessage deserialize(byte[] data) {
		// MessagePack 反序列化
	}
}
```

---

## 附录 B：集群部署拓扑示例

### 场景 1：三节点集群（同机房）
```
┌────────────────────────────────────────────────┐
│               负载均衡器 (LVS/HAProxy)          │
│            mqtt://cluster.example.com:1883     │
└────────────┬───────────────┬───────────────────┘
             │               │
     ┌───────▼──────┐  ┌────▼────────┐  ┌──────────────┐
     │   Node 1     │  │   Node 2    │  │   Node 3     │
     │ 1883/9000    │  │ 1883/9001   │  │ 1883/9002    │
     │ 10.0.1.10    │  │ 10.0.1.11   │  │ 10.0.1.12    │
     └──────────────┘  └─────────────┘  └──────────────┘
           │                  │                 │
           └──────────────────┴─────────────────┘
                    集群内网互联 (TCP 9000-9002)
```

### 场景 2：跨机房集群（不推荐）
```
机房 A                          机房 B
┌──────────────┐              ┌──────────────┐
│   Node 1     │◀────专线────▶│   Node 2     │
│ 1883/9000    │              │ 1883/9001    │
└──────────────┘              └──────────────┘
```
**注意：** 跨机房延迟高，建议使用消息队列方案。

---

**文档版本：** v1.0
**创建日期：** 2026-02-12
**作者：** mica-mqtt 团队
**状态：** 待实施
