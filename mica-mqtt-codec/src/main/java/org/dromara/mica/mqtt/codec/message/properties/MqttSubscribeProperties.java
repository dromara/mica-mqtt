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

package org.dromara.mica.mqtt.codec.message.properties;

import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.UserProperty;

/**
 * mqtt5 订阅确认属性
 *
 * @author L.cm
 */
public class MqttSubscribeProperties {
	private final MqttProperties properties;

	public MqttSubscribeProperties() {
		this(new MqttProperties());
	}

	public MqttSubscribeProperties(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 设置订阅标识符
	 *
	 * @param subscriptionIdentifier 订阅标识符
	 * @return MqttSubscribeProperties
	 */
	public MqttSubscribeProperties setSubscriptionIdentifier(int subscriptionIdentifier) {
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, subscriptionIdentifier));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttSubscribeProperty
	 */
	public MqttSubscribeProperties addUserProperty(UserProperty userProperty) {
		properties.add(userProperty);
		return this;
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 * @return MqttSubscribeProperty
	 */
	public MqttSubscribeProperties addUserProperty(String key, String value) {
		this.addUserProperty(new UserProperty(key, value));
		return this;
	}
}
