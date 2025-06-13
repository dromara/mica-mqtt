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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//User properties are the only properties that may be included multiple times and
//are the only properties where ordering is required. Therefore, they need a special handling
public final class UserProperties extends MqttProperty<List<StringPair>> {
	public UserProperties() {
		super(MqttPropertyType.USER_PROPERTY.value(), new ArrayList<>());
	}

	/**
	 * Create user properties from the collection of the String pair values
	 *
	 * @param values string pairs. Collection entries are copied, collection itself isn't shared
	 */
	public UserProperties(Collection<StringPair> values) {
		this();
		this.value.addAll(values);
	}

	public static UserProperties fromUserPropertyCollection(Collection<UserProperty> properties) {
		UserProperties userProperties = new UserProperties();
		for (UserProperty property : properties) {
			userProperties.add(new StringPair(property.value.key, property.value.value));
		}
		return userProperties;
	}

	public void add(StringPair pair) {
		this.value.add(pair);
	}

	public void add(String key, String value) {
		this.value.add(new StringPair(key, value));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("UserProperties(");
		boolean first = true;
		for (StringPair pair : value) {
			if (!first) {
				builder.append(", ");
			}
			builder.append(pair.key).append("->").append(pair.value);
			first = false;
		}
		builder.append(')');
		return builder.toString();
	}
}
