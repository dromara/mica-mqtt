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

package org.dromara.mica.mqtt.codec.message.payload;

import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttTopicSubscription;

import java.util.Collections;
import java.util.List;

/**
 * Payload of the {@link MqttSubscribeMessage}
 *
 * @author netty
 */
public final class MqttSubscribePayload {

	private final List<MqttTopicSubscription> topicSubscriptions;

	public MqttSubscribePayload(List<MqttTopicSubscription> topicSubscriptions) {
		this.topicSubscriptions = Collections.unmodifiableList(topicSubscriptions);
	}

	public List<MqttTopicSubscription> topicSubscriptions() {
		return topicSubscriptions;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("MqttSubscribePayload[");
		for (MqttTopicSubscription topicSubscription : topicSubscriptions) {
			builder.append(topicSubscription).append(", ");
		}
		if (!topicSubscriptions.isEmpty()) {
			builder.setLength(builder.length() - 2);
		}
		return builder.append(']').toString();
	}
}
