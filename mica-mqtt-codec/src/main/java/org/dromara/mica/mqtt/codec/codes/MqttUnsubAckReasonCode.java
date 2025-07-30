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
 * Reason codes for UNSUBACK MQTT message
 *
 * @author vertx-mqtt
 */
public enum MqttUnsubAckReasonCode implements MqttReasonCode {

	/**
	 * UnsubAck ReasonCode
	 */
	SUCCESS((byte) 0x0),
	NO_SUBSCRIPTION_EXISTED((byte) 0x11),
	UNSPECIFIED_ERROR((byte) 0x80),
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
	NOT_AUTHORIZED((byte) 0x87),
	TOPIC_FILTER_INVALID((byte) 0x8F),
	PACKET_IDENTIFIER_IN_USE((byte) 0x91);

	private final byte byteValue;

	MqttUnsubAckReasonCode(byte byteValue) {
		this.byteValue = byteValue;
	}

	@Override
	public byte value() {
		return byteValue;
	}

}
