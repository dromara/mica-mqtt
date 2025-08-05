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

import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.util.TopicUtil;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * 遗嘱消息
 *
 * @author L.cm
 */
public final class MqttWillMessage {
	private final String topic;
	private final byte[] message;
	/**
	 * 遗嘱消息保留标志
	 */
	private final boolean retain;
	/**
	 * 如果遗嘱标志被设置为 false，遗嘱 QoS 也必须设置为 0。 如果遗嘱标志被设置为 true，遗嘱 QoS 的值可以等于 0，1，2。
	 */
	private final MqttQoS qos;
	/**
	 * mqtt5 willProperties
	 */
	private final MqttProperties willProperties;

	private MqttWillMessage(String topic, byte[] message, boolean retain, MqttQoS qos, MqttProperties willProperties) {
		this.topic = topic;
		this.message = message;
		this.retain = retain;
		this.qos = qos;
		this.willProperties = willProperties;
	}

	public String getTopic() {
		return topic;
	}

	public byte[] getMessage() {
		return message;
	}

	public boolean isRetain() {
		return retain;
	}

	public MqttQoS getQos() {
		return qos;
	}

	public MqttProperties getWillProperties() {
		return willProperties;
	}

	public static MqttWillMessage.Builder builder() {
		return new MqttWillMessage.Builder();
	}

	public static final class Builder {
		private String topic;
		private byte[] message;
		/**
		 * 默认为不保存
		 */
		private boolean retain = false;
		/**
		 * 默认为 qos 0
		 */
		private MqttQoS qos = MqttQoS.QOS0;
		private MqttProperties willProperties;

		public Builder topic(String topic) {
			TopicUtil.validateTopicName(topic);
			this.topic = topic;
			return this;
		}

		public Builder message(byte[] message) {
			this.message = Objects.requireNonNull(message);
			return this;
		}

		public Builder messageText(String message) {
			this.message = Objects.requireNonNull(message).getBytes(StandardCharsets.UTF_8);
			return this;
		}

		public Builder retain(boolean retain) {
			this.retain = retain;
			return this;
		}

		public Builder qos(MqttQoS qos) {
			this.qos = Objects.requireNonNull(qos);
			return this;
		}

		public Builder willProperties(MqttProperties willProperties) {
			this.willProperties = Objects.requireNonNull(willProperties);
			return this;
		}

		public MqttWillMessage build() {
//			有效载荷中必须包含 Will Topic 和 Will Message字段
			Objects.requireNonNull(this.topic, "WillMessage topic is null.");
			Objects.requireNonNull(this.message, "WillMessage message is null.");
			return new MqttWillMessage(this.topic, this.message, this.retain, this.qos, this.willProperties);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MqttWillMessage that = (MqttWillMessage) o;
		return retain == that.retain &&
			Objects.equals(topic, that.topic) &&
			Arrays.equals(message, that.message) &&
			qos == that.qos;
	}

	@Override
	public int hashCode() {
		return Objects.hash(topic, Arrays.hashCode(message), retain, qos);
	}

	@Override
	public String toString() {
		return "MqttWillMessage{" +
			"topic='" + topic + '\'' +
			", message='" + Arrays.toString(message) + '\'' +
			", retain=" + retain +
			", qos=" + qos +
			'}';
	}
}
