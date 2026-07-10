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
import org.dromara.mica.mqtt.codec.message.properties.MqttPublishProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.function.Consumer;

/**
 * MqttPublishMessage builder
 *
 * @author netty, L.cm
 */
public final class MqttPublishBuilder {
	private String topic;
	private boolean isDup = false;
	private boolean retained;
	private MqttQoS qos;
	private byte[] payload;
	private int messageId;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	public MqttPublishBuilder() {
	}

	public MqttPublishBuilder topicName(String topic) {
		this.topic = topic;
		return this;
	}

	public MqttPublishBuilder isDup(boolean isDup) {
		this.isDup = isDup;
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

	public MqttPublishBuilder messageId(Integer messageId) {
		if (messageId != null) {
			this.messageId = messageId;
		}
		return this;
	}

	public MqttPublishBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttPublishBuilder properties(Consumer<MqttPublishProperties> consumer) {
		MqttPublishProperties publishProperties = new MqttPublishProperties();
		consumer.accept(publishProperties);
		return properties(publishProperties.getProperties());
	}

	public String getTopicName() {
		return topic;
	}

	public boolean isRetained() {
		return retained;
	}

	public MqttQoS getQos() {
		return qos;
	}

	public byte[] getPayload() {
		return payload;
	}

	/**
	 * 构建 PUBLISH 消息。
	 * <p>
	 * 注意：{@link MqttFixedHeader#remainingLength()} 在此处固定写 0，<strong>这不是 bug</strong>。
	 * mica-mqtt 沿用 netty 的设计——Builder 仅做字段装配，<strong>所有字节布局（含 remainingLength 的 VBI 编码）由 {@code MqttEncoder.encodePublishMessage} 在编码时自行计算</strong>，
	 * 该函数只读 fixedHeader 的 type/DUP/QoS/retain 字段，不读 remainingLength。
	 * 重传路径（{@code RetryProcessor} → {@code Tio.send}）同样会经过 encoder，发出的字节流是正确的。
	 * 不要在此处"修复"remainingLength 计算逻辑，否则会偏离上游且无实际收益。
	 *
	 * @return MqttPublishMessage
	 */
	public MqttPublishMessage build() {
		MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, qos, retained, 0);
		MqttPublishVariableHeader mqttVariableHeader =
			new MqttPublishVariableHeader(topic, messageId, properties);
		return new MqttPublishMessage(mqttFixedHeader, mqttVariableHeader, payload);
	}
}
