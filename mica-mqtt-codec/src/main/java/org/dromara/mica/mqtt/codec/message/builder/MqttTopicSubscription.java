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

package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.payload.MqttSubscribePayload;

/**
 * Contains a topic name and Qos Level.
 * This is part of the {@link MqttSubscribePayload}
 *
 * @author netty
 */
public final class MqttTopicSubscription {
	private String topicFilter;
	private final MqttSubscriptionOption option;

	public MqttTopicSubscription(String topicFilter) {
		this(topicFilter, MqttQoS.QOS0);
	}

	public MqttTopicSubscription(String topicFilter, MqttQoS qualityOfService) {
		this(topicFilter, MqttSubscriptionOption.from(qualityOfService));
	}

	public MqttTopicSubscription(String topicFilter, MqttSubscriptionOption option) {
		this.topicFilter = topicFilter;
		this.option = option;
	}

	public String topicFilter() {
		return topicFilter;
	}

	/**
	 * Rewrite topic filter.
	 * Many IoT devices do not support reconfiguration or upgrade, so it is hard to
	 * change their subscribed topics. To resolve this issue, MQTT server may offer
	 * topic rewrite capability.
	 *
	 * @param topicFilter Topic to rewrite to
	 */
	public void setTopicFilter(String topicFilter) {
		this.topicFilter = topicFilter;
	}

	public MqttQoS qualityOfService() {
		return option.qos();
	}

	public MqttSubscriptionOption option() {
		return option;
	}

	@Override
	public String toString() {
		return "MqttTopicSubscription[" +
			"topicFilter=" + topicFilter +
			", option=" + this.option +
			']';
	}
}
