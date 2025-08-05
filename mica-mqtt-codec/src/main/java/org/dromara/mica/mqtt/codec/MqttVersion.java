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

package org.dromara.mica.mqtt.codec;

import org.dromara.mica.mqtt.codec.exception.MqttUnacceptableProtocolVersionException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Mqtt version specific constant values used by multiple classes in mqtt-codec.
 *
 * @author netty
 */
public enum MqttVersion {
	/**
	 * mqtt 协议
	 */
	MQTT_3_1("MQIsdp", (byte) 3, "MQTT 3.1"),
	MQTT_3_1_1("MQTT", (byte) 4, "MQTT 3.1.1"),
	MQTT_5("MQTT", (byte) 5, "MQTT 5.0");

	private final String name;
	private final byte level;
	private final String fullName;

	MqttVersion(String protocolName, byte protocolLevel, String fullName) {
		this.name = Objects.requireNonNull(protocolName, "protocolName is null.");
		this.level = protocolLevel;
		this.fullName = fullName;
	}

	public static MqttVersion fromProtocolNameAndLevel(String protocolName, byte protocolLevel) {
		MqttVersion mv;
		switch (protocolLevel) {
			case 3:
				mv = MQTT_3_1;
				break;
			case 4:
				mv = MQTT_3_1_1;
				break;
			case 5:
				mv = MQTT_5;
				break;
			default:
				throw new MqttUnacceptableProtocolVersionException(protocolName + " is an unknown protocol name");
		}
		if (mv.name.equals(protocolName)) {
			return mv;
		}
		throw new MqttUnacceptableProtocolVersionException(protocolName + " and " + protocolLevel + " don't match");
	}

	public String protocolName() {
		return name;
	}

	public byte[] protocolNameBytes() {
		return name.getBytes(StandardCharsets.UTF_8);
	}

	public byte protocolLevel() {
		return level;
	}

	public String fullName() {
		return fullName;
	}
}
