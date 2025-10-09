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

import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPublishVariableHeader;

/**
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#publish">MQTTV3.1/publish</a>
 *
 * @author netty
 */
public class MqttPublishMessage extends MqttMessage {
	public MqttPublishMessage(
		MqttFixedHeader mqttFixedHeader,
		MqttPublishVariableHeader variableHeader,
		byte[] payload) {
		super(mqttFixedHeader, variableHeader, payload);
	}

	@Override
	public MqttPublishVariableHeader variableHeader() {
		return (MqttPublishVariableHeader) super.variableHeader();
	}

	@Override
	public byte[] payload() {
		return (byte[]) super.payload();
	}

	public byte[] getPayload() {
		return (byte[]) super.payload();
	}

	/**
	 * Create a builder for a {@link MqttPublishMessage}
	 *
	 * @return a new instance of {@link MqttPublishBuilder}
	 */
	public static MqttPublishBuilder builder() {
		return new MqttPublishBuilder();
	}
}
