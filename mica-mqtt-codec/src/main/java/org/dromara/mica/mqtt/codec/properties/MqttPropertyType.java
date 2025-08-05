/*
 * Copyright 2020 The Netty Project
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

package org.dromara.mica.mqtt.codec.properties;

/**
 * mqtt 属性类型
 *
 * @author netty、L.cm
 */
public enum MqttPropertyType {
	// single byte properties
	/**
	 * 有效载荷标识（Payload Format Indicator），该属性只存在于 PUBLISH 报文和 CONNECT 报文的遗嘱属性中。
	 */
	PAYLOAD_FORMAT_INDICATOR((byte) 0x01),
	/**
	 * 请求问题信息
	 */
	REQUEST_PROBLEM_INFORMATION((byte) 0x17),
	/**
	 * 请求响应信息
	 */
	REQUEST_RESPONSE_INFORMATION((byte) 0x19),
	/**
	 * 服务器支持得最高 qos 级别
	 */
	MAXIMUM_QOS((byte) 0x24),
	/**
	 * 保留消息可用
	 */
	RETAIN_AVAILABLE((byte) 0x25),
	/**
	 * 订阅通配符可用
	 */
	WILDCARD_SUBSCRIPTION_AVAILABLE((byte) 0x28),
	/**
	 * 订阅标识符可用
	 */
	SUBSCRIPTION_IDENTIFIER_AVAILABLE((byte) 0x29),
	/**
	 * $share 共享订阅可用
	 */
	SHARED_SUBSCRIPTION_AVAILABLE((byte) 0x2A),

	// two bytes properties
	/**
	 * 服务器 keep alive
	 */
	SERVER_KEEP_ALIVE((byte) 0x13),
	/**
	 * 告知对方自己希望处理未决的最大的 Qos1 或者 Qos2 PUBLISH消息个数，如果不存在，则默认是65535。作用：流控。
	 */
	RECEIVE_MAXIMUM((byte) 0x21),
	/**
	 * topic 别名最大值
	 */
	TOPIC_ALIAS_MAXIMUM((byte) 0x22),
	/**
	 * topic 别名
	 */
	TOPIC_ALIAS((byte) 0x23),

	// four bytes properties
	MESSAGE_EXPIRY_INTERVAL((byte) 0x02),
	/**
	 * session 超时时间，连接时使用
	 */
	SESSION_EXPIRY_INTERVAL((byte) 0x11),
	/**
	 * 遗嘱消息延迟时间
	 */
	WILL_DELAY_INTERVAL((byte) 0x18),
	/**
	 * 最大包体大小
	 */
	MAXIMUM_PACKET_SIZE((byte) 0x27),

	// Variable Byte Integer
	/**
	 * 订阅标识符
	 */
	SUBSCRIPTION_IDENTIFIER((byte) 0x0B),

	// UTF-8 Encoded String properties
	/**
	 * 内容类型（Content Type），只存在于 PUBLISH 报文和 CONNECT 报文的遗嘱属性中。
	 * 例如：存放 MIME 类型，比如 text/plain 表示文本文件，audio/aac 表示音频文件。
	 */
	CONTENT_TYPE((byte) 0x03),
	/**
	 * 响应的 topic
	 */
	RESPONSE_TOPIC((byte) 0x08),
	/**
	 * 指定的客户标识符
	 */
	ASSIGNED_CLIENT_IDENTIFIER((byte) 0x12),
	/**
	 * 身份验证方法
	 */
	AUTHENTICATION_METHOD((byte) 0x15),
	/**
	 * 响应信息
	 */
	RESPONSE_INFORMATION((byte) 0x1A),
	/**
	 * 服务器参考
	 */
	SERVER_REFERENCE((byte) 0x1C),
	/**
	 * 所有的ACK以及DISCONNECT 都可以携带 Reason String属性告知对方一些特殊的信息，
	 * 一般来说是ACK失败的情况下会使用该属性告知对端为什么失败，可用来弥补Reason Code信息不够。
	 */
	REASON_STRING((byte) 0x1F),
	/**
	 * 用户属性
	 */
	USER_PROPERTY((byte) 0x26),

	// Binary Data
	/**
	 * 相关数据
	 */
	CORRELATION_DATA((byte) 0x09),
	/**
	 * 认证数据
	 */
	AUTHENTICATION_DATA((byte) 0x16);

	private static final MqttPropertyType[] VALUES;

	static {
		VALUES = new MqttPropertyType[43];
		for (MqttPropertyType v : values()) {
			VALUES[v.value] = v;
		}
	}

	private final byte value;

	MqttPropertyType(byte value) {
		this.value = value;
	}

	public static MqttPropertyType valueOf(int type) {
		MqttPropertyType t = null;
		try {
			t = VALUES[type];
		} catch (ArrayIndexOutOfBoundsException ignored) {
			// nop
		}
		if (t == null) {
			throw new IllegalArgumentException("unknown property type: " + type);
		}
		return t;
	}

	public byte value() {
		return value;
	}
}
