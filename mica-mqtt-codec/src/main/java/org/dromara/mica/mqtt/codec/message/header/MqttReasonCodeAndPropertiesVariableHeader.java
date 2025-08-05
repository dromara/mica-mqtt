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

import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

/**
 * Variable Header for AUTH and DISCONNECT messages represented by {@link MqttMessage}
 *
 * @author netty
 */
public final class MqttReasonCodeAndPropertiesVariableHeader {
	public static final byte REASON_CODE_OK = 0;
	private final byte reasonCode;
	private final MqttProperties properties;

	public MqttReasonCodeAndPropertiesVariableHeader(byte reasonCode,
													 MqttProperties properties) {
		this.reasonCode = reasonCode;
		this.properties = MqttProperties.withEmptyDefaults(properties);
	}

	public byte reasonCode() {
		return reasonCode;
	}

	public MqttProperties properties() {
		return properties;
	}

	@Override
	public String toString() {
		return "MqttReasonCodeAndPropertiesVariableHeader[" +
			"reasonCode=" + reasonCode +
			", properties=" + properties +
			']';
	}
}
