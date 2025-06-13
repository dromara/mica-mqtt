/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.dromara.mica.mqtt.codec;

/**
 * Mqtt QoS
 *
 * @author netty
 */
public enum MqttQoS {

	/**
	 * QoS level 0 至多发送一次，发送即丢弃。没有确认消息，也不知道对方是否收到。
	 */
	QOS0((byte) 0),
	/**
	 * QoS level 1 至少一次，都要在可变头部中附加一个16位的消息ID，SUBSCRIBE 和 UNSUBSCRIBE 消息使用 QoS level 1。
	 */
	QOS1((byte) 1),
	/**
	 * QoS level 2 确保只有一次，仅仅在 PUBLISH 类型消息中出现，要求在可变头部中要附加消息ID。
	 */
	QOS2((byte) 2),
	/**
	 * 失败
	 */
	FAILURE((byte) 0x80);

	private final byte value;

	MqttQoS(byte value) {
		this.value = value;
	}

	public short value() {
		return (short) (value & 0xFF);
	}

	public static MqttQoS valueOf(int value) {
		switch (value) {
			case 0:
				return QOS0;
			case 1:
				return QOS1;
			case 2:
				return QOS2;
			case 0x80:
				return FAILURE;
			default:
				throw new IllegalArgumentException("invalid QoS: " + value);
		}
	}

	@Override
	public String toString() {
		return "QoS" + value;
	}

}
