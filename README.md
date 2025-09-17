# 🌐 Dromara mica mqtt 组件
[![Java CI](https://github.com/dromara/mica-mqtt/workflows/Java%20CI/badge.svg)](https://github.com/dromara/mica-mqtt/actions)
![JAVA 8](https://img.shields.io/badge/JDK-1.8+-brightgreen.svg)
[![Mica Maven release](https://img.shields.io/maven-central/v/org.dromara.mica-mqtt/mica-mqtt-codec?style=flat-square)](https://central.sonatype.com/artifact/org.dromara.mica-mqtt/mica-mqtt-codec/versions)
![Mica Maven SNAPSHOT](https://img.shields.io/maven-metadata/v?metadataUrl=https://central.sonatype.com/repository/maven-snapshots/org/dromara/mica-mqtt/mica-mqtt-codec/maven-metadata.xml)
[![GitHub](https://img.shields.io/github/license/dromara/mica-mqtt.svg?style=flat-square)](https://github.com/dromara/mica-mqtt/blob/master/LICENSE)

[![star](https://gitcode.com/dromara/mica-mqtt/star/badge.svg)](https://gitcode.com/dromara/mica-mqtt)
[![star](https://gitee.com/dromara/mica-mqtt/badge/star.svg?theme=dark)](https://gitee.com/dromara/mica-mqtt/stargazers)
[![GitHub Repo stars](https://img.shields.io/github/stars/dromara/mica-mqtt?label=Github%20Stars)](https://github.com/dromara/mica-mqtt)

---

📖简体中文 | [📖English](README.en.md)

Dromara `mica-mqtt` **低延迟**、**高性能**的 `mqtt` 物联网组件。更多使用方式详见： **mica-mqtt-example** 模块。

✨✨✨**最佳实践**✨✨✨ [**BladeX 物联网平台(「mica-mqtt加强版」+「EMQX+Kafka插件」双架构)**](https://iot.bladex.cn?from=mica-mqtt) 

## 🍱 使用场景

- 物联网（云端 mqtt broker）
- 物联网（边缘端消息通信）
- 群组类 IM
- 消息推送
- 简单易用的 mqtt 客户端

## 🚀 优势
- ✓ 轻如蝉翼 - 核心依赖仅 500KB
- ✓ 新手友好 - 5 分钟极速上手
- ✓ 自由操控 - 手动档设计，扩展随心
- ✓ 潜力无限 - 小而强大，未来可期

## ✨ 功能
- [x] 支持 MQTT v3.1、v3.1.1 以及 v5.0 协议。
- [x] 支持 websocket mqtt 子协议（支持 mqtt.js）。
- [x] 支持 http rest api，[http api 文档详见](docs/http-api.md)。
- [x] 支持 MQTT client 客户端，支持 **Android** 最低要求 API 26（Android 8.0）。
- [x] 支持 MQTT server 服务端，支持 **Android** 最低要求 API 26（Android 8.0）。
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

- [ ] 优化处理 mqtt 服务端 session，以及简化 mqtt v5.0 使用。
- [ ] 基于 easy-rule + druid sql 解析，实现规则引擎。

## 🚨 默认端口

| 端口号   | 协议            | 说明                       |
|-------|---------------|--------------------------|
| 1883  | tcp           | mqtt tcp 端口              |
| 8883  | tcp ssl       | mqtt tcp ssl 端口          |
| 8083  | websocket     | websocket mqtt 子协议端口     |
| 8084  | websocket ssl | websocket ssl mqtt 子协议端口 |
| 18083 | http          | http、大模型 MCP 接口端口        |

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

### Solon 项目
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
- [mica-mqtt 快速开始](https://mica-mqtt.dreamlu.net/guide/)
- [mica-mqtt 使用常见问题汇总](https://mica-mqtt.dreamlu.net/faq/faq.html)
- [mica-mqtt 发行版本](https://mica-mqtt.dreamlu.net/version/changelog.html)
- [mica-mqtt 老版本迁移指南](https://mica-mqtt.dreamlu.net/version/update.html)

## 🍻 开源推荐
- `Avue` 基于 vue 可配置化的前端框架：[https://gitcode.com/superwei/avue](https://gitcode.com/superwei/avue)
- `pig` 上央视的微服务框架（架构必备）：[https://gitcode.com/pig-mesh/pig](https://gitcode.com/pig-mesh/pig)
- `SpringBlade` 企业级解决方案（企业开发必备）：[https://gitcode.com/bladex/SpringBlade](https://gitcode.com/bladex/SpringBlade)

## 📱 微信

![如梦技术](docs/img/dreamlu-weixin.jpg)

**JAVA架构日记**，精彩内容每日推荐！