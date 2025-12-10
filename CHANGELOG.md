# 变更记录

## 发行版本

### v2.5.10 - 2025-12-28

- ✨ mica-mqtt-client 支持使用 MqttSubscriptionOption 进行订阅配置 感谢 `@rocker-cheng` 反馈（gitee #IDB1DN）
- 🔄 mica-mqtt-codec 优化 MQTT 消息解码代码结构，编解码逻辑，减少不必要的对象创建，提升内存使用效率
- 🔧 移除不必要的 @org.springframework.lang.NonNull 注解，spring7 已移除
- 🐛 修复 UnSubAck 消息中的取消订阅原因码应该为单字节
- ⬆️ 升级到 mica-net 1.2.5

### v2.5.9 - 2025-11-29

- ✨ mica-mqtt-client solon 和 spring 插件 MQTT 客户端订阅中的 beanName 支持占位符解析，感谢 `@tan90` 反馈（gitee #ID7PF6）
- ✨ mica-mqtt-server ClientInfo 添加 SSL 和 WebSocket 标识。
- ⬆️ 升级到 mica-net 1.2.4，优化 sse，修复 jackson3 方法错误。

### v2.5.7 - 2025-11-07

- ✨ mica-mqtt-server 新增 `/api/v1/stats/sse` 接口，支持通过 SSE 实时获取服务器统计信息
- ✨ example 升级到 solon 3.7.0 更改相关依赖命名规则
- ⬆️ 升级 mica-net 到 1.2.2 支持 snack4 json 序列化，内存优化和消息发送性能优化
- 🐛 mica-mqtt-server-solon-plugin 移除 MqttServerConfiguration bean MqttFunctionManager 的 static 修饰符

### v2.5.6 - 2025-10-27
- 🐛 修复 MQTT 解码器中的缓冲区读取问题，修复解码异常重连后无法恢复的问题。（所有版本）
- ⬆️ 升级 mica-net 到 1.2.1，修改慢包读取 (gitee #ID3IAU)，影响范围（2.5.5）

### v2.5.5 - 2025-10-10
- ✨ mqtt-client 添加通过 `Consumer<MqttWillPublishProperties>` 函数式接口自定义遗嘱属性
- ✨ mqtt-client 添加直接使用 MqttPublishBuilder 发布消息
- ✨ mqtt-client 添加 disconnectBeforeStop 配置（默认 true），断开连接前是否发送 disconnect 消息，感谢 `@steven` 反馈（gitee #ICXY4A）
- ✨ mica-mqtt-server 使用 ConcurrentHashMap 替代 IntObjectHashMap，优化内存会话管理
- ✨ mica-mqtt-server-spring-boot-starter bean 加载顺序优化，避免出现提示
- ⬆️ 升级 mica-net 到 1.2.0，调整慢包攻击规则和支持 jackson3，感谢 `@well` 反馈（gitee #ICXF5N）

### v2.5.4 - 2025-08-29
- ✨ mica-mqtt-server 使用前缀树管理 MQTT 订阅。
- ✨ mica-mqtt-server 心跳超时小于等于0时，不开启心跳检测。（不建议这样使用）感谢 `@刘业兴` 反馈（gitee #ICTT2V）
- ✨ mica-mqtt-server solon 和 spring 插件，将 `@MqttServerFunction` 统一到 mica-mqtt-common 包中，不兼容。
- ✨ mica-mqtt-server solon 和 spring 插件，`@MqttServerFunction` 增加 topic 变量解析功能，支持解析 Map<String, String> 类型的 topic 中的 ${topicVars} 变量参数。
- ✨ mica-mqtt-client solon 和 spring 插件，将 `@MqttClientSubscribe` 统一到 mica-mqtt-common 包中，MqttClientTemplate 中的 `DEFAULT_CLIENT_TEMPLATE_BEAN` 常量定义移动到了 `@MqttClientSubscribe`，不兼容。
- ✨ mica-mqtt-client solon 和 spring 插件，`@MqttClientSubscribe` 注解订阅，增加 topic 变量解析功能，支持解析 Map<String, String> 类型的 topic 中的 ${topicVars} 变量参数。
- ✨ mica-mqtt-codec 移除了 MqttCodecUtil 中的 `isValidPublishTopicName` 方法 ，直接使用 `isTopicFilter` 校验发布主题名称是否包含通配符。
- ✨ mica-mqtt-codec 包调整，重命名类名、方法名，重构 MQTT 消息构建器类（为后续方便做准备），不兼容。
- ✨ mica-mqtt-common `TopicUtil` 调整 validateTopicFilter 方法，移除了对空白字符的校验。（注意：emqx 支持使用空白字符，mosquitto 不支持）。
- ✨ `TopicUtil` 和 `MqttCodecUtil` 增加对 topic 中空白符的日志提示。感谢 `@长草颜团子` 反馈（gitee #26）

### v2.5.3 - 2025-08-03
- ✨ mica-mqtt-server-spring-boot-starter 支持注解 `@MqttServerFunction` 监听
- ✨ mica-mqtt-server-solon-plugin 支持注解 `@MqttServerFunction` 监听
- ✨ mica-mqtt-client-solon-plugin 更新 `solon-configuration-metadata.json`
- ✨ mica-mqtt-codec `ReasonCode` 统一移动到 `codes` 包（不影响老用户升级）

### v2.5.2 - 2025-07-27
- 🐛 mica-mqtt-server 修复启动报错（影响范围 `2.5.0` ~ `2.5.1`），感谢 `CoderKK` 反馈（gitee #ICOQ3Q）

### v2.5.1 - 2025-07-24
- ✨ mica-mqtt-server 优化 sse mcp，添加 sse 心跳
- ✨ mica-mqtt-client 内置 ssl SNI 支持，感谢 `sword007`、`@TomatoLay` 反馈（gitee #ICKBAY #ICEANP）
- ✨ mica-mqtt-client 支持多网卡下指定网卡 `bindIp`（网卡对应IP）和 `bindNetworkInterface`（网卡名） 配置（2个方法使用任意一个即可）。感谢 `@iovera` 反馈（gitee #ICO699)
- 🐛 mica-mqtt-client 高CPU下 packetId 生成超限，感谢 `@火焰之魂` 反馈（gitee #ICLXC3）

### v2.5.0 - 2025-07-12
- ✨ mica-mqtt X AI，mica-mqtt-server 支持大模型 mcp
- ✨ mica-mqtt-server 支持同时配置多协议支持，拆分 `http(默认端口18083)` 和 `websocket(默认端口8083)`，使统计更加准确
- ✨ mica-mqtt-server spring、solon 插件删除不推荐使用的 `EventMqttMessageListener`
- ✨ mica-mqtt-server 调整保留消息的规则，支持 `$retain` 带存储周期的保留消息 感谢 `@tan90` pr #ICB9I2 #23
- ✨ mica-mqtt-client 删除 `IMqttClientMessageIdGenerator` 接口合入 `IMqttClientSession` 接口
- ✨ mica-mqtt-client 默认 mqtt5.0，cleanSession 改为 cleanStart 感谢 `@tan90` 反馈 (gitee #IBKKAG)
- ✨ mica-mqtt-client MqttClient 和 MqttClientTemplate 支持通过代理接口来进行Publish 感谢 `@galaxy-sea` pr (github #100)
- ✨ mica-mqtt-codec 完全同步成私服版，将 MqttProperties 内部类拆解出来，方便使用
- ✨ mica-mqtt client、server solon 插件添加对 [Solon IDEA](https://plugins.jetbrains.com/plugin/21380-solon) 插件配置提示支持。
- ✨ 移除 mica-mqtt-broker，未来重构
- ✨ 统一参数命名，userName 统一为 username。
- ✨ 优化部分日志，使用中文，方便大家排查问题

### v2.4.9 - 2025-06-27
- 🐛 mqtt server 修复 gitee #ICID15

### v2.4.8 - 2025-06-20
- ✨ mica-mqtt-client-spring-boot-starter `MqttClientSubscribeDetector` bean 配置改成 `static` 方法。
- ✨ mica-mqtt-server 调整保留消息标志位的规则。

### v2.4.7 - 2025-06-02
- ✨ mica-mqtt-client、mica-mqtt-server publish相关接口支持object发送 (github #98) 感谢 `@galaxy-sea` 贡献
- ✨ mica-mqtt-client 调整 MqttClient#reconnect 策略，（gitee #IBY5LQ）感谢 `@拉风的CC` 反馈。
- ✨ mica-mqtt-codec MqttCodecUtil#isTopicFilter 代码优化改为逆序循环
- ✨ mica-mqtt-codec 代码优化详见： https://github.com/netty/netty/pull/15227
- ✨ mica-net-http http api 响应头 name 不转换成小写
- 🔧 mica-mqtt-common 更新 `module-info.java` 添加序列化模块
- 🐛 mica-net-utils DefaultThreadFactory 不应该共用。

### v2.4.6 - 2025-05-19
- ✨ mica-mqtt-client-spring-boot-starter MqttDeserializer 接口重写，支持泛型调用 (github #95) 感谢 `@galaxy-sea` 贡献
- ✨ mica-mqtt-client 批量订阅兼容 mqtt 3.1.1 部分 broker 只返回一个 reasonCode 的情况。感谢 `@Jacky` 反馈
- ✨ mica-mqtt-server-solon-plugin 添加对 metrics 指标的支持
- ✨ mica-mqtt-client-solon-plugin 注解订阅支持自定义序列化（默认 json 序列化）和泛型

### v2.4.5 - 2025-05-06
- ✨ mica-mqtt-client-spring-boot-starter 的 `@MqttClientSubscribe` 注解支持自定义反序列化。
- ✨ 优化代码 Spring Boot Client 可以自定义 MqttClientSubscribeDetector github #90 感谢 `@galaxy-sea` 贡献
- ✨ 升级 mica-net 到 1.1.6，解决 eclipse paho mqtt websocket client 连接 mica-mqtt server 报错
- ✨ 依赖调整，减少 example 示例项目的安全提示
- 🐛 解决服务端重启时 client有消息发送，导致 client 无法正常重连 gitee #IC4DWT 感谢 `@wtjperi2003` 反馈
- 🐛 同步 netty mqtt codec Fix the assignment error of maxQoS parameter in ConnAck Properties

### v2.4.4 - 2025-04-13
- ✨ mica-mqtt-server，更好的兼容 Android 环境。github #81 感谢 `@KittenBall` 的联调测试。

### v2.4.3 - 2025-03-23
- ✨ Central Portal 开始支持 Snapshots（仅存储90天，需尽快切到最新的正式版），dev 分支提交后 Github action 自动发布快照版。
- ✨ 精简，删除没有用到的代码，下沉到 mica-net。
- ✨ mica-mqtt-client 添加 heartbeatMode 和 heartbeatTimeoutStrategy，用于某些弱网场景 gitee #IBSMZ7 感谢 `@拉风的CC` 反馈。
- ✨ mica-mqtt-server 默认依赖上 mica-net-http，不再需要手动添加依赖，简化使用。
- ✨ mica-mqtt-server-spring-boot-starter MqttServerTemplate 暴露 `getMqttServer()` 方法，方便使用。
- ✨ mica-mqtt-server-spring-boot-starter 兼容存在 `MeterRegistry` 类，但是 `MeterRegistry` bean 不存在的情况。gitee #IBLBCY 感谢 `@xxg` 反馈。

### v2.4.2 - 2025-01-24
- ✨ mica-mqtt-client Spring Boot stater 和 solon 插件添加工作线程数配置 `bizThreadPoolSize` (默认：2，如果消息量大，业务复杂处理慢，例如做emqx消息转发处理，可调大此配置)。
- ✨ mica-mqtt-client Spring Boot stater 和 solon 插件添加 MQTT5.0 的 `sessionExpiryIntervalSecs` 配置 gitee #IBIE27 感谢 `@cyber` 反馈。
- ✨ mica-mqtt-client 调整重连重新订阅逻辑，Spring Boot stater 和 solon 插件 `@MqttClientSubscribe` 注解订阅，保留 session 重连时不丢失消息 gitee #IBIE27 感谢 `@cyber` 反馈。
- ✨ mica-mqtt-client DefaultMqttClientSession 移除 `final` 修饰，方便继承自定义。
- ✨ mica-mqtt-client 将 clientId 绑定到 context 上，可以使用 `context.getId()` 获取，方便多 mqtt client 实例下使用，gitee #IBHHB1 感谢 `@cv` 反馈。
- ✨ mica-mqtt-server proxy 代理协议简化，已测底抽象到 mica-net。
- ✨ mica-mqtt-common 调整 `TopicUtil`，支持原生 Android，gitee #IBJBFL 感谢 `@DeanNode` 反馈。
- ✨ mica-mqtt-server 默认的 nodeName 改为随机 `nanoId`，支持原生 Android，gitee #IBJBFL 感谢 `@DeanNode` 反馈。
- ✨ 将 MqttServerCustomizer 和 MqttClientCustomizer 抽到 mica-mqtt-server、mica-mqtt-client 方便组件封装，需要调整包名，请先将老的包导入删除，idea 会自动引入新的包。
- 🐛 mica-mqtt-client-spring-boot-starter 修复 Spring Boot 3.2 启动时出现警告 gitee #IBITP5 感谢 `@cyber` 反馈。

### v2.4.2-M2 - 2025-01-23
- ✨ mica-mqtt-client Spring boot stater 和 solon 添加工作线程数配置 `bizThreadPoolSize` 。
- ✨ mica-mqtt-server proxy 代理简化，已测底抽象到 mica-net。
- ✨ mica-mqtt-common 调整 TopicUtil，支持原生 Android，gitee #IBJBFL 感谢 `@DeanNode` 反馈。
- ✨ mica-mqtt-server nodeName 改为随机 nanoId ，支持原生 Android，gitee #IBJBFL 感谢 `@DeanNode` 反馈。
- 🐛 mica-mqtt-client-spring-boot-starter 修复 Spring Boot 3.2 启动时出现警告 gitee #IBITP5 感谢 `@cyber` 反馈。
- ⬆️ 升级 mica-net 到 1.0.12，tcp server 如果非 debug 不开启版本信息等打印，方便支持 Android（Android 下没有 ManagementFactory）

### v2.4.2-M1 - 2025-01-17
- ✨ mica-mqtt client 调整重连重新订阅的逻辑 gitee #IBIE27 感谢 `@cyber` 反馈。
- ✨ mica-mqtt client solon 和 spring boot 插件添加 MQTT5.0 的 sessionExpiryIntervalSecs 配置 gitee #IBIE27  感谢 `@cyber` 反馈。
- ✨ mica-mqtt client DefaultMqttClientSession 移除 final 修饰，方便继承自定义。
- ✨ mica-mqtt client 将 clientId 绑定到 context 上，可以使用 `context.getId()` 获取，gitee #IBHHB1 感谢 `@cv` 反馈。
- ✨ 将 MqttServerCustomizer 和 MqttClientCustomizer 抽到 mica-mqtt-server、mica-mqtt-client 方便组件封装 gitee #IBIJDF 感谢 `@cyber` 反馈

### v2.4.1 - 2025-01-04
- ✨ mqtt server 统一 topic 订阅、发布认证日志方便排查问题。
- ✨ mqtt server 添加 PROXY protocol v1 支持，nginx 可开启 tcp proxy_protocol on; 时转发源 ip 信息。
- 📝 修复文档 maven 坐标错误。
- 🐛 修复 spring boot 项目使用全局懒加载 topic无法订阅 gitee #IBFIV8 感谢 `@xixuanhao` 反馈

### v2.4.0 - 2024-12-07
- ✨ http api 添加 `stats`、`clients` 列表和 `client详情` 接口。
- ✨ MqttServer 和 MqttServerTemplate 添加 `getClientInfo` `getClients` 系列客户端信息接口。
- ✨ MqttServer 和 MqttServerTemplate 添加 `getSubscriptions` 获取客户端订阅列表接口。
- ✨ MqttServer 和 MqttServerTemplate 添加 `getStat` 统计接口。
- 🚚 调整 maven groupId `net.dreamlu` 到新的 `org.dromara.mica-mqtt`。
- 🚚 调整包名 `net.dreamlu.iot.mqtt` 到新的 `org.dromara.mica.mqtt`，其他均保持不变。
- 🚚 切换到 central sonatype，central sonatype 不支持快照版，mica-mqtt 不再发布快照版。
- 🐛 修复订阅发送时机问题 gitee #IB72L6 感谢 `@江上烽` 反馈

### v2.4.0-M2 - 2024-12-01
- ✨ mica-mqtt-server 暴露获取 clientId 和状态统计接口
- 🐛 修复订阅发送时机问题 gitee #IB72L6 感谢 `@江上烽` 反馈

### v2.4.0-M1 - 2024-11-24
- ✨ 调整 groupId 到 org.dromara.mica-mqtt
- 🚚 切换包名到 org.dromara
- 🚚 切换到 central sonatype， central sonatype 不支持快照版

### v2.3.9 - 2024-11-16
- ✨ ssl 支持 **PKCS12** 证书，根据文件后缀自动判断 `.jks、.keystore` 识别为 **JKS证书**，`.p12、.pfx` 识别成 **PKCS12证书**。其他默认成**JKS**
- ✨ 优化 **Solon** 版本依赖（仅编译依赖），兼容 `2.8.0` 和 `2.8.0` 以上版本。

### v2.3.8 - 2024-09-26
- ✨ 升级到 solon v3, 调整 solon 版本兼容
- 🐛 mica-net 心跳支持了 `keepAliveBackoff`，mica-mqtt 漏改规则（影响范围：mica-mqtt server 开源版，私服版无此问题。） gitee #IAW9FC 感谢 `tan90` 反馈。

### v2.3.7 - 2024-09-22
- ✨ 优化 Mqtt server starter 添加 schedule 系列方法。
- ✨ MqttClient schedule 系列方法下层到底层，方法改造。

### v2.3.6 - 2024-09-14
- ✨ mica-mqtt server 和 client 优化 stop，支持 stop 后重新配置再启动（注意：需要重新配置，因为老的线程池已经停止）。
- ✨ mica-mqtt server 和 client Spring boot starter 支持 Spring boot devtools 热启动。
- ✨ `FastJsonMessageSerializer` 重构为 `JsonMessageSerializer`。
- ✨ 添加 `module-info.java` 方便模块化。

### v2.3.5 - 2024-09-01
- ✨ 新增 `SSLEngineCustomizer`，用于自定义 tls 协议版本和加密套件。
- ✨ !20 修改了 solon 插件的默认配置数值，感谢 `@peigen` pr。

### v2.3.4 - 2024-08-10
- ✨ mica-mqtt 合并 `mica-mqtt-client-solon-plugin` 和 `mica-mqtt-server-solon-plugin` 感谢 `@peigenlpy`
- ✨ jfinal 插件重命名为 `mica-mqtt-client-jfinal-plugin` 和 `mica-mqtt-server-jfinal-plugin`
- 🐛 mica-mqtt-server 修复分组订阅删除，感谢 `@tangjj` 反馈。

### v2.3.3 - 2024-07-22
- ✨ mica-mqtt-server 可停止，同步捐助版。
- ✨ mica-mqtt-server 添加 schedule 系列方法，同步捐助版。
- ✨ mica-mqtt 代码优化 TopicUtil 优化 getTopicFilter 方法。
- ✨ mica-mqtt 优化 AckTimerTask 和 retry 重发日志。gitee #IABQ7L 感谢 `@tan90` 反馈。
- ✨ mica-mqtt-client-spring-boot-starter 更加方便自定义 MqttClientTemplate。
- ✨ mica-mqtt-client-spring-boot-starter MqttClientTemplate 暴露更多方法，方便使用。
- ✨ mica-mqtt-example 添加 ssl 测试代码
- 🐛 mica-mqtt-client 修复 ssl 服务端重启问题 gitee #IA9FFW #IAEHOD 感谢 `@geekerstar` `@hangrj` 反馈。

### v2.3.1 - 2024-06-25
- ✨ mica-mqtt-server 重构心跳，心跳检测模式默认为：最后接收的数据时间。gitee #I9R0SN #IA69SM 感谢 `@HY` `@tan90` 反馈。
- ✨ mica-mqtt-server 优化端口占用的异常提示，方便排查。
- ✨ mica-mqtt client 使用 mica-net 内置的心跳检测，内置心跳已重构。
- ✨ mica-mqtt-client 重连不管服务端是否存在 session 都发送订阅。gitee #I9VIUV 感谢 `@xiaochonzi` 反馈。
- ✨ 快照版也打 source jar 方便使用。
- ✨ 添加 renovate bot 方便更新依赖和插件版本。
- ✨ 优化 issue.yml 和 github action。

### v2.3.0 - 2024-05-26
- ✨ mica-mqtt 优化 MqttQoS 枚举，改为 `MqttQoS.QOS0`，方便使用（不兼容）。
- ✨ mica-mqtt-client 同步私服部分功能，支持 stop 完全停止。
- ✨ mica-mqtt-client 同步私服部分功能，MqttClient 都添加了 `schedule`、`scheduleOnce` 方法，（**耗时任务，请务必自定义线程池**）
- ✨ mica-mqtt-server 优化设备离线，简化代码。
- ✨ mica-mqtt-server 用户绑定使用 tio 内置 `Tio.bindUser(context, username)`。
- 🐛 修复 @MqttClientSubscribe 类型错误时的异常提示。
- 🐛 mica-mqtt-client 修复重连可能失败的问题 gitee #I9RI8E 感谢 `@YYGuo` 反馈。

### v2.2.13 - 2024-05-12
- ✨ mica-mqtt-codec MqttVersion 添加版本全名。
- ✨ mica-mqtt-codec MqttConnectReasonCode 添加中文说明。
- 🐛 mica-mqtt-server 保留消息下发时没有订阅也应该先存储 gitee #I9IYX1。

### v2.2.12 - 2024-04-16
- 🐛 mica-mqtt-server 遗嘱消息发送判断

### v2.2.11 - 2024-04-13
- ✨ mica-mqtt-client-spring-boot-starter 简化 MqttClientTemplate 构造，方便自定义。
- ✨ mica-mqtt-client-spring-boot-starter 优化 spring event mqtt client 连接监听。
- ✨ mica-mqtt-client-spring-boot-starter 优化注解订阅。
- 🐛 mqtt-client 修复 mqtt5 props 和遗嘱同时配置时连接编码问题。

### v2.2.10 - 2024-03-23
- ✨ mica-mqtt-client 优化 client publish 时还没有认证的情况。
- ✨ mica-mqtt-client-spring-boot-starter 优化注解订阅，支持 clean session false 重启接收消息。 

### v2.2.9 - 2024-02-25
- ✨ mica-mqtt-server 拦截器 IMqttMessageInterceptor 添加 onAfterConnected 方法，方便在连接时做黑名单等功能。
- ✨ mica-mqtt-client 添加私服版客户端全局订阅功能和添加使用文档。
- 💥 mica-mqtt-common 删除弃用的 ThreadUtil。

### v2.2.8 - 2024-01-19
- ✨ jfinal-mica-mqtt-client 启动改为同步连接。
- 🐛 mica-mqtt-client 修复 `isConnected` 判断。`2.2.7` 中存在此问题。
- ⬆️ 依赖升级

### v2.2.7 - 2024-01-03
- ✨ mica-mqtt-server mqttws开启了ssl后，使用mqtt.js去连接，多刷新几次就会超时 gitee #I8LCMY 
- ✨ mica-mqtt-example 优化 graalvm 配置，感谢 github `@litongjava` 反馈

### v2.2.6 - 2023-11-26
- ✨ mica-mqtt-server 添加 `webConfigCustomize` 支持自定义 http 和 ws 配置，可用于 gitee #I8HF7P
- ✨ mica-mqtt-client 添加连接测试功能 connectTest gitee #I8J35M 感谢 `@彭蕾` 反馈
- ✨ mica-mqtt-example 更新 graalvm 配置

### v2.2.5.1 - 2023-11-01
- 💩 mica-mqtt-client mqttExecutor 方法参数类型漏改。

### v2.2.5 - 2023-10-05
- ✨ mqtt 业务线程池支持自定义设置为 java21虚拟线程。
- ✨ 更新 GitHub action，java17 改为 java21。
- ✨ ThreadUtil 弃用（暂时未删），切换到 mica-net 中的 ThreadUtils。

### v2.2.4 - 2023-09-02
- ✨ 合并去年开源之夏的服务端共享订阅和完善（捐助VIP版采用 topic 树存储，跟 topic 数无关，百万 topic 性能依旧）。
- ✨ 优化 topic 检验
- 🐛 相同 clientId 订阅相同 匹配 topic 应该取最大的qos gitee #I7WWPN

### v2.2.3 - 2023-07-23
- ✨ mqtt server http api publish 不按 clientId 进行路由（无实际意义），而是按 topic，规则改为同 emqx。
- ✨ mqtt server http api publish 触发 onMessage 消息监听。
- ⬆️ 依赖升级

### v2.2.2 - 2023-06-17
- ✨ mica-mqtt-client 心跳包日志受 debug 控制
- ✨ mica-mqtt-broker 的集群改为 redis stream 实现。
- 🐛 修复 starter ssl truststorePass 配置，github #6 感谢 `@zkname` 反馈

### v2.2.1 - 2023-05-28
- ⚡️ mica-mqtt-client 共享订阅更好的兼容 emqx 高版本，gitee #I786GU
- ⬆️ 依赖升级

### v2.2.0 - 2023-05-14
- ✨ MqttPublishMessage payload 参数均由 `ByteBuffer` 改为 `byte[]`，简化代码，方便使用。
- 🐛 修复 高并发场景下取消订阅时报 ConcurrentModificationException github #5 感谢 `@yinyuncan` 反馈

### v2.1.2 - 2023-04-26
- ✨ mica-mqtt-client 支持 `reconnect(String ip, int port)` 转移到其他服务，订阅保留，连接成功时自动重新订阅。感谢 `@powerxie` 反馈
- ✨ 优化 `TopicUtil#getTopicFilter()` topic 占位符替换。
- ✨ 调整 mica-mqtt-client-spring-boot-starter 启动时机。`MqttClientCustomizer` 支持从数据库中获取配置。感谢 `@powerxie` 反馈
- 📝 修复迁移指南**ssl配置**文档错误
- 🐛 修复包长度计算错误，压测下协议解析异常 gitee #I6YOMD 感谢 `@powerxie` 反馈

### v2.1.1 - 2023-04-08
- ✨ mica-mqtt-server http-api 不再强制依赖 `fastjson` 还支持 `Jackson`、`Fastjson2`、`Gson`、`hutool-json` 和自定义, `@皮球` 反馈 gitee #I6O49D。
- ✨ mica-mqtt-codec 删除 `org.dromara.mica.mqtt.codec.ByteBufferUtil`，2.1.0 漏删。
- ✨ mica-mqtt-codec 兼容 qos大于0，messageId == 0，做 qos 降级处理，`@那一刹的容颜` 反馈，详见 gitee #I6PFIH
- ✨ mica-mqtt-codec maxClientIdLength 默认改为 64，gitee #I6P2CG
- ✨ mica-mqtt-client 优化链接时的遗嘱消息构建，默认为 qos0。`@tan90` 反馈 gitee #I6BRBV
- 🐛 mqtt-server 修复 mqtt.js websocket 空包问题，感谢群友反馈。
- 🐛 mqtt-server 修复 websocket mqtt 包长度判断问题。
- ⬆️ 依赖升级

### v2.1.0 - 2023-03-05
- ✨ 【不兼容】调整接口参数，方便使用
- ✨ 【不兼容】底层重构调整
- ✨ 兼容更多 Spring boot 版本，支持 `2.1.0.RELEASE` 以上版本。
- ✨ ssl 支持双向认证 gitee #I61AHJ 感谢 @DoubleH 反馈
- 🐛 修复遗嘱消息判断 gitee #I6BRBV 感谢 @tan90 反馈。
- 🐛 修复错别字 gitee #I6F2PA 感谢 @hpz 反馈
- ⬆️ 依赖升级

### v2.0.3 - 2022-09-18
- ✨ 完善 ssl 方法，方便使用。
- ⬆️ 依赖升级，避免依赖导致的 bug。

### v2.0.2 - 2022-09-13
- 🐛 彻底修复解码异常: `BufferUnderflowException`。

### v2.0.1 - 2022-09-12
- ✨ 优化 MqttWebServer 配置。
- ✨ mica-mqtt-example 添加华为云iot连接示例。
- ✨ mica-mqtt-example 改为使用 tinylog。
- 🐛 修复解码异常: `BufferUnderflowException`。

### v2.0.0 - 2022-09-04
- ✨ mica mqtt server 完善方法，方便使用。
- ✨ 切换到自维护的 java8 t-io，注意：升级了 t-io 部分类名变更。

### v1.3.9 - 2022-08-28
- ✨ mica-mqtt server 添加消息拦截器，gitee #I5KLST
- ✨ mica-mqtt client、server ack 优化和完善，可自定义 ackService。
- ✨ mica-mqtt client stater MqttClientTemplate 完善，统一调整客户端示例。
- ✨ mica-mqtt client 优化客户端心跳和心跳日志优化。
- ✨ mica-mqtt client 订阅代码优化。
- ✨ mica-mqtt codec 代码优化。
- ✨ test 代码优化，更加符合 junit5 规范。
- 🐛 mqtt client Qos2 修复。

### v1.3.8 - 2022-08-11
- ✨ mica-mqtt codec 代码优化。
- ✨ mica-mqtt server 使用 Spring event 解耦消息监听。
- ✨ mica-mqtt client stater，@MqttClientSubscribe topic 支持其他变量 ${productKey} 自动替换成 +。
- 📝 添加演示地址
- 🐛 修复 mica-mqtt client 心跳发送问题。gitee #I5LQXV 感谢 `@iTong` 反馈。

### v1.3.7 - 2022-07-24
- ✨ 添加 mica-mqtt jfinal client 和 server 插件。
- ✨ mica-mqtt server 代码优化，useQueueDecode 默认为 true。 
- ✨ mica-mqtt client 监听回调代码优化。
- 📝 添加赞助，让你我走的更远！！！
- ⬆️ 依赖升级。

### v1.3.6 - 2022-06-25
- ✨ mica-mqtt 统一调整最大的消息体和一次读取的字节数。
- ✨ mica-mqtt client 简化 ssl 开启。
- ✨ mica-mqtt server 添加默认的账号密码配置。
- ⬆️ 依赖升级

### v1.3.4 - 2022-06-06
- ✨ mica-mqtt starter 使用 Spring event 解耦 mqtt client 断连事件。
- ✨ mica-mqtt server `IMqttConnectStatusListener#offline` 方法添加 `reason` 断开原因字段。
- ✨ 添加赞助计划。**捐助共勉，让你我走的更远！！！**
- 🐛 修复 http api 响应问题。

### v1.3.3 - 2022-05-28
- ✨ mica-mqtt 优化线程池。
- ✨ mica-mqtt 添加 Compression 压缩接口。
- ✨ mica-mqtt 添加 kafka TimingWheel 重构 ack。
- ✨ mica-mqtt server 添加 `MqttClusterMessageListener` 方便集群消息处理。
- ✨ mica-mqtt client 优化客户端取消订阅逻辑，gitee #I5779A 感谢 `@杨钊` 同学反馈。
- ⬆️ 升级 fastjson 到 1.2.83。

### v1.3.2 - 2022-05-09
- ✨ mica-mqtt topic 匹配完善。
- ✨ mica-mqtt 订阅、发布时添加 topicFilter、topicName 校验。

### v1.3.1 - 2022-05-08
- ✨ mica-mqtt-broker 默认开启 http 和 basic auth。
- ✨ mica-mqtt server 添加服务端共享订阅接口，方便开源之夏学生参与。
- ✨ mica-mqtt server 添加 IMqttSessionListener。
- ✨ mica-mqtt server publish 保留消息存储。
- ✨ mica-mqtt server 统一 http 响应模型、优化 http 请求判断。
- ✨ mica-mqtt server 优化 MqttHttpRoutes，添加获取所有路由的方法。
- ✨ mica-mqtt server 完善 Result 和 http api。
- ✨ mica-mqtt server http api 添加 endpoints 列表接口。
- ✨ mica-mqtt client 添加同步连接 connectSync 方法。
- ✨ mica-mqtt client 优化 bean 依赖，减少循环依赖可能性。
- 🐛 重构 mqtt topic 匹配规则，提升性能减少内存占用，修复 gitee #I56BTC
- ⬆️ spring boot、mica 版本升级

### v1.3.0 - 2022-04-17
- ✨ mica-mqtt mqtt-server 简化，默认多设备可以直接互相订阅和处理消息。
- ✨ mica-mqtt server、client 添加 `tioConfigCustomize` 方法，方便更大程度的自定义 TioConfig。
- ✨ 拆分 mica-mqtt-client-spring-boot-starter 和 mica-mqtt-server-spring-boot-starter gitee #I4OTC5
- ✨ mica-mqtt-client-spring-boot-example 添加重连动态更新 clientId、username、password 示例。
- ✨ mica-mqtt server 添加根据踢出指定 clientId 的 http api 接口。
- ✨ mica-mqtt server IMqttConnectStatusListener api 调整，添加用户名字段。
- ✨ mica-mqtt server IMqttMessageListener 不再强制要求实现。
- ✨ 使用 netty IntObjectHashMap 优化默认 session 存储。
- ✨ 添加 github action，用于自动构建开发阶段的 SNAPSHOT 版本。
- ✨ 示例项目拆分到 example 目录，mica-mqtt client、server starter 拆分到 starter 目录。
- ⬆️ 依赖升级.

### v1.2.10 - 2022-03-20
- ✨ mica-mqtt server 添加 MQTT 客户端 keepalive 系数 `keepalive-backoff`。
- ✨ mica-mqtt client、server 调整发布的日志级别为 debug。
- ✨ mica-mqtt client 优化 javadoc。
- ✨ mica-mqtt client 重连时，支持重新设置新的鉴权密码。

### v1.2.9 - 2022-03-06
- ✨ mqttServer#publishAll() 日志级别调整 gitee #I4W4IS
- ✨ @MqttClientSubscribe 支持 springboot 配置 gitee #I4UOR3
- ✨ mica-mqtt client 代码优化
- ✨ mica-mqtt-spring-boot-example 拆分

### v1.2.8 - 2022-02-20
- ✨ mica-mqtt server 优化连接 connect 日志。
- ✨ mica-mqtt server 代码优化。
- ✨ mica-mqtt server 添加 statEnable 配置，默认关闭，开启 Prometheus 监控，需要设置为 true。
- ✨ mica-mqtt client 添加 statEnable 配置，默认关闭。
- ✨ mica-mqtt client 优化默认线程池。

### v1.2.7 - 2022-02-13
- ✨ mica-mqtt-spring-boot-starter 完善。
- ✨ mica-mqtt client 考虑一开始就没有连接上服务端的情况。
- ✨ mica-mqtt client 添加 isConnected 方法
- ✨ mica-mqtt client、server connectListener 改为异步
- ✨ mica-mqtt server ChannelContext 添加用户名，使用 (String) context.get(MqttConst.USER_NAME_KEY) 获取。
- ✨ websocket ssl 配置
- ✨ 尝试新版 graalvm
- 🐛 修复多个 mica mqtt client 消息id生成器隔离。

### v1.2.6 - 2022-01-19
- ✨ mica-mqtt-client 支持 `$share`、`$queue` 共享订阅

### v1.2.5 - 2022-01-16
- ✨ mica mqtt server 调整发布权限规则。
- ✨ mica mqtt server 自定义接口的异常处理。
- ✨ mica mqtt server 放开 tio 队列配置。
- ✨ mica mqtt client publish 添加一批 byte[] payload 参数方法。
- ✨ mica-mqtt-model DefaultMessageSerializer 重构，**不兼容**。
- 📝 添加日志，避免遗忘。
- 🐛 http websocket 都不开启并排除 tio-websocket-server 依赖时 gitee  #I4Q3CP

### v1.2.4 - 2022-01-09
- 🔥 mica-mqtt-core 排除一些不需要的依赖。
- 🔥 mica-mqtt-core http websocket 都不开启时，可以排除 tio-websocket-server 依赖。
- ✨ mica-mqtt-core MqttTopicUtil 改名为 TopicUtil。
- ✨ mica-mqtt-spring-boot-starter `@MqttClientSubscribe` 支持 IMqttClientMessageListener bean。
- ✨ mica-mqtt-spring-boot-starter `@MqttClientSubscribe` 支持自定义 MqttClientTemplate Bean。
- ✨ mica-mqtt-spring-boot-starter 完善。
- ✨ mica-mqtt-codec 缩短 mqtt 版本 key。
- 🐛 mica-mqtt-codec 修复 will message。

### v1.2.3 - 2022-01-03
- ✨ mica-mqtt-spring-boot-starter `@MqttClientSubscribe` value 改为数组，支持同时订阅多 topic。
- ✨ mica-mqtt-core 缓存 TopicFilter Pattern。
- ✨ mica-mqtt-core 优化客户端和服务端订阅逻辑 `IMqttServerSubscribeValidator` 接口调整。
- ✨ mica-mqtt client 添加批量订阅。
- ✨ mica-mqtt client 添加批量取消订阅。
- ✨ mica-mqtt client 添加客户端是否断开连接。
- ✨ mica-mqtt client 客户端断开重新订阅时支持配置批次大小。
- 🔖 mica-mqtt client 订阅 `IMqttClientMessageListener` 添加 `onSubscribed` 默认方法。
- ⬆️ mica-mqtt-example 升级 log4j2 到 2.17.1

### v1.2.2 - 2021-12-26
- ✨ mica-mqtt server 添加发布权限接口，无权限直接断开连接，避免高级别 qos 重试浪费资源。
- ✨ mica-mqtt-broker 优化节点信息存储
- ✨ mica-mqtt client 重复订阅优化。感谢 `@一片小雨滴`
- ✨ mica-mqtt client 抽象 IMqttClientSession 接口。
- 🐛 修复重构 AbstractMqttMessageDispatcher 保持跟 mica-mqtt-broker 逻辑一致 gitee #I4MA6A 感谢 `@胡萝博`
- ⬆️ mica-mqtt-example 升级 log4j2 到 2.17.0

### v1.2.1 - 2021-12-11
- ✨ mica-mqtt 优化 topic 匹配。
- ✨ mica-mqtt client disconnect 不再自动重连 gitee #I4L0WK 感谢 `@willianfu`。
- ✨ mica-mqtt client 添加 retryCount 配置 gitee #I4L0WK 感谢 `@willianfu`。
- ✨ mica-mqtt-model message 添加 json 序列化。
- ✨ mica-mqtt-broker 重新梳理逻辑。
- 🐛 mica-mqtt-spring-boot-starter 在 boot 2.6.x 下 bean 循环依赖 gitee #I4LUZP 感谢 `@hongfeng11`。
- 🐛 mica-mqtt server 同一个 clientId 踢出时清除老的 session。
- 🐛 mica-mqtt server 集群下一个 clientId 只允许连接到一台服务器。
- 🐛 mica-mqtt client 修复 IMqttClientConnectListener onDisconnect 空指针。
- 📝 mica-mqtt-model 添加 README.md

### v1.2.0 - 2021-11-28
- ✨ mqtt-mqtt-core client IMqttClientConnectListener 添加 onDisconnect 方法。gitee #I4JT1D 感谢 `@willianfu` 同学反馈。
- ✨ mica-mqtt-core server IMqttMessageListener 接口调整，不兼容老版本。
- ✨ mica-mqtt-broker 调整上下行消息通道。
- ✨ mica-mqtt-broker 添加节点管理。
- ✨ mica-mqtt-broker 调整默认的 Message 序列化方式，不兼容老版本。
- ✨ mica-mqtt-broker 优化设备上下线，处理节点停机的情况。
- ✨ 抽取 mica-mqtt-model 模块，方便后期支持消息桥接，Message 添加默认的消息序列化。 gitee #I4ECEO
- ✨ mica-mqtt-model 完善 Message 消息模型，方便集群。
- 🐛 mica-mqtt-core MqttClient 修复 ssl 没有设置，感谢 `@hjkJOJO` 同学反馈。
- 🐛 修复 websocket mqtt.js 需要拆包 gitee #I4JYJX 感谢 `@Symous` 同学反馈。
- 📝 完善 mica-mqtt-broker README.md，添加二开说明。
- 📝 统一 mica-mqtt server ip 文档。
- 📝 更新 README.md
- ⬆️ 升级 tio 到 3.7.5.v20211028-RELEASE AioDecodeException 改为 TioDecodeException，

### v1.1.4 - 2021-10-16
- ✨ 添加 uniqueId 概念，用来处理 clientId 不唯一的场景。详见：gitee #I4DXQU
- ✨ 微调 `IMqttServerAuthHandler` 认证，添加 uniqueId 参数。

### v1.1.3 - 2021-10-13
- ✨ 状态事件接口 `IMqttConnectStatusListener` 添加 ChannelContext 参数。
- ✨ 从认证中拆分 `IMqttServerSubscribeValidator` 订阅校验接口，添加 ChannelContext、clientId 参数。
- ✨ 认证 `IMqttServerAuthHandler` 调整包、添加 ChannelContext 参数。
- ✨ 完善文档和示例，添加默认端口号说明。
- ⬆️ 依赖升级

### v1.1.2 - 2021-09-12
- ✨ 添加 mica-mqtt-broker 模块，基于 redis pub/sub 实现 mqtt 集群。
- ✨ mica-mqtt-broker 基于 redis 实现客户端状态存储。
- ✨ mica-mqtt-broker 基于 redis 实现遗嘱、保留消息存储。
- ✨ mqtt-server http api 调整订阅和取消订阅，方便集群处理。
- ✨ mica-mqtt-spring-boot-example 添加 mqtt 和 http api 认证示例。
- ✨ 添加 mqtt 5 所有 ReasonCode。
- ✨ 优化解码 PacketNeededLength 计算。
- 🐛 修复遗嘱消息，添加消息类型。
- 🐛 修复 mqtt-server 保留消息匹配规则。

### v1.1.1 - 2021-09-05
- ✨ mqtt-server 优化连接关闭日志。
- ✨ mqtt-server 优化订阅，相同 topicFilter 订阅对 qos 判断。
- ✨ mqtt-server 监听器添加 try catch，避免因业务问题导致连接断开。
- ✨ mqtt-server 优化 topicFilters 校验。
- ✨ mqtt-client 优化订阅 reasonCodes 判断。
- ✨ mqtt-client 监听器添加 try catch，避免因业务问题导致连接断开。
- ✨ mqtt-client 添加 session 有效期。
- ✨ 代码优化，减少 codacy 上的问题。
- 🐛 mqtt-server 修复心跳时间问题。
- 🐛 修复 mqtt-server 多个订阅同时匹配时消息重复的问题。
- 🐛 mqtt-client 优化连接处理的逻辑，mqtt 连接之后再订阅。
- 🐛 修复 MqttProperties 潜在的一个空指针。

### v1.1.0 - 2021-08-29
- ✨ 重构，内置 http，http 和 websocket 公用端口。
- ✨ 添加 mica-core 中的 HexUtil。
- ✨ 添加 PayloadEncode 工具。
- ✨ ServerTioConfig#share 方法添加 groupStat。
- ✨ 考虑使用 udp 多播做集群。
- ✨ MqttServer、MqttServerTemplate 添加 close、getChannelContext 等方法。
- ✨ 重构 MqttServerConfiguration 简化代码。
- ✨ 配置项 `mqtt.server.websocket-port` 改为 `mqtt.server.web-port`。
- 📝 添加 JetBrains 连接。
- 🐛 修复默认的消息转发器逻辑。
- 🐛 修复 websocket 下线无法触发offline gitee #I47K13 感谢 `@willianfu` 同学反馈。 

### v1.0.6 - 2021-08-21
- ✨ 添加订阅 topicFilter 校验。
- ✨ 优化压测工具，更新压测说明，添加 tcp 连接数更改文档地址。
- ✨ mica-mqtt-example 添加多设备交互示例。
- ✨ 优化 mica-mqtt-spring-boot-example。
- ✨ 优化 deploy.sh 脚本。
- 🐛 优化解码异常处理。
- 🐛 修复心跳超时处理。
- ⬆️ 升级 spring boot 到 2.5.4

### v1.0.5 - 2021-08-15
- 🐛 修复编译导致的 java8 运行期间的部分问题，NoSuchMethodError: java.nio.ByteBuffer.xxx

### v1.0.3 - 2021-08-15
- ✨ mica-mqtt server 添加 websocket mqtt 子协议支持（支持 mqtt.js）。
- ✨ mica-mqtt server ip，默认为空，可不设置。
- ✨ mica-mqtt client去除 CountDownLatch 避免启动时未连接上服务端卡住。
- ✨ mica-mqtt client 添加最大包体长度字段，避免超过 8092 长度的包体导致解析异常。
- ✨ mica-mqtt client 添加连接监听 IMqttClientConnectListener。
- ✨ mica-mqtt 3.1 协议会校验 clientId 长度，添加配置项 maxClientIdLength。
- ✨ mica-mqtt 优化 mqtt 解码异常处理。
- ✨ mica-mqtt 日志优化，方便查询。
- ✨ mica-mqtt 代码优化，部分 Tio.close 改为 Tio.remove。
- ✨ mica-mqtt-spring-boot-example 添加 Dockerfile，支持 `spring-boot:build-image`。
- ✨ 完善 mica-mqtt-spring-boot-starter，添加遗嘱消息配置。
- ⬆️ 升级 t-io 到 3.7.4。

### v1.0.3-RC - 2021-08-12
- ✨ 添加 websocket mqtt 子协议支持（支持 mqtt.js）。
- ✨ mqtt 客户端去除 CountDownLatch 避免启动时未连接上服务端卡住。
- ✨ mica-mqtt 服务端 ip，默认为空，可不设置。
- ✨ 完善 mica-mqtt-spring-boot-starter，添加遗嘱消息配置。
- ✨ mqtt 3.1 协议会校验 clientId 长度，添加设置。
- ✨ mqtt 日志优化，方便查询。
- ✨ 代码优化，部分 Tio.close 改为 Tio.remove。
- ⬆️ 升级 t-io 到 3.7.4。

### v1.0.2 - 2021-08-08
- 📝 文档添加集群处理步骤说明，添加遗嘱消息、保留消息的使用场景。
- ✨ 去除演示中的 qos2 参数，性能损耗大避免误用。
- ✨ 遗嘱、保留消息内部消息转发抽象。
- ✨ mqtt server 连接时先判断 clientId 是否存在连接关系，有则先关闭已有连接。
- ✨ 添加 mica-mqtt-spring-boot-example 。感谢 wsq（ @冷月宫主 ）pr。
- ✨ mica-mqtt-spring-boot-starter 支持客户端接入和服务端优化。感谢 wsq（ @冷月宫主 ）pr。
- ✨ mica-mqtt-spring-boot-starter 服务端支持指标收集。可对接 `Prometheus + Grafana` 监控。
- ✨ mqtt server 接受连接时，先判断该 clientId 是否存在其它连接，有则解绑并关闭其他连接。
- ⬆️ 升级 mica-auto 到 2.1.3 修复 ide 多模块增量编译问题。

### v1.0.2-RC - 2021-08-04
- ✨ 添加 mica-mqtt-spring-boot-example 。感谢 wsq（ @冷月宫主 ）pr。
- ✨ mica-mqtt-spring-boot-starter 支持客户端接入和服务端优化。感谢 wsq（ @冷月宫主 ）pr。
- ✨ mica-mqtt-spring-boot-starter 服务端支持指标收集。可对接 `Prometheus + Grafana` 监控。
- ✨ mqtt server 接受连接时，先判断该 clientId 是否存在其它连接，有则解绑并关闭其他连接。

### v1.0.1 - 2021-08-02
- ✨ 订阅管理集成到 session 管理中。
- ✨ MqttProperties.MqttPropertyType 添加注释，考虑 mqtt V5.0 新特性处理。
- ✨ 添加 Spring boot starter 方便接入，兼容低版本 Spring boot。
- ✨ 调研 t-io websocket 子协议。
- 🐛 修复 java8 运行期间的部分问题，NoSuchMethodError: java.nio.ByteBuffer.xxx

### v1.0.1-RC - 2021-07-31
- ✨ 添加 Spring boot starter 方便接入。
- ✨ 调研 t-io websocket 子协议。

### v1.0.0 - 2021-07-29
- ✨ 基于低延迟高性能的 t-io AIO 框架。
- ✨ 支持 MQTT v3.1、v3.1.1 以及 v5.0 协议。
- ✨ 支持 MQTT client 客户端。
- ✨ 支持 MQTT server 服务端。
- ✨ 支持 MQTT 遗嘱消息。
- ✨ 支持 MQTT 保留消息。
- ✨ 支持自定义消息（mq）处理转发实现集群。
- ✨ 支持 GraalVM 编译成本机可执行程序。