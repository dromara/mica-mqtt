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

package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttSubscribePayload;
import org.dromara.mica.mqtt.codec.message.properties.MqttSubscribeProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * MqttSubscribeMessage builder
 * @author netty, L.cm
 */
public final class MqttSubscribeBuilder {
	private final List<MqttTopicSubscription> subscriptions;
	private int messageId;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	public MqttSubscribeBuilder() {
		subscriptions = new ArrayList<>(5);
	}

	public MqttSubscribeBuilder addSubscription(MqttTopicSubscription subscription) {
		subscriptions.add(subscription);
		return this;
	}

	public MqttSubscribeBuilder addSubscription(MqttQoS qos, String topic) {
		return addSubscription(new MqttTopicSubscription(topic, qos));
	}

	public MqttSubscribeBuilder addSubscription(String topic, MqttSubscriptionOption option) {
		return addSubscription(new MqttTopicSubscription(topic, option));
	}

	public MqttSubscribeBuilder addSubscriptions(Collection<MqttTopicSubscription> subscriptionColl) {
		subscriptions.addAll(subscriptionColl);
		return this;
	}

	public MqttSubscribeBuilder messageId(int messageId) {
		this.messageId = messageId;
		return this;
	}

	public MqttSubscribeBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttSubscribeBuilder properties(Consumer<MqttSubscribeProperties> consumer) {
		MqttSubscribeProperties subscribeProperties = new MqttSubscribeProperties();
		consumer.accept(subscribeProperties);
		return properties(subscribeProperties.getProperties());
	}

	public MqttSubscribeMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.QOS1, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(messageId, properties);
		MqttSubscribePayload mqttSubscribePayload = new MqttSubscribePayload(subscriptions);
		return new MqttSubscribeMessage(mqttFixedHeader, mqttVariableHeader, mqttSubscribePayload);
	}
}
