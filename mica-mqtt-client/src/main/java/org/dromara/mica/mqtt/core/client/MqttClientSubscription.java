/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption;
import org.dromara.mica.mqtt.codec.message.builder.MqttTopicSubscription;
import org.dromara.mica.mqtt.core.common.TopicFilterType;

import java.io.Serializable;
import java.util.Objects;

/**
 * 发送订阅，未 ack 前的数据承载
 *
 * @author L.cm
 */
public final class MqttClientSubscription implements Serializable {
	private final String topicFilter;
	private final MqttSubscriptionOption option;
	private final TopicFilterType type;
	private final transient IMqttClientMessageListener listener;

	public MqttClientSubscription(String topicFilter,
								  MqttSubscriptionOption option,
								  IMqttClientMessageListener listener) {
		this.topicFilter = Objects.requireNonNull(topicFilter, "MQTT subscribe topicFilter is null.");
		this.option = Objects.requireNonNull(option, "MQTT subscribe option is null.");
		this.type = TopicFilterType.getType(topicFilter);
		this.listener = Objects.requireNonNull(listener, "MQTT subscribe listener is null.");
	}

	public String getTopicFilter() {
		return topicFilter;
	}

	public MqttSubscriptionOption getOption() {
		return option;
	}

	public MqttQoS getMqttQoS() {
		return option.qos();
	}

	public IMqttClientMessageListener getListener() {
		return listener;
	}

	public boolean matches(String topic) {
		return this.type.match(this.topicFilter, topic);
	}

	public MqttTopicSubscription toTopicSubscription() {
		return new MqttTopicSubscription(topicFilter, option);
	}

	@Override
	public boolean equals(Object object) {
		if (object == null || getClass() != object.getClass()) {
			return false;
		}
		MqttClientSubscription that = (MqttClientSubscription) object;
		return topicFilter.equals(that.topicFilter) && Objects.equals(option, that.option) && type == that.type && Objects.equals(listener, that.listener);
	}

	@Override
	public int hashCode() {
		int result = topicFilter.hashCode();
		result = 31 * result + Objects.hashCode(option);
		result = 31 * result + Objects.hashCode(type);
		result = 31 * result + Objects.hashCode(listener);
		return result;
	}

	@Override
	public String toString() {
		return "MqttClientSubscription{" +
			"topicFilter='" + topicFilter + '\'' +
			", option=" + option +
			", type=" + type +
			", listener=" + listener +
			'}';
	}
}
