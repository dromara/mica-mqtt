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

import org.dromara.mica.mqtt.codec.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.*;

/**
 * mqtt5 连接属性
 *
 * @author L.cm
 */
public class MqttConnectProperty {
	private final MqttProperties properties;

	public MqttConnectProperty() {
		this(new MqttProperties());
	}

	public MqttConnectProperty(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 设置会话过期时间
	 *
	 * @param sessionExpiryInterval 会话过期时间
	 */
	public void setSessionExpiryInterval(int sessionExpiryInterval) {
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, sessionExpiryInterval));
	}

	/**
	 * 设置认证方法
	 *
	 * @param authenticationMethod 认证方法
	 */
	public void setAuthenticationMethod(String authenticationMethod) {
		properties.add(new StringProperty(MqttPropertyType.AUTHENTICATION_METHOD, authenticationMethod));
	}

	/**
	 * 设置认证数据
	 *
	 * @param authenticationData 认证数据
	 */
	public void setAuthenticationData(byte[] authenticationData) {
		properties.add(new BinaryProperty(MqttPropertyType.AUTHENTICATION_DATA, authenticationData));
	}

	/**
	 * 设置请求问题信息
	 *
	 * @param requestProblemInformation 请求问题信息
	 */
	public void setRequestProblemInformation(boolean requestProblemInformation) {
		properties.add(new IntegerProperty(MqttPropertyType.REQUEST_PROBLEM_INFORMATION, requestProblemInformation ? 1 : 0));
	}

	/**
	 * 设置请求响应信息
	 *
	 * @param requestResponseInformation 请求响应信息
	 */
	public void setRequestResponseInformation(boolean requestResponseInformation) {
		properties.add(new IntegerProperty(MqttPropertyType.REQUEST_RESPONSE_INFORMATION, requestResponseInformation ? 1 : 0));
	}

	/**
	 * 设置接收最大包数
	 *
	 * @param receiveMaximum 接收最大包数
	 */
	public void setReceiveMaximum(int receiveMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, receiveMaximum));
	}

	/**
	 * 设置主题别名最大数
	 *
	 * @param topicAliasMaximum 主题别名最大数
	 */
	public void setTopicAliasMaximum(int topicAliasMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));
	}

	/**
	 * 设置最大包大小
	 *
	 * @param maximumPacketSize 最大包大小
	 */
	public void setMaximumPacketSize(int maximumPacketSize) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE, maximumPacketSize));
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 */
	public void addUserProperty(UserProperty userProperty) {
		properties.add(userProperty);
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 */
	public void addUserProperty(String key, String value) {
		this.addUserProperty(new UserProperty(key, value));
	}
}
