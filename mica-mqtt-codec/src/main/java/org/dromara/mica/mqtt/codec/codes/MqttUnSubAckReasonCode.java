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
public enum MqttUnSubAckReasonCode implements MqttReasonCode {

	/**
	 * UnsubAck ReasonCode
	 */
	SUCCESS((byte) 0x0, "成功"),
	NO_SUBSCRIPTION_EXISTED((byte) 0x11, "订阅不存在"),
	UNSPECIFIED_ERROR((byte) 0x80, "未指明的错误"),
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83, "实现特定错误"),
	NOT_AUTHORIZED((byte) 0x87, "未经授权"),
	TOPIC_FILTER_INVALID((byte) 0x8F, "主题过滤器无效"),
	PACKET_IDENTIFIER_IN_USE((byte) 0x91, "报文标识符已被占用");

	private static final MqttUnSubAckReasonCode[] VALUES = new MqttUnSubAckReasonCode[0x92];

	static {
		ReasonCodeUtils.fillValuesByCode(VALUES, values());
	}

	private final byte byteValue;
	private final String message;

	MqttUnSubAckReasonCode(byte byteValue, String message) {
		this.byteValue = byteValue;
		this.message = message;
	}

	public static MqttUnSubAckReasonCode valueOf(byte b) {
		return ReasonCodeUtils.codeLoopUp(VALUES, b, "UNSUBACK");
	}

	@Override
	public byte value() {
		return byteValue;
	}

	@Override
	public String toString() {
		return "[0x" + String.format("%02X", this.byteValue & 0xFF) + "] " + this.name().toLowerCase().replace('_', ' ') + " (" + this.message + ')';
	}

}
