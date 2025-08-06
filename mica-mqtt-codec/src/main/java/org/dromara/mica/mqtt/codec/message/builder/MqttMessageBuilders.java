/*
 * Copyright 2017 The Netty Project
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

package org.dromara.mica.mqtt.codec.message.builder;

/**
 * mqtt 消息构造器
 *
 * @author netty
 */
public final class MqttMessageBuilders {

	private MqttMessageBuilders() {
	}

	public static MqttConnectBuilder connect() {
		return new MqttConnectBuilder();
	}

	public static MqttConnAckBuilder connAck() {
		return new MqttConnAckBuilder();
	}

	public static MqttPublishBuilder publish() {
		return new MqttPublishBuilder();
	}

	public static MqttSubscribeBuilder subscribe() {
		return new MqttSubscribeBuilder();
	}

	public static MqttUnSubscribeBuilder unsubscribe() {
		return new MqttUnSubscribeBuilder();
	}

	public static MqttPubAckBuilder pubAck() {
		return new MqttPubAckBuilder();
	}

	public static MqttSubAckBuilder subAck() {
		return new MqttSubAckBuilder();
	}

	public static MqttUnSubAckBuilder unsubAck() {
		return new MqttUnSubAckBuilder();
	}

	public static MqttDisconnectBuilder disconnect() {
		return new MqttDisconnectBuilder();
	}

	public static MqttAuthBuilder auth() {
		return new MqttAuthBuilder();
	}
}
