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

import org.dromara.mica.mqtt.codec.message.MqttConnectMessage;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

/**
 * Variable Header for the {@link MqttConnectMessage}
 *
 * @author netty
 */
public final class MqttConnectVariableHeader {

	private final String name;
	private final int version;
	private final boolean hasUsername;
	private final boolean hasPassword;
	private final boolean isWillRetain;
	private final int willQos;
	private final boolean isWillFlag;
	private final boolean isCleanStart;
	private final int keepAliveTimeSeconds;
	private final MqttProperties properties;

	public MqttConnectVariableHeader(
		String name,
		int version,
		boolean hasUsername,
		boolean hasPassword,
		boolean isWillRetain,
		int willQos,
		boolean isWillFlag,
		boolean isCleanStart,
		int keepAliveTimeSeconds) {
		this(name,
			version,
			hasUsername,
			hasPassword,
			isWillRetain,
			willQos,
			isWillFlag,
			isCleanStart,
			keepAliveTimeSeconds,
			MqttProperties.NO_PROPERTIES);
	}

	public MqttConnectVariableHeader(
		String name,
		int version,
		boolean hasUsername,
		boolean hasPassword,
		boolean isWillRetain,
		int willQos,
		boolean isWillFlag,
		boolean isCleanStart,
		int keepAliveTimeSeconds,
		MqttProperties properties) {
		this.name = name;
		this.version = version;
		this.hasUsername = hasUsername;
		this.hasPassword = hasPassword;
		this.isWillRetain = isWillRetain;
		this.willQos = willQos;
		this.isWillFlag = isWillFlag;
		this.isCleanStart = isCleanStart;
		this.keepAliveTimeSeconds = keepAliveTimeSeconds;
		this.properties = MqttProperties.withEmptyDefaults(properties);
	}

	public String name() {
		return name;
	}

	public int version() {
		return version;
	}

	public boolean hasUsername() {
		return hasUsername;
	}

	public boolean hasPassword() {
		return hasPassword;
	}

	public boolean isWillRetain() {
		return isWillRetain;
	}

	public int willQos() {
		return willQos;
	}

	public boolean isWillFlag() {
		return isWillFlag;
	}

	public boolean isCleanStart() {
		return isCleanStart;
	}

	public int keepAliveTimeSeconds() {
		return keepAliveTimeSeconds;
	}

	public MqttProperties properties() {
		return properties;
	}

	@Override
	public String toString() {
		return "MqttConnectVariableHeader[" +
			"name=" + name +
			", version=" + version +
			", hasUsername=" + hasUsername +
			", hasPassword=" + hasPassword +
			", isWillRetain=" + isWillRetain +
			", isWillFlag=" + isWillFlag +
			", isCleanStart=" + isCleanStart +
			", keepAliveTimeSeconds=" + keepAliveTimeSeconds +
			']';
	}
}
