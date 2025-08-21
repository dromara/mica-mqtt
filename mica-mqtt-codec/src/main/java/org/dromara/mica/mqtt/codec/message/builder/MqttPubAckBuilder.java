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
import org.dromara.mica.mqtt.codec.codes.MqttPubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPubReplyMessageVariableHeader;
import org.dromara.mica.mqtt.codec.message.properties.MqttPubAckProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.function.Consumer;

/**
 * MqttPubAckMessage builder
 * @author netty, L.cm
 */
public final class MqttPubAckBuilder {
	private int packetId;
	private MqttPubAckReasonCode reasonCode;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	MqttPubAckBuilder() {
	}

	public MqttPubAckBuilder reasonCode(byte reasonCode) {
		this.reasonCode = MqttPubAckReasonCode.valueOf(reasonCode);
		return this;
	}

	public MqttPubAckBuilder reasonCode(MqttPubAckReasonCode reasonCode) {
		this.reasonCode = reasonCode;
		return this;
	}

	public MqttPubAckBuilder packetId(int packetId) {
		this.packetId = packetId;
		return this;
	}

	public MqttPubAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttPubAckBuilder properties(Consumer<MqttPubAckProperties> consumer) {
		MqttPubAckProperties pubAckProperties = new MqttPubAckProperties();
		consumer.accept(pubAckProperties);
		return properties(pubAckProperties.getProperties());
	}

	public MqttMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.QOS0, false, 0);
		MqttPubReplyMessageVariableHeader mqttPubAckVariableHeader =
			new MqttPubReplyMessageVariableHeader(packetId, reasonCode != null ? reasonCode.value() : 0, properties);
		return new MqttMessage(mqttFixedHeader, mqttPubAckVariableHeader);
	}
}
