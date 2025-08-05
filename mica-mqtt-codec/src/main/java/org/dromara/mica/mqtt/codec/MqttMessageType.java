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

package org.dromara.mica.mqtt.codec;

/**
 * MQTT Message Types.
 *
 * @author netty
 */
public enum MqttMessageType {
	/**
	 * 连接服务端
	 */
	CONNECT((byte) 1),
	/**
	 * 确认连接请求
	 */
	CONNACK((byte) 2),
	/**
	 * 发布消息
	 */
	PUBLISH((byte) 3),
	/**
	 * 发布确认
	 */
	PUBACK((byte) 4),
	/**
	 * 发布收到（QoS 2，第一步）
	 */
	PUBREC((byte) 5),
	/**
	 * 发布释放（QoS 2，第二步）
	 */
	PUBREL((byte) 6),
	/**
	 * 发布完成（QoS 2，第三步）
	 */
	PUBCOMP((byte) 7),
	/**
	 * 订阅主题
	 */
	SUBSCRIBE((byte) 8),
	/**
	 * 订阅确认
	 */
	SUBACK((byte) 9),
	/**
	 * 取消订阅
	 */
	UNSUBSCRIBE((byte) 10),
	/**
	 * 取消订阅确认
	 */
	UNSUBACK((byte) 11),
	/**
	 * 心跳请求
	 */
	PINGREQ((byte) 12),
	/**
	 * 心跳响应
	 */
	PINGRESP((byte) 13),
	/**
	 * 断开连接
	 */
	DISCONNECT((byte) 14),
	/**
	 * 认证
	 */
	AUTH((byte) 15);

	private static final MqttMessageType[] VALUES;

	static {
		// this prevent values to be assigned with the wrong order
		// and ensure valueOf to work fine
		final MqttMessageType[] values = values();
		VALUES = new MqttMessageType[values.length + 1];
		for (MqttMessageType mqttMessageType : values) {
			final byte value = mqttMessageType.value;
			if (VALUES[value] != null) {
				throw new AssertionError("value already in use: " + value);
			}
			VALUES[value] = mqttMessageType;
		}
	}

	private final byte value;

	MqttMessageType(byte value) {
		this.value = value;
	}

	public static MqttMessageType valueOf(int type) {
		if (type <= 0 || type >= VALUES.length) {
			throw new IllegalArgumentException("unknown message type: " + type);
		}
		return VALUES[type];
	}

	public byte value() {
		return value;
	}
}

