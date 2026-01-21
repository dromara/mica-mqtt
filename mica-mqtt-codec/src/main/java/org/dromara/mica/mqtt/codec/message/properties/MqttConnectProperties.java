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
 * mqtt5 连接属性
 *
 * @author L.cm
 */
public class MqttConnectProperties {
	private final MqttProperties properties;

	public MqttConnectProperties() {
		this(new MqttProperties());
	}

	public MqttConnectProperties(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 获取会话过期时间
	 *
	 * @return 会话过期时间，如果未设置则返回null
	 */
	public Integer getSessionExpiryInterval() {
		return properties.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL);
	}

	/**
	 * 设置会话过期时间
	 *
	 * @param sessionExpiryInterval 会话过期时间
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setSessionExpiryInterval(int sessionExpiryInterval) {
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, sessionExpiryInterval));
		return this;
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
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setAuthenticationMethod(String authenticationMethod) {
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
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setAuthenticationData(byte[] authenticationData) {
		properties.add(new BinaryProperty(MqttPropertyType.AUTHENTICATION_DATA, authenticationData));
		return this;
	}

	/**
	 * 获取请求问题信息
	 *
	 * @return 请求问题信息，如果未设置则返回null
	 */
	public Boolean getRequestProblemInformation() {
		return properties.getPropertyValue(MqttPropertyType.REQUEST_PROBLEM_INFORMATION);
	}

	/**
	 * 设置请求问题信息
	 *
	 * @param requestProblemInformation 请求问题信息
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setRequestProblemInformation(boolean requestProblemInformation) {
		properties.add(new BooleanProperty(MqttPropertyType.REQUEST_PROBLEM_INFORMATION, requestProblemInformation));
		return this;
	}

	/**
	 * 获取请求响应信息
	 *
	 * @return 请求响应信息，如果未设置则返回null
	 */
	public Boolean getRequestResponseInformation() {
		return properties.getPropertyValue(MqttPropertyType.REQUEST_RESPONSE_INFORMATION);
	}

	/**
	 * 设置请求响应信息
	 *
	 * @param requestResponseInformation 请求响应信息
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setRequestResponseInformation(boolean requestResponseInformation) {
		properties.add(new BooleanProperty(MqttPropertyType.REQUEST_RESPONSE_INFORMATION, requestResponseInformation));
		return this;
	}

	/**
	 * 获取接收最大包数
	 *
	 * @return 接收最大包数，如果未设置则返回null
	 */
	public Integer getReceiveMaximum() {
		return properties.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM);
	}

	/**
	 * 设置接收最大包数
	 *
	 * @param receiveMaximum 接收最大包数
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setReceiveMaximum(int receiveMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, receiveMaximum));
		return this;
	}

	/**
	 * 获取主题别名最大数
	 *
	 * @return 主题别名最大数，如果未设置则返回null
	 */
	public Integer getTopicAliasMaximum() {
		return properties.getPropertyValue(MqttPropertyType.TOPIC_ALIAS_MAXIMUM);
	}

	/**
	 * 设置主题别名最大数
	 *
	 * @param topicAliasMaximum 主题别名最大数
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setTopicAliasMaximum(int topicAliasMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));
		return this;
	}

	/**
	 * 获取最大包大小
	 *
	 * @return 最大包大小，如果未设置则返回null
	 */
	public Integer getMaximumPacketSize() {
		return properties.getPropertyValue(MqttPropertyType.MAXIMUM_PACKET_SIZE);
	}

	/**
	 * 设置最大包大小
	 *
	 * @param maximumPacketSize 最大包大小
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties setMaximumPacketSize(int maximumPacketSize) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE, maximumPacketSize));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties addUserProperty(UserProperty userProperty) {
		properties.add(userProperty);
		return this;
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 * @return MqttConnectProperty
	 */
	public MqttConnectProperties addUserProperty(String key, String value) {
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
