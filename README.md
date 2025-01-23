# 🌐 Dromara mica mqtt 组件
[![Java CI](https://github.com/dromara/mica-mqtt/workflows/Java%20CI/badge.svg)](https://github.com/dromara/mica-mqtt/actions)
![JAVA 8](https://img.shields.io/badge/JDK-1.8+-brightgreen.svg)
[![Mica Maven release](https://img.shields.io/maven-central/v/org.dromara.mica-mqtt/mica-mqtt-codec?style=flat-square)](https://central.sonatype.com/artifact/org.dromara.mica-mqtt/mica-mqtt-codec/versions)
[![GitHub](https://img.shields.io/github/license/dromara/mica-mqtt.svg?style=flat-square)](https://github.com/dromara/mica-mqtt/blob/master/LICENSE)

[![star](https://gitcode.com/dromara/mica-mqtt/star/badge.svg)](https://gitcode.com/dromara/mica-mqtt)
[![star](https://gitee.com/dromara/mica-mqtt/badge/star.svg?theme=dark)](https://gitee.com/dromara/mica-mqtt/stargazers)
[![GitHub Repo stars](https://img.shields.io/github/stars/dromara/mica-mqtt?label=Github%20Stars)](https://github.com/dromara/mica-mqtt)

---

📖简体中文 | [📖English](README.en.md)

Dromara `mica-mqtt` **低延迟**、**高性能**的 `mqtt` 物联网组件。更多使用方式详见： **mica-mqtt-example** 模块。

✨✨✨**最佳实践**✨✨✨ [**BladeX 物联网平台（基于 mica-mqtt 加强版）**](https://iot.bladex.cn?from=mica-mqtt) 

## 🍱 使用场景

- 物联网（云端 mqtt broker）
- 物联网（边缘端消息通信）
- 群组类 IM
- 消息推送
- 简单易用的 mqtt 客户端

## 🚀 优势
- 平凡却不单调，简单却不失精彩。
- 手动档（更加易于二次开发或扩展）。
- 牛犊初生，无限可能。

## ✨ 功能
- [x] 支持 MQTT v3.1、v3.1.1 以及 v5.0 协议。
- [x] 支持 websocket mqtt 子协议（支持 mqtt.js）。
- [x] 支持 http rest api，[http api 文档详见](docs/http-api.md)。
- [x] 支持 MQTT client 客户端，支持 Android。
- [x] 支持 MQTT server 服务端，支持 Android。
- [x] 支持 MQTT client、server 共享订阅支持。
- [x] 支持 MQTT 遗嘱消息。
- [x] 支持 MQTT 保留消息。
- [x] 支持自定义消息（mq）处理转发实现集群。
- [x] MQTT 客户端 **阿里云 mqtt**、**华为云 mqtt** 连接 demo 示例。
- [x] 支持 GraalVM 编译成本机可执行程序。
- [x] 支持 Spring boot、Solon 和 JFinal 项目快速接入。
- [x] Spring boot、Solon client 插件支持保留 session。
- [x] 支持对接 Prometheus + Grafana 实现监控。
- [x] 基于 redis stream 实现集群，详见 [mica-mqtt-broker 模块](mica-mqtt-broker)。

## 🌱 待办

- [ ] 优化处理 mqtt session，以及支持 v5.0 
- [ ] 基于 easy-rule + druid sql 解析，实现规则引擎。

## 🚨 默认端口

| 端口号 | 协议            | 说明                             |
| ------ | --------------- | -------------------------------- |
| 1883   | tcp             | mqtt tcp 端口                    |
| 8083   | http、websocket | http api 和 websocket mqtt 子协议端口 |

**演示地址**：mqtt.dreamlu.net 端口同上，账号：mica 密码：mica

## 📦️ 依赖

### Spring boot 项目
**客户端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-client-spring-boot-starter</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-client-spring-boot-starter 使用文档](starter/mica-mqtt-client-spring-boot-starter/README.md)

**服务端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-server-spring-boot-starter</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-server-spring-boot-starter 使用文档](starter/mica-mqtt-server-spring-boot-starter/README.md)

### solon 项目
**客户端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-client-solon-plugin</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-client-solon-plugin 使用文档](starter/mica-mqtt-client-solon-plugin/README.md)

**服务端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-server-solon-plugin</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-server-solon-plugin 使用文档](starter/mica-mqtt-server-solon-plugin/README.md)

### JFinal 项目
**客户端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-client-jfinal-plugin</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-client-jfinal-plugin 使用文档](starter/mica-mqtt-client-jfinal-plugin/README.md)

**服务端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-server-jfinal-plugin</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-server-jfinal-plugin 使用文档](starter/mica-mqtt-server-jfinal-plugin/README.md)

### 其他项目

**客户端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-client</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-client 使用文档](mica-mqtt-client/README.md)

**服务端：**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-server</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**配置详见**：[mica-mqtt-server 使用文档](mica-mqtt-server/README.md)

## 📝 文档
- [mqtt科普、mqttx、mica-mqtt的使用**视频**](https://www.bilibili.com/video/BV1wv4y1F7Av/)
- [mica-mqtt 快速开始](example/README.md)
- [mica-mqtt http api 文档详见](docs/http-api.md)
- [mica-mqtt 使用常见问题汇总](https://gitee.com/596392912/mica-mqtt/issues/I45GO7)
- [mica-mqtt 发行版本](CHANGELOG.md)
- [老版本迁移到 mica-mqtt 新版](docs/update.md)
- [mqtt 协议文档](https://github.com/mcxiaoke/mqtt)

## 💡 参考vs借鉴
- [netty codec mqtt](https://github.com/netty/netty/tree/4.1/codec-mqtt)
- [jmqtt](https://github.com/Cicizz/jmqtt)
- [iot-mqtt-server](https://gitee.com/recallcode/iot-mqtt-server)
- [netty-mqtt-client](https://github.com/jetlinks/netty-mqtt-client)

## 🏗️ mqtt 桌面工具
- [mqttx 优雅的跨平台 MQTT 5.0 GUI工具](https://mqttx.app)

## 🍻 开源推荐
- `Avue` 基于 vue 可配置化的前端框架：[https://gitee.com/smallweigit/avue](https://gitee.com/smallweigit/avue)
- `pig` 上央视的微服务框架（架构必备）：[https://gitee.com/log4j/pig](https://gitee.com/log4j/pig)
- `SpringBlade` 企业级解决方案（企业开发必备）：[https://gitee.com/smallc/SpringBlade](https://gitee.com/smallc/SpringBlade)
- `smart-mqtt` 性能极致的 mqtt broker（超越 emqx）：[https://gitee.com/smartboot/smart-mqtt](https://gitee.com/smartboot/smart-mqtt)
- `IJPay` 支付 SDK，让支付触手可及：[https://gitee.com/javen205/IJPay](https://gitee.com/javen205/IJPay)
- `JustAuth` 史上最全的第三方登录开源库: [https://github.com/zhangyd-c/JustAuth](https://github.com/zhangyd-c/JustAuth)
- `spring-boot-demo` Spring boot 深度学习实战: [https://github.com/xkcoding/spring-boot-demo](https://github.com/xkcoding/spring-boot-demo)

## 💚 鸣谢
感谢 JetBrains 提供的免费开源 License：

[![JetBrains](docs/img/jetbrains.png)](https://www.jetbrains.com/?from=mica-mqtt)

## 📱 微信

![如梦技术](docs/img/dreamlu-weixin.jpg)

**JAVA架构日记**，精彩内容每日推荐！