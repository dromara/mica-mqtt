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
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.pipeline.MqttMessageDeliveryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 下行消息处理器
 *
 * @author L.cm
 */
public class DownStreamMessageHandler extends BaseMessageHandler implements MqttMessageDeliveryHandler {
	private static final Logger logger = LoggerFactory.getLogger(DownStreamMessageHandler.class);

	public DownStreamMessageHandler(MqttServer mqttServer) {
		super(mqttServer);
	}

	@Override
	public boolean handle(Message message) {
		if (MessageType.DOWN_STREAM != message.getMessageType()) {
			return true;
		}
		
		String topic = message.getTopic();

		// 发送到客户端订阅者
		boolean sent = mqttServer.sendToClient(topic, message);
		if (sent) {
			return true;
		}
		// publishAll 在「没有订阅者」和「发送失败」时都返回 false：
		// 前者是正常空操作，后者必须让遗嘱重试路径感知到失败。
		List<Subscribe> subscribers = mqttServer.getServerCreator().getSessionManager().searchSubscribe(topic);
		if (subscribers == null || subscribers.isEmpty()) {
			logger.debug("Down stream message on topic:{} has no subscribers.", topic);
			return true;
		}
		logger.warn("Down stream message on topic:{} failed to send to {} subscribers.", topic, subscribers.size());
		return false;
	}

	@Override
	public int getOrder() {
		return 100; // 下行消息处理优先级高
	}

}
