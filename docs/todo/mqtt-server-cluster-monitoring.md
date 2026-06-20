# mqtt-server-cluster-monitoring

> Production monitoring, alerting, and runbook for the mica-mqtt cluster broker.

## 1. Metrics Endpoints

### 1.1 Prometheus (recommended)

The broker ships with an in-process Prometheus exporter. Add this HTTP handler
to your Spring Boot application:

```java
@Bean
public HttpHandler clusterMetricsHandler(MqttClusterManager clusterManager) {
    return exchange -> {
        byte[] body = clusterManager.getMetrics().toPrometheus().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    };
}
```

Available metrics (prefix `mqtt_cluster_`):

| Metric                                | Type    | Description                                |
|---------------------------------------|---------|--------------------------------------------|
| `mqtt_cluster_publish_forward_sent_total`    | counter | PUBLISH_FORWARD messages sent to peers     |
| `mqtt_cluster_shared_dispatch_sent_total`    | counter | Shared-sub dispatches sent                 |
| `mqtt_cluster_shared_dispatch_received_total`| counter | Shared-sub dispatches received             |
| `mqtt_cluster_shared_dispatch_repick_total`  | counter | Plan-B re-picks                            |
| `mqtt_cluster_shared_dispatch_dropped_total` | counter | Shared-sub messages dropped (no candidate) |
| `mqtt_cluster_state_sync_requests_total`     | counter | State sync requests sent                   |
| `mqtt_cluster_state_sync_responses_total`    | counter | State sync responses received              |
| `mqtt_cluster_client_connect_broadcast_total`| counter | CLIENT_CONNECT broadcasts                  |
| `mqtt_cluster_client_disconnect_broadcast_total`| counter | CLIENT_DISCONNECT broadcasts             |
| `mqtt_cluster_cluster_messages_sent_total`   | counter | All cluster messages sent                  |
| `mqtt_cluster_cluster_messages_received_total`| counter | All cluster messages received              |
| `mqtt_cluster_cluster_send_errors_total`     | counter | Failed cluster sends                       |

### 1.2 JSON (for ad-hoc inspection)

```java
Map<String, Long> snap = clusterManager.getMetrics().snapshot();
ObjectMapper om = new ObjectMapper();
String json = om.writeValueAsString(snap);
```

### 1.3 JMX (optional)

Each {@code ClusterMetrics} is a plain POJO — register it with the platform
MBean server using a 1-line Spring bean:

```xml
<bean id="clusterMetricsMBean" class="org.springframework.jmx.export.MBeanExporter">
    <property name="beans">
        <map>
            <entry key="mica-mqtt:type=ClusterMetrics" value-ref="clusterMetrics"/>
        </map>
    </property>
</bean>
```

## 2. Alerting Rules (Prometheus)

```yaml
groups:
  - name: mqtt-cluster
    rules:
      # 2.1 Cluster send error rate > 1/s for 5 minutes
      - alert: MqttClusterSendErrorsHigh
        expr: rate(mqtt_cluster_cluster_send_errors_total[5m]) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "MQTT cluster send errors spiking on {{ $labels.instance }}"

      # 2.2 Drop ratio > 5% means shared-sub groups are stranded
      - alert: MqttSharedDispatchDropRatioHigh
        expr: |
          rate(mqtt_cluster_shared_dispatch_dropped_total[5m])
            / (rate(mqtt_cluster_shared_dispatch_sent_total[5m]) + 0.001) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "More than 5% of shared-sub messages dropped; check group membership"

      # 2.3 Re-pick rate indicates plan-B fallback (subscriber gone)
      - alert: MqttSharedDispatchRepickSpike
        expr: rate(mqtt_cluster_shared_dispatch_repick_total[5m]) > 10
        for: 5m
        labels:
          severity: info
        annotations:
          summary: "Shared-sub re-picks spiking — subscribers may be churning"

      # 2.4 Cluster divergence: received >> sent (broadcast loop)
      - alert: MqttClusterDivergence
        expr: |
          abs(
            rate(mqtt_cluster_cluster_messages_received_total[5m])
            - rate(mqtt_cluster_cluster_messages_sent_total[5m])
          ) > 100
        for: 5m
        labels:
          severity: critical
```

## 3. Runbook

### 3.1 Add a new node

1. Pick a unique cluster port (e.g. 9003)
2. Pick a unique data dir (e.g. `data/node-3`)
3. Start the broker with the same cluster seed list as the existing nodes
4. Verify with `mqtt_cluster_cluster_messages_received_total` incrementing

### 3.2 Drain a node (rolling restart)

1. Set the broker to refuse new connections (use a load balancer rule)
2. Wait for `mqtt_cluster_publish_forward_sent_total` to stop incrementing
3. For each client connected to this node, the takeover protocol will re-home
   the session to a peer; `mqtt_cluster_session_migrated_total` should tick
4. Stop the broker

### 3.3 Recover from H2 corruption

The H2 MVStore file is journaled; transient corruption is usually recovered
automatically on the next open.  If the file is permanently damaged:

1. Stop the broker
2. Move `data/<node>/mica-mqtt.mv.db` to a backup location
3. Restart — the broker will start in pure-memory mode (INV-6) and clients
   will reconnect with fresh sessions
4. After all clients are connected, stop and re-enable the V3 storage

## 4. Capacity Planning

| Workload                          | Per-node throughput | Recommended cluster size |
|-----------------------------------|---------------------|--------------------------|
| 10k concurrent clients, 1 msg/s  | ~5k msg/s           | 3 nodes                  |
| 50k concurrent clients, 1 msg/s  | ~3k msg/s           | 5 nodes                  |
| 100k concurrent clients, retain  | 1.5k msg/s          | 7 nodes, 5+ replicas     |

These numbers are conservative; the broker's actual ceiling depends on
hardware and payload size.  Always load-test before production.
