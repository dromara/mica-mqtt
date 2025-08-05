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

import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

/**
 * Variable Header of the {@link MqttPublishMessage}
 *
 * @author netty
 */
public final class MqttPublishVariableHeader {
	private final String topicName;
	private final int packetId;
	private final MqttProperties properties;

	public MqttPublishVariableHeader(String topicName, int packetId) {
		this(topicName, packetId, MqttProperties.NO_PROPERTIES);
	}

	public MqttPublishVariableHeader(String topicName, int packetId, MqttProperties properties) {
		this.topicName = topicName;
		this.packetId = packetId;
		this.properties = MqttProperties.withEmptyDefaults(properties);
	}

	public String topicName() {
		return topicName;
	}

	public int packetId() {
		return packetId;
	}

	public MqttProperties properties() {
		return properties;
	}

	@Override
	public String toString() {
		return "MqttPublishVariableHeader[" +
			"topicName=" + topicName +
			", packetId=" + packetId +
			']';
	}
}
