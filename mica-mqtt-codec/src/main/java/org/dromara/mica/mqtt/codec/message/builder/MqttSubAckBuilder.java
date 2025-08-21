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
import org.dromara.mica.mqtt.codec.codes.MqttSubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttSubAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttSubAckPayload;
import org.dromara.mica.mqtt.codec.message.properties.MqttSubAckProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * MqttSubAckMessage builder
 * @author netty, L.cm
 */
public final class MqttSubAckBuilder {
	private final List<MqttSubAckReasonCode> reasonCodes;
	private int packetId;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	MqttSubAckBuilder() {
		reasonCodes = new ArrayList<>();
	}

	public MqttSubAckBuilder packetId(int packetId) {
		this.packetId = packetId;
		return this;
	}

	public MqttSubAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttSubAckBuilder properties(Consumer<MqttSubAckProperties> consumer) {
		MqttSubAckProperties subAckProperties = new MqttSubAckProperties();
		consumer.accept(subAckProperties);
		return properties(subAckProperties.getProperties());
	}

	public MqttSubAckBuilder addGrantedQos(MqttQoS qos) {
		this.reasonCodes.add(MqttSubAckReasonCode.qosGranted(qos));
		return this;
	}

	public MqttSubAckBuilder addReasonCode(MqttSubAckReasonCode reasonCode) {
		this.reasonCodes.add(reasonCode);
		return this;
	}

	public MqttSubAckBuilder addGrantedQoses(MqttQoS... qoses) {
		for (MqttQoS qos : qoses) {
			this.reasonCodes.add(MqttSubAckReasonCode.qosGranted(qos));
		}
		return this;
	}

	public MqttSubAckBuilder addReasonCodes(MqttSubAckReasonCode... reasonCodes) {
		this.reasonCodes.addAll(Arrays.asList(reasonCodes));
		return this;
	}

	public MqttSubAckBuilder addGrantedQosList(List<MqttQoS> qosList) {
		for (MqttQoS qos : qosList) {
			this.reasonCodes.add(MqttSubAckReasonCode.qosGranted(qos));
		}
		return this;
	}

	public MqttSubAckMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.QOS0, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttSubAckVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(packetId, properties);
		// transform to primitive types
		short[] grantedQosArray = new short[this.reasonCodes.size()];
		int i = 0;
		for (MqttSubAckReasonCode reasonCode : this.reasonCodes) {
			grantedQosArray[i++] = reasonCode.value();
		}
		MqttSubAckPayload subAckPayload = new MqttSubAckPayload(grantedQosArray);
		return new MqttSubAckMessage(mqttFixedHeader, mqttSubAckVariableHeader, subAckPayload);
	}
}
