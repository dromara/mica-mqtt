/*
 * Copyright 2020 The Netty Project
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
 * Variable Header containing, Packet Id and Properties as in MQTT v5 spec.
 *
 * @author netty
 */
public final class MqttMessageIdAndPropertiesVariableHeader extends MqttMessageIdVariableHeader {
	private final MqttProperties properties;

	public MqttMessageIdAndPropertiesVariableHeader(int messageId, MqttProperties properties) {
		super(messageId);
		if (messageId < 1 || messageId > 0xffff) {
			throw new IllegalArgumentException("messageId: " + messageId + " (expected: 1 ~ 65535)");
		}
		this.properties = MqttProperties.withEmptyDefaults(properties);
	}

	public MqttProperties properties() {
		return properties;
	}

	@Override
	public String toString() {
		return "MqttMessageIdAndPropertiesVariableHeader[" +
			"messageId=" + messageId() +
			", properties=" + properties +
			']';
	}

	@Override
	public MqttMessageIdAndPropertiesVariableHeader withDefaultEmptyProperties() {
		return this;
	}
}
