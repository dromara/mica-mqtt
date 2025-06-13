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
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.serializer.IMessageSerializer;
import org.dromara.mica.mqtt.spring.server.MqttServerTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.tio.core.ChannelContext;

import java.util.Objects;

/**
 * redis 消息转发器
 *
 * @author L.cm
 */
public class RedisMqttMessageDispatcher implements IMqttMessageDispatcher, SmartInitializingSingleton {
	private final ApplicationContext context;
	private final RStreamTemplate streamTemplate;
	private final IMessageSerializer messageSerializer;
	private final String channel;
	private MqttServerTemplate mqttServerTemplate;

	public RedisMqttMessageDispatcher(ApplicationContext context,
									  RStreamTemplate streamTemplate,
									  IMessageSerializer messageSerializer,
									  String channel) {
		this.context = context;
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

	@Override
	public void sendRetainMessage(ChannelContext context, String clientId, Message retainMessage) {
		MqttServer mqttServer = mqttServerTemplate.getMqttServer();
		String topic = retainMessage.getTopic();
		byte[] payload = retainMessage.getPayload();
		MqttQoS mqttQoS = MqttQoS.valueOf(retainMessage.getQos());
		boolean retain = retainMessage.isRetain();
		mqttServer.publish(context, clientId, topic, payload, mqttQoS, retain);
	}

	@Override
	public void afterSingletonsInstantiated() {
		mqttServerTemplate = context.getBean(MqttServerTemplate.class);
	}
}
