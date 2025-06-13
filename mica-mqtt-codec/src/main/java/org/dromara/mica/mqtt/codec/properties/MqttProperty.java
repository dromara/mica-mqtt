/*
 * Copyright 2020 The Netty Project
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

package org.dromara.mica.mqtt.codec.properties;

/**
 * MQTT property base class
 *
 * @param <T> property type
 */
public abstract class MqttProperty<T> {
	final int propertyId;
	final T value;

	protected MqttProperty(int propertyId, T value) {
		this.propertyId = propertyId;
		this.value = value;
	}

	/**
	 * Get MQTT property ID
	 *
	 * @return property ID
	 */
	public int propertyId() {
		return propertyId;
	}

	/**
	 * Get MQTT property value
	 *
	 * @return property value
	 */
	public T value() {
		return value;
	}

	@Override
	public int hashCode() {
		return propertyId + 31 * value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		MqttProperty that = (MqttProperty) obj;
		return this.propertyId == that.propertyId && this.value.equals(that.value);
	}
}
