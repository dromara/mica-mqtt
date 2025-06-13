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

public final class StringProperty extends MqttProperty<String> {

	public StringProperty(MqttPropertyType propertyType, String value) {
		super(propertyType.value(), value);
	}

	public StringProperty(int propertyId, String value) {
		super(propertyId, value);
	}

	@Override
	public String toString() {
		return "StringProperty(" + propertyId + ", " + value + ')';
	}

}
