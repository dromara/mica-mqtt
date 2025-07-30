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
 * Reason codes for PUBACK MQTT message
 *
 * @author vertx-mqtt
 */
public enum MqttPubAckReasonCode implements MqttReasonCode {

	/**
	 * PubAck ReasonCode
	 */
	SUCCESS((byte) 0x0),
	NO_MATCHING_SUBSCRIBERS((byte) 0x10),
	UNSPECIFIED_ERROR((byte) 0x80),
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
	NOT_AUTHORIZED((byte) 0x87),
	TOPIC_NAME_INVALID((byte) 0x90),
	PACKET_IDENTIFIER_IN_USE((byte) 0x91),
	QUOTA_EXCEEDED((byte) 0x97),
	PAYLOAD_FORMAT_INVALID((byte) 0x99);

	private static final MqttPubAckReasonCode[] VALUES = new MqttPubAckReasonCode[0x9A];

	static {
		ReasonCodeUtils.fillValuesByCode(VALUES, values());
	}

	private final byte byteValue;

	MqttPubAckReasonCode(byte byteValue) {
		this.byteValue = byteValue;
	}

	public static MqttPubAckReasonCode valueOf(byte b) {
		return ReasonCodeUtils.codeLoopUp(VALUES, b, "PUBACK");
	}

	@Override
	public byte value() {
		return byteValue;
	}

}
