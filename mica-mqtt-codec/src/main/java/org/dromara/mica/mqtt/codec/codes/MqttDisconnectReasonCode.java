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
 * Reason codes for DISCONNECT MQTT message
 *
 * @author vertx-mqtt
 */
public enum MqttDisconnectReasonCode implements MqttReasonCode {

	/**
	 * Disconnect ReasonCode
	 */
	NORMAL((byte) 0x0),
	WITH_WILL_MESSAGE((byte) 0x04),
	UNSPECIFIED_ERROR((byte) 0x80),
	MALFORMED_PACKET((byte) 0x81),
	PROTOCOL_ERROR((byte) 0x82),
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
	NOT_AUTHORIZED((byte) 0x87),
	SERVER_BUSY((byte) 0x89),
	SERVER_SHUTTING_DOWN((byte) 0x8B),
	KEEP_ALIVE_TIMEOUT((byte) 0x8D),
	SESSION_TAKEN_OVER((byte) 0x8E),
	TOPIC_FILTER_INVALID((byte) 0x8F),
	TOPIC_NAME_INVALID((byte) 0x90),
	RECEIVE_MAXIMUM_EXCEEDED((byte) 0x93),
	TOPIC_ALIAS_INVALID((byte) 0x94),
	PACKET_TOO_LARGE((byte) 0x95),
	MESSAGE_RATE_TOO_HIGH((byte) 0x96),
	QUOTA_EXCEEDED((byte) 0x97),
	ADMINISTRATIVE_ACTION((byte) 0x98),
	PAYLOAD_FORMAT_INVALID((byte) 0x99),
	RETAIN_NOT_SUPPORTED((byte) 0x9A),
	QOS_NOT_SUPPORTED((byte) 0x9B),
	USE_ANOTHER_SERVER((byte) 0x9C),
	SERVER_MOVED((byte) 0x9D),
	SHARED_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0x9E),
	CONNECTION_RATE_EXCEEDED((byte) 0x9F),
	MAXIMUM_CONNECT_TIME((byte) 0xA0),
	SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED((byte) 0xA1),
	WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0xA2);

	private static final MqttDisconnectReasonCode[] VALUES = new MqttDisconnectReasonCode[0xA3];

	static {
		ReasonCodeUtils.fillValuesByCode(VALUES, values());
	}

	private final byte byteValue;

	MqttDisconnectReasonCode(byte byteValue) {
		this.byteValue = byteValue;
	}

	public static MqttDisconnectReasonCode valueOf(byte b) {
		return ReasonCodeUtils.codeLoopUp(VALUES, b, "DISCONNECT");
	}

	@Override
	public byte value() {
		return byteValue;
	}
}
