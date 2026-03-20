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

package org.dromara.mica.mqtt.broker.cluster.message;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.serializer.DefaultMessageSerializer;
import org.tio.server.cluster.message.ClusterDataMessage;

import java.util.Map;

/**
 * 遗嘱消息通知消息
 * <p>
 * 当客户端设置遗嘱消息时，广播通知其他节点备份。
 * 当节点收到客户端遗嘱消息触发通知时，从远程备份恢复。
 * </p>
 *
 * @author L.cm
 */
public class WillMessageNotifyMessage implements BrokerMessage {
	/**
	 * 客户端 ID
	 */
	private String clientId;
	/**
	 * 遗嘱消息
	 */
	private Message willMessage;

	@Override
	public BrokerMessageType getType() {
		return BrokerMessageType.WILL_MESSAGE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(BrokerMessageConverter.HEADER_CLIENT_ID, clientId);
	}

	@Override
	public byte[] toPayload() {
		if (willMessage == null) {
			return new byte[0];
		}
		return DefaultMessageSerializer.INSTANCE.serialize(willMessage);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(BrokerMessageConverter.HEADER_CLIENT_ID);
		byte[] payload = message.getPayload();
		if (payload != null && payload.length > 0) {
			this.willMessage = DefaultMessageSerializer.INSTANCE.deserialize(payload);
		}
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public Message getWillMessage() {
		return willMessage;
	}

	public void setWillMessage(Message willMessage) {
		this.willMessage = willMessage;
	}
}