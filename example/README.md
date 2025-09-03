##  快速开始

**example** 中有 `mqtt` 服务端和客户端演示代码。

也可以只启动**Server**端，**Client**端用客户端工具测试。

### 1. 启动 Server 端

运行  `example/mica-mqtt-server-spring-boot-example/src/main/java/net/dreamlu/iot/mqtt/server/MqttServerApplication.java` 的 `main` 方法

控制台打印如下内容：

```text
2021-07-05 20:42:36,869 INFO  server.TioServer - 
|----------------------------------------------------------------------------------------|
| t-io site         | https://www.tiocloud.com                                           |
| t-io on gitee     | https://gitee.com/tywo45/t-io                                      |
| t-io on github    | https://github.com/tywo45/t-io                                     |
| t-io version      | 3.7.3.v20210706-RELEASE                                            |
| ---------------------------------------------------------------------------------------|
| TioConfig name    | Mica-Mqtt-Server                                                   |
| Started at        | 2021-07-05 20:42:36                                                |
| Listen on         | 127.0.0.1:1883                                                     |
| Main Class        | org.dromara.mica.mqtt.server.MqttServerTest                         |
| Jvm start time    | 2715ms                                                             |
| Tio start time    | 16ms                                                               |
| Pid               | 3588                                                               |
|----------------------------------------------------------------------------------------|

2021-07-05 20:42:37,884 WARN  server.MqttServer - Mqtt publish to all ChannelContext is empty.
```

`Mqtt publish to all ChannelContext is empty.` 通道上下文为空，即没有客户端。

```text
Mica-Mqtt-Server
 ├ 当前时间:1625489086843
 ├ 连接统计
 │ 	 ├ 共接受过连接数  :0
 │ 	 ├ 当前连接数            :0
 │ 	 ├ 异IP连接数           :0
 │ 	 └ 关闭过的连接数  :0
 ├ 消息统计
 │ 	 ├ 已处理消息  :0
 │ 	 ├ 已接收消息(packet/byte):0/0
 │ 	 ├ 已发送消息(packet/byte):0/0b
 │ 	 ├ 平均每次TCP包接收的字节数  :0.0
 │ 	 └ 平均每次TCP包接收的业务包  :0.0
 └ IP统计时段 
   	 └ 没有设置ip统计时间
 ├ 节点统计
 │ 	 ├ clientNodes :0
 │ 	 ├ 所有连接               :0
 │ 	 ├ 绑定user数         :0
 │ 	 ├ 绑定token数       :0
 │ 	 └ 等待同步消息响应 :0
 ├ 群组
 │ 	 └ groupmap:0
 └ 拉黑IP 
   	 └ []
2021-07-05 20:44:46,925 WARN  server.ServerTioConfig - Mica-Mqtt-Server, 检查心跳, 共0个连接, 取锁耗时0ms, 循环耗时71ms, 心跳超时时间:120000ms
```

### 2. 启动 Client 端

运行 `example/mica-mqtt-client-spring-boot-example/src/main/java/net/dreamlu/iot/mqtt/client/MqttClientApplication.java` 的 `main` 方法

控制台打印如下内容，表示客户端连接成功：
```text
2021-07-05 20:46:10,972 ERROR client.TioClient - closeds:0, connections:0
2021-07-05 20:46:10,972 INFO  client.TioClient - [1]: curr:0, closed:0, received:(0p)(0b), handled:0, sent:(0p)(0b)
2021-07-05 20:46:12,566 INFO  client.ConnectionCompletionHandler - connected to 127.0.0.1:1883
2021-07-05 20:46:12,586 INFO  client.MqttClient - MqttClient reconnect send connect result:true
2021-07-05 20:46:12,630 INFO  client.DefaultMqttClientProcessor - MqttClient connection succeeded!
2021-07-05 20:46:13,932 INFO  client.MqttClientTest - /test/123	mica最牛皮
```

此时的 Server 端会打印出如下内容：

```text
2021-07-05 20:46:45,654 INFO  server.MqttServerTest - subscribe:	/test/client	mica最牛皮
2021-07-05 20:46:46,926 WARN  server.ServerTioConfig - 
Mica-Mqtt-Server
 ├ 当前时间:1625489206923
 ├ 连接统计
 │ 	 ├ 共接受过连接数  :1
 │ 	 ├ 当前连接数            :1
 │ 	 ├ 异IP连接数           :1
 │ 	 └ 关闭过的连接数  :0
 ├ 消息统计
 │ 	 ├ 已处理消息  :20
 │ 	 ├ 已接收消息(packet/byte):20/584
 │ 	 ├ 已发送消息(packet/byte):37/935b
 │ 	 ├ 平均每次TCP包接收的字节数  :29.2
 │ 	 └ 平均每次TCP包接收的业务包  :1.0
 └ IP统计时段 
   	 └ 没有设置ip统计时间
 ├ 节点统计
 │ 	 ├ clientNodes :1
 │ 	 ├ 所有连接               :1
 │ 	 ├ 绑定user数         :0
 │ 	 ├ 绑定token数       :0
 │ 	 └ 等待同步消息响应 :0
 ├ 群组
 │ 	 └ groupmap:0
 └ 拉黑IP 
   	 └ []
2021-07-05 20:46:46,926 WARN  server.ServerTioConfig - Mica-Mqtt-Server, 检查心跳, 共1个连接, 取锁耗时0ms, 循环耗时0ms, 心跳超时时间:120000ms
```

### 3. Client 接入 Aliyun MQTT 服务（示例）

详见 `example/mica-mqtt-example/src/main/java/net/dreamlu/iot/mqtt/aliyun/MqttClientTest.java`
