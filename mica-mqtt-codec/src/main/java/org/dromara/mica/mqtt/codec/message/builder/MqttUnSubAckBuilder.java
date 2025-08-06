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
import org.dromara.mica.mqtt.codec.message.MqttUnSubAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttUnsubAckPayload;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MqttUnSubAckMessage builder
 * @author netty, L.cm
 */
public final class MqttUnSubAckBuilder {
	private final List<Short> reasonCodes = new ArrayList<>();
	private int packetId;
	private MqttProperties properties;

	MqttUnSubAckBuilder() {
	}

	public MqttUnSubAckBuilder packetId(int packetId) {
		this.packetId = packetId;
		return this;
	}

	public MqttUnSubAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttUnSubAckBuilder addReasonCode(short reasonCode) {
		this.reasonCodes.add(reasonCode);
		return this;
	}

	public MqttUnSubAckBuilder addReasonCodes(Short... reasonCodes) {
		this.reasonCodes.addAll(Arrays.asList(reasonCodes));
		return this;
	}

	public MqttUnSubAckMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.QOS0, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttSubAckVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(packetId, properties);

		MqttUnsubAckPayload subAckPayload = new MqttUnsubAckPayload(reasonCodes);
		return new MqttUnSubAckMessage(mqttFixedHeader, mqttSubAckVariableHeader, subAckPayload);
	}
}
