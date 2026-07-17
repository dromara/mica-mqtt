# mica-mqtt 集群监控与运维

> 适用于 V2 dispatcher 与 V3 H2 MVStore。代码提供 Prometheus 文本快照，不内置 HTTP 服务；应用负责把快照挂到现有监控端点。

## 1. 指标导出

通过 `MqttClusterManager.toPrometheus()` 同时导出协议计数器、接管/Retain 指标和存储容量指标：

```java
String prometheusText = clusterManager.toPrometheus();
```

HTTP 端点必须返回 `text/plain; version=0.0.4; charset=utf-8`。若只需程序内诊断，可使用 `clusterManager.getMetrics().snapshot()`。

关键指标如下：

| 指标 | 类型 | 含义 |
|---|---|---|
| `mqtt_cluster_cluster_send_errors_total` | counter | 点对点或广播发送异常/通道不可用 |
| `mqtt_cluster_node_departures_total` | counter | 已确认的节点离开次数 |
| `mqtt_cluster_session_takeover_*_total` | counter | 接管发起、成功、失败和超时 |
| `mqtt_cluster_inflight_replayed_total` | counter | 重连或接管后重放数 |
| `mqtt_cluster_inflight_expired_total` | counter | TTL 到期清理数；是潜在丢失风险代理，不等同于已证实丢消息 |
| `mqtt_cluster_inflight_entries` | gauge | 当前待 ACK 记录数 |
| `mqtt_cluster_retain_replica_latency_millis_total` | counter | Retain 副本接收累计延迟 |
| `mqtt_cluster_retain_replica_latency_samples_total` | counter | Retain 延迟样本数 |
| `mqtt_cluster_retain_query_timed_out_total` | counter | 远程 Retain 查询超时数 |
| `mqtt_cluster_storage_healthy` | gauge | H2 是否打开并健康 |
| `mqtt_cluster_storage_file_size_bytes` | gauge | H2 文件大小 |
| `mqtt_cluster_storage_entries` | gauge | H2 全部 map 的逻辑记录数 |
| `mqtt_cluster_storage_startup_duration_millis` | gauge | 本次 H2 启动耗时 |

Prometheus 规则文件位于 `docs/todo/monitoring/mqtt-cluster-alerts.yml`，覆盖 P4 的 1 GiB、10 万 Inflight、1% 接管失败、0.1% Inflight 过期代理、5 秒 Retain 延迟与 5 秒 H2 启动阈值。

## 2. 上下线 Runbook

### 2.1 扩容

1. 为节点配置独立的 MQTT 端口、集群端口和数据目录。
2. 至少配置一个当前在线 seed，启动节点。不要在所有节点上互相配置完整 seed 全网；mica-net 的 DATA 回调有连接方向语义，推荐固定 2-3 个 bootstrap seed，其余节点只连接 bootstrap。
3. 确认 `storage_healthy=1`，且新旧节点的收发计数持续变化。
4. 观察 Retain 查询超时、发送错误和接管失败至少一个 `nodeTimeout` 周期，再加入负载均衡。

### 2.2 滚动下线

1. 从负载均衡摘除节点，停止接收新连接。
2. 等待客户端主动重连/接管，观察 Inflight 与接管失败率。
3. 正常停止 Broker，使其发送 `NODE_LEAVE`；强杀场景由心跳探活在 `nodeTimeout` 后收敛。
4. 确认存活节点的 Retain 副本已重平衡，随后才能删除旧数据目录。

### 2.3 H2 故障

1. 摘流并停止节点，保留损坏文件作为证据，禁止在线复制正在写入的 MVStore 文件。
2. 从同一节点最近一次冷备恢复；没有备份时使用空目录启动，节点会从集群同步路由，客户端重连重建会话。
3. 若 H2 打开失败，Broker 会降级到内存模式；`storage_healthy=0` 必须持续告警，不能把降级当成恢复完成。

## 3. 性能与容量

- 通过 `-Pcluster-benchmark` 运行 10 万订阅者选择和 10 万 Retain 索引的本机回归阈值。
- 通过 `-Pcluster-chaos` 运行 3/5 个独立 JVM 的强杀、成员收缩、同端口重启和广播恢复验收。
- 微基线不代表端到端 MQTT TPS。上线前必须在目标硬件上使用真实 payload、QoS、客户端数量和磁盘做容量压测。
- H2 文件超过 1 GiB、Inflight 超过 10 万条或查询延迟持续上升时，先定位慢 ACK 客户端和磁盘延迟，再考虑水平扩容。

## 4. 灰度与回滚

按 5% → 25% → 50% → 100% 节点逐步启用，每阶段至少覆盖业务高峰和一次滚动重启。回滚时先摘流，等待接管完成，再关闭 V3 存储配置；不要让两个节点同时以相同 `clientId` owner 状态对外服务。旧版本节点会忽略未知的新消息类型，但混合版本阶段不保证 V3 状态能在 V1 节点持久化。
