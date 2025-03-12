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

package org.dromara.mica.mqtt.broker.cluster;

import net.dreamlu.mica.redis.stream.RStreamTemplate;
import org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.serializer.IMessageSerializer;

import java.util.Objects;

/**
 * redis 消息转发器
 *
 * @author L.cm
 */
public class RedisMqttMessageDispatcher implements IMqttMessageDispatcher {
	private final RStreamTemplate streamTemplate;
	private final IMessageSerializer messageSerializer;
	private final String channel;

	public RedisMqttMessageDispatcher(RStreamTemplate streamTemplate,
									  IMessageSerializer messageSerializer,
									  String channel) {
		this.streamTemplate = streamTemplate;
		this.messageSerializer = messageSerializer;
		this.channel = Objects.requireNonNull(channel, "Redis pub/sub channel is null.");
	}

	@Override
	public boolean send(Message message) {
		// 手动序列化和反序列化，避免 redis 序列化不一致问题
		String topic = message.getTopic();
		String key = topic == null ? message.getClientId() : topic;
		long maxLen = 10_0000;
		streamTemplate.send(channel, key, message, messageSerializer::serialize, maxLen);
		return true;
	}

}
