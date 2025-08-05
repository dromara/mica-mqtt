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
 * MQTT5 CONNACK 属性
 *
 * @author L.cm
 */
public class MqttConnAckProperty {
	private final MqttProperties properties;

	public MqttConnAckProperty() {
		this(new MqttProperties());
	}

	public MqttConnAckProperty(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 设置会话过期间隔
	 *
	 * @param sessionExpiryInterval 会话过期间隔
	 */
	public void setSessionExpiryInterval(int sessionExpiryInterval) {
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, sessionExpiryInterval));
	}

	/**
	 * 设置分配的客户端标识符
	 *
	 * @param assignedClientIdentifier 客户端标识符
	 */
	public void setAssignedClientIdentifier(String assignedClientIdentifier) {
		properties.add(new StringProperty(MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER, assignedClientIdentifier));
	}

	/**
	 * 设置服务器保持连接时间
	 *
	 * @param serverKeepAlive 保持连接时间
	 */
	public void setServerKeepAlive(int serverKeepAlive) {
		properties.add(new IntegerProperty(MqttPropertyType.SERVER_KEEP_ALIVE, serverKeepAlive));
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
	 * 设置响应信息
	 *
	 * @param responseInformation 响应信息
	 */
	public void setResponseInformation(String responseInformation) {
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_INFORMATION, responseInformation));
	}

	/**
	 * 设置服务器引用
	 *
	 * @param serverReference 服务器引用
	 */
	public void setServerReference(String serverReference) {
		properties.add(new StringProperty(MqttPropertyType.SERVER_REFERENCE, serverReference));
	}

	/**
	 * 设置接收最大数量
	 *
	 * @param receiveMaximum 接收最大数量
	 */
	public void setReceiveMaximum(int receiveMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, receiveMaximum));
	}

	/**
	 * 设置主题别名最大值
	 *
	 * @param topicAliasMaximum 主题别名最大值
	 */
	public void setTopicAliasMaximum(int topicAliasMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));
	}

	/**
	 * 设置最大QOS
	 *
	 * @param maximumQos 最大QOS
	 */
	public void setMaximumQos(int maximumQos) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_QOS, maximumQos));
	}

	/**
	 * 设置保留可用标志
	 *
	 * @param retainAvailable 是否保留可用
	 */
	public void setRetainAvailable(boolean retainAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.RETAIN_AVAILABLE, retainAvailable));
	}

	/**
	 * 设置最大数据包大小
	 *
	 * @param maximumPacketSize 最大数据包大小
	 */
	public void setMaximumPacketSize(int maximumPacketSize) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE, maximumPacketSize));
	}

	/**
	 * 设置通配符订阅可用标志
	 *
	 * @param wildcardSubscriptionAvailable 是否通配符订阅可用
	 */
	public void setWildcardSubscriptionAvailable(boolean wildcardSubscriptionAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE, wildcardSubscriptionAvailable));
	}

	/**
	 * 设置订阅标识符可用标志
	 *
	 * @param subscriptionIdentifiersAvailable 是否订阅标识符可用
	 */
	public void setSubscriptionIdentifiersAvailable(boolean subscriptionIdentifiersAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE, subscriptionIdentifiersAvailable));
	}

	/**
	 * 设置共享订阅可用标志
	 *
	 * @param sharedSubscriptionAvailable 是否共享订阅可用
	 */
	public void setSharedSubscriptionAvailable(boolean sharedSubscriptionAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE, sharedSubscriptionAvailable));
	}

	/**
	 * 设置原因字符串
	 *
	 * @param reasonString 原因字符串
	 */
	public void setReasonString(String reasonString) {
		properties.add(new StringProperty(MqttPropertyType.REASON_STRING, reasonString));
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
