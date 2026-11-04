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
	NORMAL((byte) 0x0, "正常断开连接"),
	WITH_WILL_MESSAGE((byte) 0x04, "断开连接：需发送遗嘱消息"),
	UNSPECIFIED_ERROR((byte) 0x80, "未指明的错误"),
	MALFORMED_PACKET((byte) 0x81, "报文格式错误"),
	PROTOCOL_ERROR((byte) 0x82, "协议错误"),
	IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83, "实现特定错误"),
	NOT_AUTHORIZED((byte) 0x87, "未经授权"),
	SERVER_BUSY((byte) 0x89, "服务器忙"),
	SERVER_SHUTTING_DOWN((byte) 0x8B, "服务器正在关闭"),
	KEEP_ALIVE_TIMEOUT((byte) 0x8D, "心跳超时"),
	SESSION_TAKEN_OVER((byte) 0x8E, "会话被接管"),
	TOPIC_FILTER_INVALID((byte) 0x8F, "主题过滤器无效"),
	TOPIC_NAME_INVALID((byte) 0x90, "主题名无效"),
	RECEIVE_MAXIMUM_EXCEEDED((byte) 0x93, "超出最大接收数量"),
	TOPIC_ALIAS_INVALID((byte) 0x94, "主题别名无效"),
	PACKET_TOO_LARGE((byte) 0x95, "报文过大"),
	MESSAGE_RATE_TOO_HIGH((byte) 0x96, "消息速率过高"),
	QUOTA_EXCEEDED((byte) 0x97, "超出配额"),
	ADMINISTRATIVE_ACTION((byte) 0x98, "管理操作"),
	PAYLOAD_FORMAT_INVALID((byte) 0x99, "有效负载格式无效"),
	RETAIN_NOT_SUPPORTED((byte) 0x9A, "不支持保留"),
	QOS_NOT_SUPPORTED((byte) 0x9B, "不支持服务质量"),
	USE_ANOTHER_SERVER((byte) 0x9C, "请使用其他服务器"),
	SERVER_MOVED((byte) 0x9D, "服务器已移动"),
	SHARED_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0x9E, "不支持共享订阅"),
	CONNECTION_RATE_EXCEEDED((byte) 0x9F, "连接速率超出限制"),
	MAXIMUM_CONNECT_TIME((byte) 0xA0, "超出最大连接时间"),
	SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED((byte) 0xA1, "不支持订阅标识符"),
	WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0xA2, "不支持通配符订阅");

	private static final MqttDisconnectReasonCode[] VALUES = new MqttDisconnectReasonCode[0xA3];

	static {
		ReasonCodeUtils.fillValuesByCode(VALUES, values());
	}

	private final byte byteValue;
	private final String message;

	MqttDisconnectReasonCode(byte byteValue, String message) {
		this.byteValue = byteValue;
		this.message = message;
	}

	public static MqttDisconnectReasonCode valueOf(byte b) {
		return ReasonCodeUtils.codeLoopUp(VALUES, b, "DISCONNECT");
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
