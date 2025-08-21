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

import org.dromara.mica.mqtt.codec.properties.*;

/**
 * mqtt5 认证属性
 *
 * @author L.cm
 */
public class MqttAuthProperties {
	private final MqttProperties properties;

	public MqttAuthProperties() {
		this(new MqttProperties());
	}

	public MqttAuthProperties(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 设置认证方法
	 *
	 * @param authenticationMethod 认证方法
	 * @return MqttAuthProperty
	 */
	public MqttAuthProperties setAuthenticationMethod(String authenticationMethod) {
		properties.add(new StringProperty(MqttPropertyType.AUTHENTICATION_METHOD, authenticationMethod));
		return this;
	}

	/**
	 * 设置认证数据
	 *
	 * @param authenticationData 认证数据
	 * @return MqttAuthProperty
	 */
	public MqttAuthProperties setAuthenticationData(byte[] authenticationData) {
		properties.add(new BinaryProperty(MqttPropertyType.AUTHENTICATION_DATA, authenticationData));
		return this;
	}

	/**
	 * 设置原因字符串
	 *
	 * @param reasonString 原因字符串
	 * @return MqttAuthProperty
	 */
	public MqttAuthProperties setReasonString(String reasonString) {
		properties.add(new StringProperty(MqttPropertyType.REASON_STRING, reasonString));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttAuthProperty
	 */
	public MqttAuthProperties addUserProperty(UserProperty userProperty) {
		properties.add(userProperty);
		return this;
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 * @return MqttAuthProperty
	 */
	public MqttAuthProperties addUserProperty(String key, String value) {
		this.addUserProperty(new UserProperty(key, value));
		return this;
	}

}
