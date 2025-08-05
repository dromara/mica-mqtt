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

	public static ConnectBuilder connect() {
		return new ConnectBuilder();
	}

	public static ConnAckBuilder connAck() {
		return new ConnAckBuilder();
	}

	public static MqttPublishMessageBuilder publish() {
		return new MqttPublishMessageBuilder();
	}

	public static SubscribeBuilder subscribe() {
		return new SubscribeBuilder();
	}

	public static UnsubscribeBuilder unsubscribe() {
		return new UnsubscribeBuilder();
	}

	public static PubAckBuilder pubAck() {
		return new PubAckBuilder();
	}

	public static SubAckBuilder subAck() {
		return new SubAckBuilder();
	}

	public static UnsubAckBuilder unsubAck() {
		return new UnsubAckBuilder();
	}

	public static DisconnectBuilder disconnect() {
		return new DisconnectBuilder();
	}

	public static AuthBuilder auth() {
		return new AuthBuilder();
	}
}
