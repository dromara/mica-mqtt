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

package org.dromara.mica.mqtt.codec.message.header;

import org.dromara.mica.mqtt.codec.properties.MqttProperties;

/**
 * Variable Header containing Message Id and optionally Properties (for MQTT v5)
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#msg-id">MQTTV3.1/msg-id</a>
 *
 * @author netty
 */
public class MqttMessageIdVariableHeader {
	private final int messageId;
	private final MqttProperties properties;

	protected MqttMessageIdVariableHeader(int messageId) {
		this(messageId, MqttProperties.NO_PROPERTIES);
	}

	protected MqttMessageIdVariableHeader(int messageId, MqttProperties properties) {
		if (messageId < 1 || messageId > 0xffff) {
			throw new IllegalArgumentException("messageId: " + messageId + " (expected: 1 ~ 65535)");
		}
		this.messageId = messageId;
		this.properties = MqttProperties.withEmptyDefaults(properties);
	}

	public static MqttMessageIdVariableHeader from(int messageId) {
		return new MqttMessageIdVariableHeader(messageId);
	}

	public static MqttMessageIdVariableHeader from(int messageId, MqttProperties properties) {
		return new MqttMessageIdVariableHeader(messageId, properties);
	}

	public int messageId() {
		return messageId;
	}

	public MqttProperties properties() {
		return properties;
	}

	@Override
	public String toString() {
		return "MqttMessageIdVariableHeader[" +
			"messageId=" + messageId +
			", properties=" + properties +
			']';
	}
}
