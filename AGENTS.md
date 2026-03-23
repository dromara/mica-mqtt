<!--
  AGENTS.md - Instructions for AI Agents working on mica-mqtt
  
  This file provides context, commands, and coding standards for AI agents 
  to effectively and safely modify this codebase.
-->

# mica-mqtt Agent Guidelines

## 1. Environment & Build

- **Language**: Java 8 (`<java.version>1.8</java.version>`).
- **Build Tool**: Maven 3.x.
- **Project Structure**: Multi-module Maven project.

### Modules

| Module | Description |
|--------|-------------|
| `mica-mqtt-codec` | MQTT protocol codec |
| `mica-mqtt-common` | Common utilities and models |
| `mica-mqtt-core` | Core server/client interfaces |
| `mica-mqtt-server` | MQTT broker implementation |
| `mica-mqtt-client` | MQTT client implementation |
| `mica-mqtt-broker` | Enhanced broker with cluster support (t-io based) |
| `starter` | Spring Boot, Solon, JFinal integrations |
| `example` | Usage examples |

### Key Commands

| Action | Command |
|--------|---------|
| **Build Project** | `mvn clean install` |
| **Run All Tests** | `mvn test` |
| **Run Single Test Class** | `mvn -Dtest=TargetTestClass test` |
| **Run Single Test Method** | `mvn -Dtest=TargetTestClass#methodName test` |
| **Compile Only** | `mvn compile` |
| **Package** | `mvn package -DskipTests` |
| **Run Tests in Specific Module** | `cd mica-mqtt-broker && mvn test` |

> **Note**: Always run tests after modifications to ensure no regressions.

## 2. Code Style & Conventions

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

## 3. Architecture & Patterns

- **Asynchronous IO**: The project is built on `t-io`. Be careful with blocking operations in IO threads.
- **Thread Safety**: Core classes like `MqttServer` are often accessed concurrently. Ensure thread safety for shared state.
- **Serialization**: Use the `MqttSerializer` interface for payload serialization.
- **Design Patterns**:
  - **Builder**: Used for complex configuration/message objects (e.g., `MqttPublishMessage.builder()`).
  - **Listener**: Heavily used for events (`MqttProtocolListener`, `IMqttMessageListener`).
  - **Decorator**: Used in `mica-mqtt-broker` for session management (`ClusterMqttSessionManager` wraps `IMqttSessionManager`).

### Cluster Module (mica-mqtt-broker)
- Built on t-io cluster for node-to-node communication.
- Uses `MqttBroker.create()` as entry point for cluster brokers.
- Fluent API for configuration (`MqttClusterConfig.enabled(true).clusterPort(9001)`).
- Cluster messages inherit from `ClusterMessage` base class.
- Session state is synchronized across nodes via `ClusterMqttSessionManager`.

## 4. Testing

- Write unit tests for new logic using JUnit (Jupiter).
- Place tests in the `src/test/java` directory corresponding to the package structure.
- Mock external dependencies where possible to keep tests fast.
- Cluster integration tests may have Windows-specific cleanup issues with t-io; focus on test assertions passing.

## 5. Agent Behavior Rules

- **No Assumptions**: Always verify file existence and content before editing.
- **Minimal Changes**: Only modify what is necessary to fulfill the request.
- **Preserve Style**: If you see a file using a specific style (even if it contradicts general rules), follow the file's local style (except for clearly erroneous logic).
- **Safety**: Do not commit secrets or breaking changes without user confirmation.
- **Verification**: After making changes, always run `mvn compile` to verify the code compiles.
- **Testing**: Run relevant tests after modifications to ensure no regressions.
