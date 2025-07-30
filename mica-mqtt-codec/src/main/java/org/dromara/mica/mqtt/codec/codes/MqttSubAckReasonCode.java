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

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttVersion;

/**
 * Reason codes for SUBACK MQTT message
 *
 * @author vertx-mqtt
 */
public enum MqttSubAckReasonCode implements MqttReasonCode {

	//All MQTT versions
	GRANTED_QOS0((byte) 0x0),
	GRANTED_QOS1((byte) 0x1),
	GRANTED_QOS2((byte) 0x2),
	UNSPECIFIED_ERROR((byte) 0x80),
	//MQTT5 or higher
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
	NOT_AUTHORIZED((byte) 0x87),
	TOPIC_FILTER_INVALID((byte) 0x8F),
	PACKET_IDENTIFIER_IN_USE((byte) 0x91),
	QUOTA_EXCEEDED((byte) 0x97),
	SHARED_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0x9E),
	SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED((byte) 0xA1),
	WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0xA2);

	private final byte byteValue;

	MqttSubAckReasonCode(byte byteValue) {
		this.byteValue = byteValue;
	}

	@Override
	public byte value() {
		return byteValue;
	}

	public static MqttSubAckReasonCode qosGranted(MqttQoS qos) {
		switch (qos) {
			case QOS0:
				return MqttSubAckReasonCode.GRANTED_QOS0;
			case QOS1:
				return MqttSubAckReasonCode.GRANTED_QOS1;
			case QOS2:
				return MqttSubAckReasonCode.GRANTED_QOS2;
			case FAILURE:
				return MqttSubAckReasonCode.UNSPECIFIED_ERROR;
			default:
				return MqttSubAckReasonCode.UNSPECIFIED_ERROR;
		}
	}

	public MqttSubAckReasonCode limitForMqttVersion(MqttVersion version) {
		if (version != MqttVersion.MQTT_5 && byteValue > UNSPECIFIED_ERROR.byteValue) {
			return UNSPECIFIED_ERROR;
		} else {
			return this;
		}
	}
}
