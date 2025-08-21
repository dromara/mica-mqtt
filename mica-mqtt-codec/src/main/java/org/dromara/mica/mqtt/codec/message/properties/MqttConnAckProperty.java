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
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setSessionExpiryInterval(int sessionExpiryInterval) {
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, sessionExpiryInterval));
		return this;
	}

	/**
	 * 设置分配的客户端标识符
	 *
	 * @param assignedClientIdentifier 客户端标识符
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setAssignedClientIdentifier(String assignedClientIdentifier) {
		properties.add(new StringProperty(MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER, assignedClientIdentifier));
		return this;
	}

	/**
	 * 设置服务器保持连接时间
	 *
	 * @param serverKeepAlive 保持连接时间
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setServerKeepAlive(int serverKeepAlive) {
		properties.add(new IntegerProperty(MqttPropertyType.SERVER_KEEP_ALIVE, serverKeepAlive));
		return this;
	}

	/**
	 * 设置认证方法
	 *
	 * @param authenticationMethod 认证方法
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setAuthenticationMethod(String authenticationMethod) {
		properties.add(new StringProperty(MqttPropertyType.AUTHENTICATION_METHOD, authenticationMethod));
		return this;
	}

	/**
	 * 设置认证数据
	 *
	 * @param authenticationData 认证数据
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setAuthenticationData(byte[] authenticationData) {
		properties.add(new BinaryProperty(MqttPropertyType.AUTHENTICATION_DATA, authenticationData));
		return this;
	}

	/**
	 * 设置响应信息
	 *
	 * @param responseInformation 响应信息
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setResponseInformation(String responseInformation) {
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_INFORMATION, responseInformation));
		return this;
	}

	/**
	 * 设置服务器引用
	 *
	 * @param serverReference 服务器引用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setServerReference(String serverReference) {
		properties.add(new StringProperty(MqttPropertyType.SERVER_REFERENCE, serverReference));
		return this;
	}

	/**
	 * 设置接收最大数量
	 *
	 * @param receiveMaximum 接收最大数量
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setReceiveMaximum(int receiveMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, receiveMaximum));
		return this;
	}

	/**
	 * 设置主题别名最大值
	 *
	 * @param topicAliasMaximum 主题别名最大值
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setTopicAliasMaximum(int topicAliasMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));
		return this;
	}

	/**
	 * 设置最大QOS
	 *
	 * @param maximumQos 最大QOS
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setMaximumQos(int maximumQos) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_QOS, maximumQos));
		return this;
	}

	/**
	 * 设置保留可用标志
	 *
	 * @param retainAvailable 是否保留可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setRetainAvailable(boolean retainAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.RETAIN_AVAILABLE, retainAvailable));
		return this;
	}

	/**
	 * 设置最大数据包大小
	 *
	 * @param maximumPacketSize 最大数据包大小
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setMaximumPacketSize(int maximumPacketSize) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE, maximumPacketSize));
		return this;
	}

	/**
	 * 设置通配符订阅可用标志
	 *
	 * @param wildcardSubscriptionAvailable 是否通配符订阅可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setWildcardSubscriptionAvailable(boolean wildcardSubscriptionAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE, wildcardSubscriptionAvailable));
		return this;
	}

	/**
	 * 设置订阅标识符可用标志
	 *
	 * @param subscriptionIdentifiersAvailable 是否订阅标识符可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setSubscriptionIdentifiersAvailable(boolean subscriptionIdentifiersAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE, subscriptionIdentifiersAvailable));
		return this;
	}

	/**
	 * 设置共享订阅可用标志
	 *
	 * @param sharedSubscriptionAvailable 是否共享订阅可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setSharedSubscriptionAvailable(boolean sharedSubscriptionAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE, sharedSubscriptionAvailable));
		return this;
	}

	/**
	 * 设置原因字符串
	 *
	 * @param reasonString 原因字符串
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty setReasonString(String reasonString) {
		properties.add(new StringProperty(MqttPropertyType.REASON_STRING, reasonString));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty addUserProperty(UserProperty userProperty) {
		properties.add(userProperty);
		return this;
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperty addUserProperty(String key, String value) {
		this.addUserProperty(new UserProperty(key, value));
		return this;
	}
}
