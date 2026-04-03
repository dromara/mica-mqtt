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

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 连接消息处理器
 *
 * @author L.cm
 */
public class ConnectMessageHandler extends BaseMessageHandler {
	private static final Logger logger = LoggerFactory.getLogger(ConnectMessageHandler.class);
	private final String nodeName;

	public ConnectMessageHandler(MqttServer mqttServer) {
		super(mqttServer);
		this.nodeName = mqttServer.getServerCreator().getNodeName();
	}

	@Override
	public boolean handle(Message message) {
		if (MessageType.CONNECT != message.getMessageType()) {
			return true;
		}

		// 1. 如果一个 clientId 在集群多个服务上连接时断开其他的
		String node = message.getNode();
		if (nodeName.equals(node)) {
			return true;
		}
		String clientId = message.getClientId();
		ChannelContext context = Tio.getByBsId(mqttServer.getServerConfig(), clientId);
		if (context != null) {
			Tio.remove(context, "clientId:[" + clientId + "] now bind on mqtt node:" + node);
		}

		return true;
	}

	@Override
	public int getOrder() {
		return 100; // 连接消息处理优先级高
	}

}
