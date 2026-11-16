<!--
  AGENTS.md - Instructions for AI Agents working on mica-mqtt

  This file provides context, commands, architecture, and coding standards for AI agents
  to effectively and safely modify this codebase.

  This is the single source of truth for agent instructions in this repository.
  Do not create a parallel CLAUDE.md - update this file instead.
-->

# mica-mqtt Agent Guidelines

## 1. Project Overview

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

## 2. Environment & Build

- **Language**: Java 8 (`<java.version>1.8</java.version>`) — Java 8 compatible code only.
- **Build Tool**: Maven 3.x (multi-module project).
- **Line Endings**: LF (Unix-style), enforced by `.gitattributes` (`* text eol=lf`).
- **Indentation**: TABS for Java files (`.editorconfig` → `[*.java] indent_style = tab`).

### Modules

| Module | Description |
|--------|-------------|
| `mica-mqtt-codec` | MQTT protocol encoding/decoding (no business logic) |
| `mica-mqtt-common` | Common utilities, topic filters, message models |
| `mica-mqtt-client` | MQTT client implementation |
| `mica-mqtt-server` | MQTT broker/server implementation |
| `mica-mqtt-broker` | Cluster broker (t-io based, node-to-node communication) |
| `starter/*` | Spring Boot, Solon, JFinal integrations |
| `example/` | Working examples for all integrations |

> There is **no** `mica-mqtt-core` module. `core` is only a *package* name
> (`org.dromara.mica.mqtt.core.*`) used inside `mica-mqtt-common`, `mica-mqtt-client` and `mica-mqtt-server`.

### Module Structure

```
mica-mqtt/
├── mica-mqtt-codec       # MQTT protocol encoding/decoding (no business logic)
├── mica-mqtt-common      # Common utilities, topic filters, message models
├── mica-mqtt-client      # MQTT client implementation
├── mica-mqtt-server      # MQTT broker/server implementation
├── mica-mqtt-broker      # Cluster broker (t-io based, node-to-node communication)
├── starter/              # Framework integrations
│   ├── mica-mqtt-client-spring-boot-starter
│   ├── mica-mqtt-server-spring-boot-starter
│   ├── mica-mqtt-client-solon-plugin
│   ├── mica-mqtt-server-solon-plugin
│   ├── mica-mqtt-client-jfinal-plugin
│   └── mica-mqtt-server-jfinal-plugin
└── example/              # Usage examples (develop profile)
```

**Dependency Flow:**
- `common` → `codec` + `mica-net-core`
- `client` → `common`
- `server` → `common` + `client` + `mica-net-http`
- `broker` → `server`

**Build Profiles:**
- Root `<modules>`: `codec`, `common`, `client`, `server`, `broker`, `starter`.
- `develop` profile (active by default) **adds** `example` on top of the root modules.

### Key Commands

| Action | Command |
|--------|---------|
| **Build Project** | `mvn clean install` |
| **Run All Tests** | `mvn test` |
| **Run Single Test Class** | `mvn -Dtest=TargetTestClass test` |
| **Run Single Test Method** | `mvn -Dtest=TargetTestClass#methodName test` |
| **Compile Only** | `mvn compile` |
| **Package** | `mvn package -DskipTests` |
| **Build Broker Module Only** | `mvn clean install -pl mica-mqtt-broker -am` |
| **Run Tests in Specific Module** | `cd mica-mqtt-broker && mvn test` |

> **Note**: Always run tests after modifications to ensure no regressions.

### Release Commands

```bash
# Create snapshot release
mvn clean deploy -P snapshot

# Create production release (includes GPG signing, javadoc)
mvn clean deploy -P release
```

## 3. Architecture

### Core Classes

**Client Side:**
- `MqttClient` / `MqttClientCreator`: Main client API with builder pattern
- `IMqttClientSession`: Session management (subscription tracking, message queuing)
- `DefaultMqttClientProcessor`: Message processing pipeline
- `MqttClientAioHandler` / `MqttClientAioListener`: t-io integration layer
- `MqttWillMessage`: Will message model (manual builder, no Lombok)

**Server Side:**
- `MqttServer` / `MqttServerCreator`: Main server API with builder pattern
- `MqttServerProcessor`: Message routing and delivery
- `IMqttSessionManager`: Client session lifecycle management
- `IMqttMessageStore` / `InMemoryMqttMessageStore`: Retained messages and will messages storage
- `MqttServerAioHandler` / `MqttServerAioListener`: t-io integration layer
- `MqttHttpApiListener` / `MqttMcp`: HTTP REST API and MCP interface

**Broker/Cluster Side (mica-mqtt-broker, package `org.dromara.mica.mqtt.broker.cluster`):**
- `MqttBroker`: Entry point for cluster brokers (`MqttBroker.create()`)
- `MqttClusterManager`: Manages cluster node communication
- `MqttClusterConfig` / `MqttClusterBrokerCreator` / `MqttStorageConfig`: Fluent configuration
- `ClusterMqttSessionManager`: Session synchronization across nodes (decorator wrapping `IMqttSessionManager`)
- `ClusterMqttMessageStore` / `ClusterMqttConnectStatusListener`: Cluster-aware store and listener
- `ClusterMessageDispatcher`: Routes messages across broker instances (t-io pipeline handler)
- Dispatch strategies under `pipeline/strategy`: `LocalFirstStrategy`, `RoundRobinStrategy`, `RandomStrategy`, `HashClientStrategy`, `StickyStrategy`, `SharedSubscriptionStrategy`
- Cluster messages implement the `ClusterMessage` interface; `ClusterMessageSerializer` handles wire encoding
- Storage abstraction under `store/`: `SessionStore`, `SharedSubStore`, `InflightStore`, `RetainIndex`, with in-memory (`LocalKvStoreImpl`, `MemoryKvStoreImpl`) and H2 MVStore (`H2MvStoreImpl`, `H2SessionStore`, `H2SharedSubStore`, `H2InflightStore`) implementations

### Design Patterns

1. **Builder Pattern**: `MqttClient.create()`, `MqttServer.create()`, `MqttPublishMessage.builder()`
2. **Listener Pattern**: Event-driven architecture for connections, messages, subscriptions (`MqttProtocolListener`, `IMqttMessageListener`)
3. **Processor Pattern**: Message pipeline with interceptors (`IMqttMessageInterceptor`)
4. **Strategy Pattern**: Pluggable auth (`IMqttServerAuthHandler`), serialization (`MqttSerializer`)
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
- Cluster messages implement the `ClusterMessage` interface
- `ClusterMessageDispatcher` routes messages across broker instances using pluggable dispatch strategies

### HTTP API

Server exposes REST API on port 18083 for:
- Publishing messages to clients
- Querying online clients
- Managing subscriptions

Requires one JSON library: Jackson, Fastjson, Fastjson2, Gson, Hutool-json, or Snack3.
See `docs/http-api.md` for endpoints.

## 4. Code Style & Conventions

### Formatting
- **Indentation**: Use **TABS** (not spaces).
- **Encoding**: UTF-8.
- **Line Endings**: LF (Unix-style).

### Naming
- **Classes**: `PascalCase` (e.g., `MqttServer`, `MqttConnectStatusListener`).
- **Methods/Variables**: `camelCase` (e.g., `publishMessage`, `clientId`).
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `MAX_MESSAGE_SIZE`).
- **Packages**: `lowercase` (e.g., `org.dromara.mica.mqtt.core`).

### Lombok Usage
- **Core Modules** (`mica-mqtt-server`, `mica-mqtt-client`, `mica-mqtt-common`, `mica-mqtt-codec`, `mica-mqtt-broker`):
  - **DO NOT USE LOMBOK**.
  - Manually implement `getters`, `setters`, `equals`, `hashCode`, `toString`, and `constructors`.
  - Use the Builder pattern manually if complex object creation is needed (see `MqttWillMessage`).
- **Starter/Example Modules**:
  - Lombok (e.g., `@Data`) is acceptable in these layers but prefer consistency with existing code.

### License Header
All new Java files must include the Apache License 2.0 header:

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

### Logging
- Use **SLF4J** for logging.
- `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);`
- Use appropriate log levels: `debug` for verbose info, `info` for important events, `warn/error` for issues.
- Avoid excessive `info` logging in high-frequency code paths (e.g., message handling).

### Imports
- Avoid wildcard imports (`import java.util.*;`).
- Group imports in this order:
  1. Project specific imports (`org.dromara...`)
  2. Third-party libraries (`org.slf4j...`, `org.tio...`)
  3. Standard Java (`java...`, `javax...`)
- Use import statements instead of fully qualified class names in code.

### Error Handling
- Use specific exception types rather than generic `Exception`.
- Log exceptions with appropriate context before rethrowing or handling.
- Avoid swallowing exceptions silently unless explicitly intended.
- Example:
  ```java
  try {
      // code
  } catch (IOException e) {
      logger.error("Failed to process message from node: {}", nodeId, e);
      return;
  }
  ```

### Types
- Use primitive types where possible for performance.
- Use `List`, `Map`, `Set` interfaces rather than concrete implementations in method signatures.
- Initialize collections with appropriate implementations (e.g., `ArrayList`, `HashMap`, `HashSet`).

## 5. Testing

- Write unit tests for new logic using JUnit (Jupiter).
- Tests located in `src/test/java` matching package structure.
- Mock external dependencies where possible to keep tests fast.
- Run tests after modifications: `mvn test`.
- Cluster integration tests may have Windows-specific cleanup issues with t-io; focus on test assertions passing.

## 6. Agent Behavior Rules

- **No Assumptions**: Always verify file existence and content before editing. Class names, module names and ports in this document are checked against the current source — re-verify before relying on them.
- **Minimal Changes**: Only modify what is necessary to fulfill the request.
- **Preserve Style**: If you see a file using a specific style (even if it contradicts general rules), follow the file's local style (except for clearly erroneous logic).
- **Safety**: Do not commit secrets or breaking changes without user confirmation.
- **Verification**: After making changes, always run `mvn compile` to verify the code compiles.
- **Testing**: Run relevant tests after modifications to ensure no regressions.
- **Docs**: When behaviour or structure changes, update this file - it is the only agent instruction file.

## 7. Additional Notes

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
- Current version: `2.6.14-SNAPSHOT`
- Main branch for PRs: `master`

### Module-Specific READMEs

Each module has detailed usage documentation:
- `mica-mqtt-client/README.md` - Client API examples
- `mica-mqtt-server/README.md` - Server configuration examples
- `mica-mqtt-broker/README.md` - Cluster broker configuration
- `starter/*/README.md` - Framework-specific configuration
- `example/` - Working examples for all integrations
