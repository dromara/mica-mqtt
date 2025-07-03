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

package org.dromara.mica.mqtt.proxy;

import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.listener.MqttHttpApiListener;
import org.dromara.mica.mqtt.core.server.listener.MqttProtocolListener;
import org.dromara.mica.mqtt.server.MqttConnectStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.utils.buffer.ByteBufferUtil;

/**
 * mqtt 服务端代理到另外一个服务端
 *
 * @author L.cm
 */
public class MqttServerProxy {
	private static final Logger logger = LoggerFactory.getLogger(MqttServerProxy.class);

	public static void main(String[] args) {
		// 需要将数据发往的服务端
		MqttClient client = MqttClient.create()
			.ip("ip")
			.port(1883)
			.clientId("proxy")
			.username("mica")
			.password("mcia")
			.debug()
			.connectSync();
		// 接受数据的服务端
		MqttServer.create()
			.messageListener((context, clientId, topic, qoS, message) -> {
				byte[] payload = message.getPayload();
				logger.info("clientId:{} topic:{} payload:\n{}", clientId, topic, ByteBufferUtil.toString(payload));
				// 转发数据
				client.publish(topic, payload);
			})
			// 开启 mqtt tcp 协议
			.enableMqtt()
			// 开启 mqtt websocket
			.enableMqttWs()
			// 开启 http api 接口
			.enableMqttHttpApi()
			// 客户端连接状态监听
			.connectStatusListener(new MqttConnectStatusListener())
			// 开始 stat 监控
			.statEnable()
			// 开启 debug 信息日志
			.debug()
			.start();
	}

}
