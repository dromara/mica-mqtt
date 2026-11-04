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
	GRANTED_QOS0((byte) 0x0, "授权 QoS 0"),
	GRANTED_QOS1((byte) 0x1, "授权 QoS 1"),
	GRANTED_QOS2((byte) 0x2, "授权 QoS 2"),
	UNSPECIFIED_ERROR((byte) 0x80, "未指明的错误"),
	//MQTT5 or higher
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83, "实现特定错误"),
	NOT_AUTHORIZED((byte) 0x87, "未经授权"),
	TOPIC_FILTER_INVALID((byte) 0x8F, "主题过滤器无效"),
	PACKET_IDENTIFIER_IN_USE((byte) 0x91, "报文标识符已被占用"),
	QUOTA_EXCEEDED((byte) 0x97, "超出配额"),
	SHARED_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0x9E, "不支持共享订阅"),
	SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED((byte) 0xA1, "不支持订阅标识符"),
	WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0xA2, "不支持通配符订阅");

	private final byte byteValue;
	private final String message;

	MqttSubAckReasonCode(byte byteValue, String message) {
		this.byteValue = byteValue;
		this.message = message;
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

	@Override
	public byte value() {
		return byteValue;
	}

	public MqttSubAckReasonCode limitForMqttVersion(MqttVersion version) {
		if (version != MqttVersion.MQTT_5 && byteValue > UNSPECIFIED_ERROR.byteValue) {
			return UNSPECIFIED_ERROR;
		} else {
			return this;
		}
	}

	@Override
	public String toString() {
		return "[0x" + String.format("%02X", this.byteValue & 0xFF) + "] " + this.name().toLowerCase().replace('_', ' ') + " (" + this.message + ')';
	}
}
