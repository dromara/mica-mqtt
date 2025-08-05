/*
 * Copyright 2021 The vertx Project
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

package org.dromara.mica.mqtt.codec.codes;

/**
 * Reason codes for PUBREL MQTT message
 *
 * @author vertx-mqtt
 */
public enum MqttPubRelReasonCode implements MqttReasonCode {

	/**
	 * PubRel ReasonCode
	 */
	SUCCESS((byte) 0x0),
	PACKET_IDENTIFIER_NOT_FOUND((byte) 0x92);

	private final byte byteValue;

	MqttPubRelReasonCode(byte byteValue) {
		this.byteValue = byteValue;
	}

	public static MqttPubRelReasonCode valueOf(byte b) {
		if (b == SUCCESS.byteValue) {
			return SUCCESS;
		} else if (b == PACKET_IDENTIFIER_NOT_FOUND.byteValue) {
			return PACKET_IDENTIFIER_NOT_FOUND;
		} else {
			throw new IllegalArgumentException("unknown PUBREL reason code: " + b);
		}
	}

	@Override
	public byte value() {
		return byteValue;
	}
}
