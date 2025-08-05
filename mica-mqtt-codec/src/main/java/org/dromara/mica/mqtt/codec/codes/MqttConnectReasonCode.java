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

package org.dromara.mica.mqtt.codec.codes;

import org.dromara.mica.mqtt.codec.message.MqttConnAckMessage;

/**
 * Return Code of {@link MqttConnAckMessage}
 *
 * @author netty
 */
public enum MqttConnectReasonCode implements MqttReasonCode {
	CONNECTION_ACCEPTED((byte) 0x00, "连接已接受"),

	// MQTT 3 codes
	CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION((byte) 0X01, "拒绝连接：不可接受的 mqtt 协议版本"),
	CONNECTION_REFUSED_IDENTIFIER_REJECTED((byte) 0x02, "拒绝连接：clientId 标识符被拒绝"),
	CONNECTION_REFUSED_SERVER_UNAVAILABLE((byte) 0x03, "拒绝连接：服务器不可用"),
	CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD((byte) 0x04, "拒绝连接：用户名或密码错误"),
	CONNECTION_REFUSED_NOT_AUTHORIZED((byte) 0x05, "拒绝连接：未经授权"),

	//MQTT 5 codes
	CONNECTION_REFUSED_UNSPECIFIED_ERROR((byte) 0x80, "拒绝连接：未指明的错误"),
	CONNECTION_REFUSED_MALFORMED_PACKET((byte) 0x81, "拒绝连接：报文格式错误"),
	CONNECTION_REFUSED_PROTOCOL_ERROR((byte) 0x82, "拒绝连接：协议错误"),
	CONNECTION_REFUSED_IMPLEMENTATION_SPECIFIC((byte) 0x83, "拒绝连接：实现特定错误"),
	CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION((byte) 0x84, "拒绝连接：不支持的协议版本"),
	CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID((byte) 0x85, "拒绝连接：客户端标识符无效"),
	CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD((byte) 0x86, "拒绝连接：用户名或密码错误"),
	CONNECTION_REFUSED_NOT_AUTHORIZED_5((byte) 0x87, "拒绝连接：未经授权"),
	CONNECTION_REFUSED_SERVER_UNAVAILABLE_5((byte) 0x88, "拒绝连接：服务器不可用"),
	CONNECTION_REFUSED_SERVER_BUSY((byte) 0x89, "拒绝连接：服务器忙"),
	CONNECTION_REFUSED_BANNED((byte) 0x8A, "拒绝连接：被禁止"),
	CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD((byte) 0x8C, "拒绝连接：认证方法错误"),
	CONNECTION_REFUSED_TOPIC_NAME_INVALID((byte) 0x90, "拒绝连接：主题名无效"),
	CONNECTION_REFUSED_PACKET_TOO_LARGE((byte) 0x95, "拒绝连接：报文过大"),
	CONNECTION_REFUSED_QUOTA_EXCEEDED((byte) 0x97, "拒绝连接：超出配额"),
	CONNECTION_REFUSED_PAYLOAD_FORMAT_INVALID((byte) 0x99, "拒绝连接：有效负载格式无效"),
	CONNECTION_REFUSED_RETAIN_NOT_SUPPORTED((byte) 0x9A, "拒绝连接：不支持保留"),
	CONNECTION_REFUSED_QOS_NOT_SUPPORTED((byte) 0x9B, "拒绝连接：不支持服务质量"),
	CONNECTION_REFUSED_USE_ANOTHER_SERVER((byte) 0x9C, "拒绝连接：请使用其他服务器"),
	CONNECTION_REFUSED_SERVER_MOVED((byte) 0x9D, "拒绝连接：服务器已移动"),
	CONNECTION_REFUSED_CONNECTION_RATE_EXCEEDED((byte) 0x9F, "拒绝连接：连接速率超出限制");

	private static final MqttConnectReasonCode[] VALUES = new MqttConnectReasonCode[160];

	static {
		ReasonCodeUtils.fillValuesByCode(VALUES, values());
	}

	private final byte byteValue;
	private final String message;

	MqttConnectReasonCode(byte byteValue, String message) {
		this.byteValue = byteValue;
		this.message = message;
	}

	public static MqttConnectReasonCode valueOf(byte b) {
		return ReasonCodeUtils.codeLoopUp(VALUES, b, "Connect");
	}

	/**
	 * 是否接收
	 *
	 * @return 是否已接受
	 */
	public boolean isAccepted() {
		return CONNECTION_ACCEPTED == this;
	}

	@Override
	public byte value() {
		return byteValue;
	}

	@Override
	public String toString() {
		return this.name().toLowerCase().replace('_', ' ') + " (" + this.message + ')';
	}

}
