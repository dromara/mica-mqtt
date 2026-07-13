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

/**
 * 取消订阅消息处理器
 *
 * @author L.cm
 */
public class UnsubscribeMessageHandler extends BaseMessageHandler {
	public UnsubscribeMessageHandler(MqttServer mqttServer) {
		super(mqttServer);
	}

	@Override
	public MessageType[] messageTypes() {
		return new MessageType[]{MessageType.UNSUBSCRIBE};
	}

	@Override
	public boolean handle(Message message) {
		String topic = message.getTopic();
		String fromClientId = message.getFromClientId();

		// 集群模式下由 ClusterMqttSessionManager 负责广播，本处理器只需写入一次。
		sessionManager.removeSubscribe(topic, fromClientId);

		return true;
	}

	@Override
	public int getOrder() {
		return 100; // 取消订阅消息处理优先级高
	}

}
