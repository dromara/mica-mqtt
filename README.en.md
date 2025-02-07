# 🌐 Dromara mica mqtt
[![Java CI](https://github.com/dromara/mica-mqtt/workflows/Java%20CI/badge.svg)](https://github.com/dromara/mica-mqtt/actions)
![JAVA 8](https://img.shields.io/badge/JDK-1.8+-brightgreen.svg)
[![Mica Maven release](https://img.shields.io/maven-central/v/org.dromara.mica-mqtt/mica-mqtt-codec?style=flat-square)](https://central.sonatype.com/artifact/org.dromara.mica-mqtt/mica-mqtt-codec/versions)
[![GitHub](https://img.shields.io/github/license/dromara/mica-mqtt.svg?style=flat-square)](https://github.com/dromara/mica-mqtt/blob/master/LICENSE)

[![star](https://gitcode.com/dromara/mica-mqtt/star/badge.svg)](https://gitcode.com/dromara/mica-mqtt)
[![star](https://gitee.com/dromara/mica-mqtt/badge/star.svg?theme=dark)](https://gitee.com/dromara/mica-mqtt/stargazers)
[![GitHub Repo stars](https://img.shields.io/github/stars/dromara/mica-mqtt?label=Github%20Stars)](https://github.com/dromara/mica-mqtt)

---

📖English | [📖简体中文](README.md)

Dromara `mica-mqtt` is a **low-latency** and **high-performance** `mqtt` Internet of Things component. For more usage details, please refer to the **mica-mqtt-example** module.

## 🍱 Use Cases

- Internet of Things (cloud-based MQTT broker)
- Internet of Things (edge messaging communication)
- Group IM
- Message push
- Easy-to-use MQTT client

## 🚀 Advantages
- Ordinary yet not monotonous, simple yet not lackluster.
- Manual transmission (more conducive to secondary development or expansion).
- A newborn calf; infinite possibilities.

## ✨ Features
- [x] Support for MQTT v3.1, v3.1.1, and v5.0 protocols.
- [x] Support for WebSocket MQTT sub-protocol (compatible with mqtt.js).
- [x] Support for HTTP REST API, see [HTTP API Documentation](docs/http-api.md) for details.
- [x] Support for MQTT client, support Android native.
- [x] Support for MQTT server, support Android native.
- [x] Support for MQTT Will messages.
- [x] Support for MQTT Retained messages.
- [x] Support for custom message (MQ) processing and forwarding to achieve clustering.
- [x] MQTT client **Alibaba Cloud MQTT**、**HuaWei MQTT** connection demo.
- [x] Support for GraalVM compilation into native executable programs.
- [x] Support for rapid access to Spring Boot、Solon and JFinal projects.
- [x] Spring boot and Solon client plugins support session retention.
- [x] Support for integration with Prometheus + Grafana for monitoring.
- [x] Cluster implementation based on Redis pub/sub, see [mica-mqtt-broker module](mica-mqtt-broker) for details.

## 🌱 To-do

- [ ] Optimize the handling of MQTT server sessions and simplify the use of MQTT v5.0.
- [ ] Implement rule engine based on easy-rule + druid sql parsing.

## 🚨 Default Ports

| Port | Protocol        | Description                      |
| ---- | --------------- | -------------------------------- |
| 1883 | tcp             | MQTT TCP port                    |
| 8083 | http, websocket | HTTP API and WebSocket MQTT port |

**Demo Address**: mqtt.dreamlu.net, same ports，username: mica password: mica

## 📦️ Dependencies

### Spring Boot Project
**Client:**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-client-spring-boot-starter</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**Configuration Details**: [mica-mqtt-client-spring-boot-starter Documentation](starter/mica-mqtt-client-spring-boot-starter/README.md)

**Server:**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-server-spring-boot-starter</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**Configuration Details**: [mica-mqtt-server-spring-boot-starter Documentation](starter/mica-mqtt-server-spring-boot-starter/README.md)

### Non-Spring Boot Project

**Client:**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-client</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**Configuration Details**: [mica-mqtt-client Documentation](mica-mqtt-client/README.md)

**Server:**
```xml
<dependency>
  <groupId>org.dromara.mica-mqtt</groupId>
  <artifactId>mica-mqtt-server</artifactId>
  <version>${mica-mqtt.version}</version>
</dependency>
```

**Configuration Details**: [mica-mqtt-server Documentation](mica-mqtt-server/README.md)

## 📝 Documentation
- [Introduction to MQTT, mqttx, and mica-mqtt **Video**](https://www.bilibili.com/video/BV1wv4y1F7Av/)
- [Getting Started with mica-mqtt](example/README.md)
- [mica-mqtt HTTP API Documentation](docs/http-api.md)
- [Frequently Asked Questions about mica-mqtt Usage](https://gitee.com/596392912/mica-mqtt/issues/I45GO7)
- [mica-mqtt Release Versions](CHANGELOG.md)

## 🏗️ MQTT Client Tools
- [Mqttx: An Elegant Cross-platform MQTT 5.0 Client Tool](https://mqttx.app)

## 🍻 Open Source Recommendations
- `Avue`: A Vue-based configurable front-end framework: [https://gitcode.com/superwei/avue](https://gitcode.com/superwei/avue)
- `Pig`: Microservice framework featured on CCTV (architectural essential): [https://gitcode.com/pig-mesh/pig](https://gitcode.com/pig-mesh/pig)
- `SpringBlade`: Enterprise-level solution (essential for enterprise development): [https://gitcode.com/bladex/SpringBlade](https://gitcode.com/bladex/SpringBlade)

## 📱 WeChat

![DreamLuTech](docs/img/dreamlu-weixin.jpg)

**JAVA Architecture Diary**, daily recommended exciting content!