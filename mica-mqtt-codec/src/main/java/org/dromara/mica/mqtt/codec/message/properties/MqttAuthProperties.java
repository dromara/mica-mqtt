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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	 * 获取认证方法
	 *
	 * @return 认证方法，如果未设置则返回null
	 */
	public String getAuthenticationMethod() {
		return properties.getPropertyValue(MqttPropertyType.AUTHENTICATION_METHOD);
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
	 * 获取认证数据
	 *
	 * @return 认证数据，如果未设置则返回null
	 */
	public byte[] getAuthenticationData() {
		return properties.getPropertyValue(MqttPropertyType.AUTHENTICATION_DATA);
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
	 * 获取原因字符串
	 *
	 * @return 原因字符串，如果未设置则返回null
	 */
	public String getReasonString() {
		return properties.getPropertyValue(MqttPropertyType.REASON_STRING);
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

	/**
	 * 获取所有用户属性
	 *
	 * @return 用户属性列表，如果未设置则返回空列表
	 */
	public List<UserProperty> getUserProperties() {
		List<UserProperty> userProps = new ArrayList<>();
		for (MqttProperty prop : properties.listAll()) {
			if (prop instanceof UserProperty) {
				userProps.add((UserProperty) prop);
			}
		}
		return userProps;
	}

	/**
	 * 获取所有用户属性
	 *
	 * @return 用户属性Map，如果未设置则返回空Map
	 */
	public Map<String, String> getUserPropertiesMap() {
		Map<String, String> userProps = new HashMap<>();
		for (UserProperty userProp : getUserProperties()) {
			StringPair pair = userProp.value();
			userProps.put(pair.key, pair.value);
		}
		return userProps;
	}

}
