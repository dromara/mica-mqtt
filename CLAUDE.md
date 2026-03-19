# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**mica-mqtt** is a high-performance, low-latency MQTT IoT component for Java 8+, maintained by the Dromara organization. It provides both client and server (broker) implementations supporting MQTT v3.1, v3.1.1, and v5.0 protocols.

**Key Features:**
- Lightweight core (only 500KB)
- Built on t-io async networking framework
- WebSocket MQTT sub-protocol support (compatible with mqtt.js)
- HTTP REST API for server management
- Shared subscriptions and queue subscriptions
- Client/Server support for Spring Boot, Solon, and JFinal
- GraalVM native compilation support
- **Clustering via `mica-mqtt-broker` module** (t-io cluster for node-to-node communication)

**Documentation:** https://mica-mqtt.dreamlu.net/guide/
**Demo Server:** mqtt.dreamlu.net (username: mica, password: mica)

## Common Commands

### Build & Test
```bash
# Full build with tests (includes example module via develop profile)
mvn clean install

# Build without tests
mvn package -DskipTests

# Run all tests
mvn test

# Run specific test class
mvn -Dtest=TopicFilterTypeTest test

# Run specific test method
mvn -Dtest=TopicFilterTypeTest#methodName test

# Compile only
mvn compile
```

### Module-Specific Build
```bash
# Build mica-mqtt-broker (cluster module, not in develop profile by default)
mvn clean install -pl mica-mqtt-broker -am
```

### Release Commands
```bash
# Create snapshot release
mvn clean deploy -P snapshot

# Create production release (includes GPG signing, javadoc)
mvn clean deploy -P release
```

## Architecture Overview

### Module Structure

```
mica-mqtt/
├── mica-mqtt-codec       # MQTT protocol encoding/decoding (no business logic)
├── mica-mqtt-common      # Common utilities, topic filters, message models
├── mica-mqtt-client      # MQTT client implementation
├── mica-mqtt-server      # MQTT broker/server implementation
├── mica-mqtt-broker      # Cluster broker (t-io based, node-to-node communication)
└── starter/              # Framework integrations
    ├── mica-mqtt-client-spring-boot-starter
    ├── mica-mqtt-server-spring-boot-starter
    ├── mica-mqtt-client-solon-plugin
    ├── mica-mqtt-server-solon-plugin
    ├── mica-mqtt-client-jfinal-plugin
    └── mica-mqtt-server-jfinal-plugin
```

**Dependency Flow:**
- `client/server` → `common` → `codec` + `mica-net-core` (both direct dependencies)
- `broker` → `server` + `mica-net-core`

**Active Profile Modules** (`develop` profile, active by default):
`common`, `client`, `server`, `starter`, `example`

Note: `mica-mqtt-codec` and `mica-mqtt-broker` are built as transitive dependencies but not as top-level modules in the develop profile.

### Core Classes

**Client Side:**
- `MqttClient` / `MqttClientCreator`: Main client API with builder pattern
- `IMqttClientSession`: Session management (subscription tracking, message queuing)
- `DefaultMqttClientProcessor`: Message processing pipeline
- `MqttClientAioHandler` / `MqttClientAioListener`: t-io integration layer

**Server Side:**
- `MqttServer` / `MqttServerCreator`: Main server API with builder pattern
- `MqttServerProcessor`: Message routing and delivery
- `IMqttSessionManager`: Client session lifecycle management
- `MqttMessageStore`: Retained messages and will messages storage
- `MqttServerAioHandler` / `MqttServerAioListener`: t-io integration layer

**Broker/Cluster Side (mica-mqtt-broker):**
- `MqttBroker`: Entry point for cluster brokers (`MqttBroker.create()`)
- `MqttClusterManager`: Manages cluster node communication
- `ClusterMqttSessionManager`: Session synchronization across nodes (decorator wrapping `IMqttSessionManager`)
- `ClusterMessageDispatcher`: Routes messages across broker instances
- `BrokerMessage`: Base class for cluster messages

### Design Patterns

1. **Builder Pattern**: `MqttClient.create()`, `MqttServer.create()`, `MqttPublishMessage.builder()`
2. **Listener Pattern**: Event-driven architecture for connections, messages, subscriptions (`MqttProtocolListener`, `IMqttMessageListener`)
3. **Processor Pattern**: Message pipeline with interceptors (`IMqttMessageInterceptor`)
4. **Strategy Pattern**: Pluggable auth (`IMqttServerAuthHandler`), serialization (`IMqttSerializer`), message dispatching (`IMqttMessageDispatcher`)
5. **Decorator Pattern**: `ClusterMqttSessionManager` wraps `IMqttSessionManager` for session sync

### Thread Safety & Async I/O

- Built on **t-io** framework - avoid blocking operations in I/O callback threads
- Core classes like `MqttServer` and `MqttClient` handle concurrent access
- Use `ChannelContext` for per-connection state
- Session storage uses concurrent data structures (`ConcurrentHashMap`)

### Protocol Ports (Default)

| Port  | Protocol          | Purpose                          |
|-------|-------------------|----------------------------------|
| 1883  | TCP               | Standard MQTT                    |
| 8883  | TCP SSL           | MQTT over TLS                    |
| 8083  | WebSocket         | WebSocket MQTT sub-protocol      |
| 8084  | WebSocket SSL     | Secure WebSocket MQTT            |
| 18083 | HTTP              | REST API & MCP interface         |

### Topic Wildcards

- `/`: Hierarchy separator (e.g., `a/b/c`)
- `#`: Multi-level wildcard - matches ≥0 levels (e.g., `a/#` matches `a/`, `a/b`, `a/b/c`). Must be last character.
- `+`: Single-level wildcard - matches exactly 1 level (e.g., `a/+` matches `a/b`, not `a/b/c`). Can combine with `#`: `+/tennis/#`

### Shared Subscriptions

Two modes for load balancing:
1. **Queue Subscription**: `$queue/topic` - Only one subscriber receives each message
2. **Group Subscription**: `$share/<group>/topic` - One subscriber per group receives each message

**Note:** If publishing to `/topic/test` (leading slash), subscribe to `$share/group1//topic/test` (double slash).

### Framework Integration Points

**Spring Boot:**
- Auto-configuration via `@EnableMqttServer` / `@EnableMqttClient`
- `@MqttClientSubscribe` for declarative subscriptions
- `MqttTemplate` for publishing (if using starter patterns)
- Properties: `mqtt.client.*`, `mqtt.server.*`

**Solon:**
- Plugin-based initialization
- Use `@MqttClientSubscribe` annotations
- Configuration through `application.yml`

**JFinal:**
- Manual plugin registration in `configPlugin()`
- Direct API usage via `MqttClient` / `MqttServer`

### Clustering & Message Dispatching

The `mica-mqtt-broker` module provides clustering via t-io cluster for node-to-node communication:
- `MqttBroker.create()` as entry point with fluent API: `MqttClusterConfig.enabled(true).clusterPort(9001)`
- Session state synchronized across nodes via `ClusterMqttSessionManager`
- Cluster messages inherit from `BrokerMessage` base class
- Implement `IMqttMessageDispatcher` to route messages across brokers (Redis Pub/Sub, Kafka, RocketMQ)

### HTTP API

Server exposes REST API on port 18083 for:
- Publishing messages to clients
- Querying online clients
- Managing subscriptions

Requires one JSON library: Jackson, Fastjson, Fastjson2, Gson, Hutool-json, or Snack3.
See `docs/http-api.md` for endpoints.

## Important Notes

### Code Style

- **Java 8** compatible code only
- **NO Lombok** in core modules (`mica-mqtt-client`, `mica-mqtt-server`, `mica-mqtt-common`, `mica-mqtt-codec`, `mica-mqtt-broker`)
- Use **TABS** for indentation (not spaces)
- Always include Apache License 2.0 header in new files
- Use SLF4J for logging: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);`

### License Header (required for new files)

```java
/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

### Testing

- Tests located in `src/test/java` matching package structure
- Run tests after modifications: `mvn test`
- Mock external dependencies for unit tests

### Android Support

Minimum API 26 (Android 8.0). Add to `build.gradle`:
```groovy
android {
    packagingOptions {
        exclude 'META-INF/INDEX.LIST'  // Required for mica-mqtt
    }
}
```

### Memory Optimization

For server deployments handling many connections, use JVM flag: `-Xss129k` to reduce per-thread stack size.

### Version Management

- Uses `${revision}` property for version (defined in root pom.xml)
- Flatten Maven plugin processes version for publishing
- Current version: `2.6.0`
- Main branch for PRs: `master`

## Module-Specific READMEs

Each module has detailed usage documentation:
- `mica-mqtt-client/README.md` - Client API examples
- `mica-mqtt-server/README.md` - Server configuration examples
- `mica-mqtt-broker/README.md` - Cluster broker configuration
- `starter/*/README.md` - Framework-specific configuration
- `example/` - Working examples for all integrations
