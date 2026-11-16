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
 * Reason codes for PUBREC MQTT message
 *
 * @author vertx-mqtt
 */
public enum MqttPubRecReasonCode implements MqttReasonCode {

	/**
	 * PubRec ReasonCode
	 */
	SUCCESS((byte) 0x0, "成功"),
	NO_MATCHING_SUBSCRIBERS((byte) 0x10, "没有匹配的订阅者"),
	UNSPECIFIED_ERROR((byte) 0x80, "未指明的错误"),
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83, "实现特定错误"),
	NOT_AUTHORIZED((byte) 0x87, "未经授权"),
	TOPIC_NAME_INVALID((byte) 0x90, "主题名无效"),
	PACKET_IDENTIFIER_IN_USE((byte) 0x91, "报文标识符已被占用"),
	QUOTA_EXCEEDED((byte) 0x97, "超出配额"),
	PAYLOAD_FORMAT_INVALID((byte) 0x99, "有效负载格式无效");

	private static final MqttPubRecReasonCode[] VALUES = new MqttPubRecReasonCode[0x9A];

	static {
		ReasonCodeUtils.fillValuesByCode(VALUES, values());
	}

	private final byte byteValue;
	private final String message;

	MqttPubRecReasonCode(byte byteValue, String message) {
		this.byteValue = byteValue;
		this.message = message;
	}

	public static MqttPubRecReasonCode valueOf(byte b) {
		return ReasonCodeUtils.codeLoopUp(VALUES, b, "PUBREC");
	}

	@Override
	public byte value() {
		return byteValue;
	}

	@Override
	public boolean isError() {
		return Byte.toUnsignedInt(byteValue) >= Byte.toUnsignedInt(UNSPECIFIED_ERROR.byteValue);
	}

	@Override
	public String toString() {
		return "[0x" + String.format("%02X", this.byteValue & 0xFF) + "] " + this.name().toLowerCase().replace('_', ' ') + " (" + this.message + ')';
	}

}
