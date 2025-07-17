

# 🌐 Dromara mica mqtt 组件

## 🍱 使用场景
- Spring Boot 项目
- Solon 项目
- JFinal 项目
- 其他 Java 项目

## 🚀 优势
- 箍�이터轻量
- 支持 MQTT 3.1.1 和 MQTT 5.0
- 提供 Spring Boot、Solon、JFinal 等主流框架插件
- 支持自动重连和订阅恢复
- 提供 HTTP API 接口用于消息发布和订阅管理
- 支持 SSL/TLS 加密通信
- 提供连接状态监听和消息拦截机制
- 支持多种序列化/反序列化方式（默认 JSON）

## ✨ 功能
- 客户端连接与断开
- 消息发布与订阅（支持 QoS 0/1/2）
- 共享订阅与取消订阅
- 保留消息处理
- 遗嘱消息设置
- 自定义主题过滤与订阅验证
- 消息统计与监控
- 支持 TCP、WebSocket、SSL/TLS 通信协议
- 支持通过 HTTP API �ention消息发布和订阅管理

## 🌱 待办
- 支持更多序列化方式
- 支持 MQTT over QUIC
- 提供更完善的集群支持
- 支持更多监控指标

## 🚨 默认端口
- MQTT: 1883
- MQTT SSL: 8883
- MQTT WebSocket: 8083
- MQTT WebSocket SSL: 8084
- HTTP API: 8080

## 📦 依赖

### Spring Boot 项目
```xml
<dependency>
	<groupId>org.dromara.mica</groupId>
	<artifactId>mica-mqtt-server-spring-boot-starter</artifactId>
	<version>2.5.0</version>
</dependency>
```

### Solon 项目
```xml
<dependency>
	<groupId>org.dromara.mica</groupId>
	<artifactId>mica-mqtt-server-solon-plugin</artifactId>
	<version>2.5.0</version>
</dependency>
```

### JFinal 项目
```xml
<dependency>
	<groupId>org.dromara.mica</groupId>
	<artifactId>mica-mqtt-server-jfinal-plugin</artifactId>
	<version>2.5.0</version>
</dependency>
```

## 📝 文档
- [HTTP API 文档](docs/http-api.md)
- [GraalVM 支持](docs/graalvm.md)
- [升级指南](docs/update.md)

## 🍻 开源推荐
- [mica](https://gitee.com/dromara/mica)
- [spring-boot-starter](https://gitee.com/dromara/mica-spring-boot-starter)
- [solon-boot-starter](https://gitee.com/dromara/mica-solon-boot-starter)

## 📱 微信
关注 [Dromara](https://gitee.com/dromara) 获取最新动态。