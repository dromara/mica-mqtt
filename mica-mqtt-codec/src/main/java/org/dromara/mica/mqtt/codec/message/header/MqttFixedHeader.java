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

package org.dromara.mica.mqtt.codec.message.header;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;

import java.util.Objects;

/**
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#fixed-header">
 * MQTTV3.1/fixed-header</a>
 *
 * @author netty、L.cm
 */
public final class MqttFixedHeader {

	private final MqttMessageType messageType;
	private volatile boolean isDup;
	private final boolean isRetain;
	private final int headLength;
	private final int remainingLength;
	private MqttQoS qosLevel;

	public MqttFixedHeader(
		MqttMessageType messageType,
		boolean isDup,
		MqttQoS qosLevel,
		boolean isRetain,
		int remainingLength) {
		this(messageType, isDup, qosLevel, isRetain, 0, remainingLength);
	}

	public MqttFixedHeader(
		MqttMessageType messageType,
		boolean isDup,
		MqttQoS qosLevel,
		boolean isRetain,
		int headLength,
		int remainingLength) {
		this.messageType = Objects.requireNonNull(messageType, "messageType is null.");
		this.isDup = isDup;
		this.qosLevel = Objects.requireNonNull(qosLevel, "qosLevel is null.");
		this.isRetain = isRetain;
		this.headLength = headLength;
		this.remainingLength = remainingLength;
	}

	public MqttMessageType messageType() {
		return messageType;
	}

	public boolean isDup() {
		return isDup;
	}

	/**
	 * 设置 {@code DUP} 标识（固定头 bit3）。
	 * <p>
	 * 注意：{@code DUP} 只对 {@code PUBLISH(QoS > 0)} 有意义，
	 * {@code SUBSCRIBE} / {@code UNSUBSCRIBE} / {@code PUBREL} 的 bit3 是保留位必须为 0，
	 * 否则严格校验的 broker（如 mosquitto、netty-codec-mqtt）会按 malformed 断开连接。
	 * 仅供重传场景使用。
	 *
	 * @param dup 是否重传标识
	 * @see <a href="https://gitee.com/dromara/mica-mqtt/issues/IKH0V8">IKH0V8</a>
	 */
	public void setDup(boolean dup) {
		this.isDup = dup;
	}

	public MqttQoS qosLevel() {
		return qosLevel;
	}

	/**
	 * 做 qos 降级，mqtt 规定 qos 大于 0，messageId 必须大于 0，为了兼容，固做降级处理
	 */
	public void downgradeQos() {
		this.qosLevel = MqttQoS.QOS0;
	}

	public boolean isRetain() {
		return isRetain;
	}

	public int headLength() {
		return headLength;
	}

	public int remainingLength() {
		return remainingLength;
	}

	public int getMessageLength() {
		return headLength + remainingLength;
	}

	@Override
	public String toString() {
		return "MqttFixedHeader[" +
			"messageType=" + messageType +
			", isDup=" + isDup +
			", qosLevel=" + qosLevel +
			", isRetain=" + isRetain +
			", remainingLength=" + remainingLength +
			']';
	}
}
