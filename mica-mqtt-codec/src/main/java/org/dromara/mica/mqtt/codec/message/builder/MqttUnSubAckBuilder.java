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
import org.dromara.mica.mqtt.codec.codes.MqttUnSubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttUnSubAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttUnsubAckPayload;
import org.dromara.mica.mqtt.codec.message.properties.MqttUnSubAckProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * MqttUnSubAckMessage builder
 * @author netty, L.cm
 */
public final class MqttUnSubAckBuilder {
	private final List<MqttUnSubAckReasonCode> reasonCodes = new ArrayList<>();
	private int packetId;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	public MqttUnSubAckBuilder() {
	}

	public MqttUnSubAckBuilder packetId(int packetId) {
		this.packetId = packetId;
		return this;
	}

	public MqttUnSubAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttUnSubAckBuilder properties(Consumer<MqttUnSubAckProperties> consumer) {
		MqttUnSubAckProperties unSubAckProperties = new MqttUnSubAckProperties();
		consumer.accept(unSubAckProperties);
		return properties(unSubAckProperties.getProperties());
	}

	public MqttUnSubAckBuilder addReasonCode(short reasonCode) {
		this.reasonCodes.add(MqttUnSubAckReasonCode.values()[reasonCode]);
		return this;
	}

	public MqttUnSubAckBuilder addReasonCode(MqttUnSubAckReasonCode reasonCode) {
		this.reasonCodes.add(reasonCode);
		return this;
	}

	public MqttUnSubAckBuilder addReasonCodes(Short... reasonCodes) {
		for (Short reasonCode : reasonCodes) {
			this.reasonCodes.add(MqttUnSubAckReasonCode.values()[reasonCode]);
		}
		return this;
	}

	public MqttUnSubAckBuilder addReasonCodes(MqttUnSubAckReasonCode... reasonCodes) {
		this.reasonCodes.addAll(Arrays.asList(reasonCodes));
		return this;
	}

	public MqttUnSubAckMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.QOS0, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttSubAckVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(packetId, properties);

		List<Short> reasonCodeValues = new ArrayList<>();
		for (MqttUnSubAckReasonCode reasonCode : reasonCodes) {
			reasonCodeValues.add((short) reasonCode.value());
		}
		MqttUnsubAckPayload subAckPayload = new MqttUnsubAckPayload(reasonCodeValues);
		return new MqttUnSubAckMessage(mqttFixedHeader, mqttSubAckVariableHeader, subAckPayload);
	}
}
