/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.dromara.mica.mqtt.codec.message;

import org.dromara.mica.mqtt.codec.message.builder.MqttSubAckBuilder;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttSubAckPayload;

/**
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#suback">MQTTV3.1/suback</a>
 *
 * @author netty
 */
public final class MqttSubAckMessage extends MqttMessage {

	public MqttSubAckMessage(
		MqttFixedHeader mqttFixedHeader,
		MqttMessageIdAndPropertiesVariableHeader variableHeader,
		MqttSubAckPayload payload) {
		super(mqttFixedHeader, variableHeader, payload);
	}

	public MqttSubAckMessage(
		MqttFixedHeader mqttFixedHeader,
		MqttMessageIdVariableHeader variableHeader,
		MqttSubAckPayload payload) {
		this(mqttFixedHeader, variableHeader.withDefaultEmptyProperties(), payload);
	}

	@Override
	public MqttMessageIdVariableHeader variableHeader() {
		return (MqttMessageIdVariableHeader) super.variableHeader();
	}

	public MqttMessageIdAndPropertiesVariableHeader idAndPropertiesVariableHeader() {
		return (MqttMessageIdAndPropertiesVariableHeader) super.variableHeader();
	}

	@Override
	public MqttSubAckPayload payload() {
		return (MqttSubAckPayload) super.payload();
	}

	/**
	 * Create a builder for a {@link MqttSubAckMessage}
	 *
	 * @return a new instance of {@link MqttSubAckBuilder}
	 */
	public static MqttSubAckBuilder builder() {
		return new MqttSubAckBuilder();
	}
}
