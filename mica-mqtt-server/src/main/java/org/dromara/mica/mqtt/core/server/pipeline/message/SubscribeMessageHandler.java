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

package org.dromara.mica.mqtt.core.server.pipeline.message;

import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;

/**
 * 订阅消息处理器
 *
 * @author L.cm
 */
public class SubscribeMessageHandler extends BaseMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(SubscribeMessageHandler.class);

	public SubscribeMessageHandler(MqttServer mqttServer) {
		super(mqttServer);
	}

	@Override
	public boolean handle(Message message) {
		if (MessageType.SUBSCRIBE != message.getMessageType()) {
			return true;
		}
		
		String topic = message.getTopic();
		String fromClientId = message.getFromClientId();
		int qos = message.getQos();
		
		// 添加订阅
		sessionManager.addSubscribe(topic, fromClientId, qos);
		
		// 处理集群消息
		ChannelContext context = mqttServer.getChannelContext(fromClientId);
		if (context != null) {
			// 如果是来自其他节点的订阅消息，本地也需要添加订阅
			String node = message.getNode();
			if (node != null && !node.equals(mqttServer.getServerCreator().getNodeName())) {
				sessionManager.addSubscribe(topic, fromClientId, qos);
			}
		}
		
		return true;
	}

	@Override
	public int getOrder() {
		return 100; // 订阅消息处理优先级高
	}

}
