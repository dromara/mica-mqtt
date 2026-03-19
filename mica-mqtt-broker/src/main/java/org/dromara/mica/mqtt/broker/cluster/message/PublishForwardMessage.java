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

public class PublishForwardMessage implements BrokerMessage {
	private Message message;

	@Override
	public BrokerMessageType getType() {
		return BrokerMessageType.PUBLISH_FORWARD;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
	}

	@Override
	public byte[] toPayload() {
		if (message == null) {
			return new byte[0];
		}
		return DefaultMessageSerializer.INSTANCE.serialize(message);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		byte[] payload = message.getPayload();
		if (payload != null && payload.length > 0) {
			this.message = DefaultMessageSerializer.INSTANCE.deserialize(payload);
		}
	}

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}
}
