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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Payload of {@link MqttConnectMessage}
 *
 * @author netty
 */
public final class MqttConnectPayload {
	private final String clientIdentifier;
	private final MqttProperties willProperties;
	private final String willTopic;
	private final byte[] willMessage;
	private final String username;
	private final byte[] password;

	public MqttConnectPayload(
		String clientIdentifier,
		String willTopic,
		byte[] willMessage,
		String username,
		byte[] password) {
		this(clientIdentifier,
			MqttProperties.NO_PROPERTIES,
			willTopic,
			willMessage,
			username,
			password);
	}

	public MqttConnectPayload(
		String clientIdentifier,
		MqttProperties willProperties,
		String willTopic,
		byte[] willMessage,
		String username,
		byte[] password) {
		this.clientIdentifier = clientIdentifier;
		this.willProperties = MqttProperties.withEmptyDefaults(willProperties);
		this.willTopic = willTopic;
		this.willMessage = willMessage;
		this.username = username;
		this.password = password;
	}

	public String clientIdentifier() {
		return clientIdentifier;
	}

	public MqttProperties willProperties() {
		return willProperties;
	}

	public String willTopic() {
		return willTopic;
	}

	public byte[] willMessageInBytes() {
		return willMessage;
	}

	public String username() {
		return username;
	}

	public byte[] passwordInBytes() {
		return password;
	}

	public String password() {
		return password == null ? null : new String(password, StandardCharsets.UTF_8);
	}

	@Override
	public String toString() {
		return "MqttConnectPayload[" +
			"clientIdentifier=" + clientIdentifier +
			", willTopic=" + willTopic +
			", willMessage=" + Arrays.toString(willMessage) +
			", username=" + username +
			", password=" + Arrays.toString(password) +
			']';
	}
}
