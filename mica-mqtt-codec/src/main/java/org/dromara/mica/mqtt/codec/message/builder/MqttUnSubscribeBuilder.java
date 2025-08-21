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
import org.dromara.mica.mqtt.codec.message.MqttUnSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttUnsubscribePayload;
import org.dromara.mica.mqtt.codec.message.properties.MqttUnSubscribeProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * MqttUnSubscribeMessage builder
 * @author netty, L.cm
 */
public final class MqttUnSubscribeBuilder {
	private final List<String> topicFilters;
	private int messageId;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	MqttUnSubscribeBuilder() {
		topicFilters = new ArrayList<>(5);
	}

	public MqttUnSubscribeBuilder addTopicFilter(String topic) {
		topicFilters.add(topic);
		return this;
	}

	public MqttUnSubscribeBuilder addTopicFilters(Collection<String> topicColl) {
		topicFilters.addAll(topicColl);
		return this;
	}

	public MqttUnSubscribeBuilder messageId(int messageId) {
		this.messageId = messageId;
		return this;
	}

	public MqttUnSubscribeBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttUnSubscribeBuilder properties(Consumer<MqttUnSubscribeProperties> consumer) {
		MqttUnSubscribeProperties unSubscribeProperties = new MqttUnSubscribeProperties();
		consumer.accept(unSubscribeProperties);
		return properties(unSubscribeProperties.getProperties());
	}

	public MqttUnSubscribeMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.QOS1, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(messageId, properties);
		MqttUnsubscribePayload mqttSubscribePayload = new MqttUnsubscribePayload(topicFilters);
		return new MqttUnSubscribeMessage(mqttFixedHeader, mqttVariableHeader, mqttSubscribePayload);
	}
}
