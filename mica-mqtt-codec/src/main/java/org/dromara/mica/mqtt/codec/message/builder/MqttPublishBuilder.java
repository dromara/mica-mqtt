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
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

/**
 * MqttPublishMessage builder
 *
 * @author netty, L.cm
 */
public final class MqttPublishBuilder {
	private String topic;
	private boolean retained;
	private MqttQoS qos;
	private byte[] payload;
	private int messageId;
	private MqttProperties mqttProperties;

	MqttPublishBuilder() {
	}

	public MqttPublishBuilder topicName(String topic) {
		this.topic = topic;
		return this;
	}

	public MqttPublishBuilder retained(boolean retained) {
		this.retained = retained;
		return this;
	}

	public MqttPublishBuilder qos(MqttQoS qos) {
		this.qos = qos;
		return this;
	}

	public MqttPublishBuilder payload(byte[] payload) {
		this.payload = payload;
		return this;
	}

	public MqttPublishBuilder messageId(int messageId) {
		this.messageId = messageId;
		return this;
	}

	public MqttPublishBuilder properties(MqttProperties properties) {
		this.mqttProperties = properties;
		return this;
	}

	public boolean isRetained() {
		return retained;
	}

	public MqttPublishMessage build() {
		MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, retained, 0);
		MqttPublishVariableHeader mqttVariableHeader =
			new MqttPublishVariableHeader(topic, messageId, mqttProperties);
		return new MqttPublishMessage(mqttFixedHeader, mqttVariableHeader, payload);
	}
}
