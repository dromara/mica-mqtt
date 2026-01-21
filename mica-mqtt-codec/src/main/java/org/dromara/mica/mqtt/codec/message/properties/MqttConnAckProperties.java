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
 * MQTT5 CONNACK 属性
 *
 * @author L.cm
 */
public class MqttConnAckProperties {
	private final MqttProperties properties;

	public MqttConnAckProperties() {
		this(new MqttProperties());
	}

	public MqttConnAckProperties(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 获取会话过期间隔
	 *
	 * @return 会话过期间隔，如果未设置则返回null
	 */
	public Integer getSessionExpiryInterval() {
		return properties.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL);
	}

	/**
	 * 设置会话过期间隔
	 *
	 * @param sessionExpiryInterval 会话过期间隔
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setSessionExpiryInterval(int sessionExpiryInterval) {
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, sessionExpiryInterval));
		return this;
	}

	/**
	 * 获取分配的客户端标识符
	 *
	 * @return 分配的客户端标识符，如果未设置则返回null
	 */
	public String getAssignedClientIdentifier() {
		return properties.getPropertyValue(MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER);
	}

	/**
	 * 设置分配的客户端标识符
	 *
	 * @param assignedClientIdentifier 客户端标识符
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setAssignedClientIdentifier(String assignedClientIdentifier) {
		properties.add(new StringProperty(MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER, assignedClientIdentifier));
		return this;
	}

	/**
	 * 获取服务器保持连接时间
	 *
	 * @return 服务器保持连接时间，如果未设置则返回null
	 */
	public Integer getServerKeepAlive() {
		return properties.getPropertyValue(MqttPropertyType.SERVER_KEEP_ALIVE);
	}

	/**
	 * 设置服务器保持连接时间
	 *
	 * @param serverKeepAlive 保持连接时间
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setServerKeepAlive(int serverKeepAlive) {
		properties.add(new IntegerProperty(MqttPropertyType.SERVER_KEEP_ALIVE, serverKeepAlive));
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
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setAuthenticationMethod(String authenticationMethod) {
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
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setAuthenticationData(byte[] authenticationData) {
		properties.add(new BinaryProperty(MqttPropertyType.AUTHENTICATION_DATA, authenticationData));
		return this;
	}

	/**
	 * 获取响应信息
	 *
	 * @return 响应信息，如果未设置则返回null
	 */
	public String getResponseInformation() {
		return properties.getPropertyValue(MqttPropertyType.RESPONSE_INFORMATION);
	}

	/**
	 * 设置响应信息
	 *
	 * @param responseInformation 响应信息
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setResponseInformation(String responseInformation) {
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_INFORMATION, responseInformation));
		return this;
	}

	/**
	 * 获取服务器引用
	 *
	 * @return 服务器引用，如果未设置则返回null
	 */
	public String getServerReference() {
		return properties.getPropertyValue(MqttPropertyType.SERVER_REFERENCE);
	}

	/**
	 * 设置服务器引用
	 *
	 * @param serverReference 服务器引用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setServerReference(String serverReference) {
		properties.add(new StringProperty(MqttPropertyType.SERVER_REFERENCE, serverReference));
		return this;
	}

	/**
	 * 获取接收最大数量
	 *
	 * @return 接收最大数量，如果未设置则返回null
	 */
	public Integer getReceiveMaximum() {
		return properties.getPropertyValue(MqttPropertyType.RECEIVE_MAXIMUM);
	}

	/**
	 * 设置接收最大数量
	 *
	 * @param receiveMaximum 接收最大数量
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setReceiveMaximum(int receiveMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM, receiveMaximum));
		return this;
	}

	/**
	 * 获取主题别名最大值
	 *
	 * @return 主题别名最大值，如果未设置则返回null
	 */
	public Integer getTopicAliasMaximum() {
		return properties.getPropertyValue(MqttPropertyType.TOPIC_ALIAS_MAXIMUM);
	}

	/**
	 * 设置主题别名最大值
	 *
	 * @param topicAliasMaximum 主题别名最大值
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setTopicAliasMaximum(int topicAliasMaximum) {
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));
		return this;
	}

	/**
	 * 获取最大QOS
	 *
	 * @return 最大QOS，如果未设置则返回null
	 */
	public Integer getMaximumQos() {
		return properties.getPropertyValue(MqttPropertyType.MAXIMUM_QOS);
	}

	/**
	 * 设置最大QOS
	 *
	 * @param maximumQos 最大QOS
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setMaximumQos(int maximumQos) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_QOS, maximumQos));
		return this;
	}

	/**
	 * 获取保留可用标志
	 *
	 * @return 保留可用标志，如果未设置则返回null
	 */
	public Boolean getRetainAvailable() {
		return properties.getPropertyValue(MqttPropertyType.RETAIN_AVAILABLE);
	}

	/**
	 * 设置保留可用标志
	 *
	 * @param retainAvailable 是否保留可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setRetainAvailable(boolean retainAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.RETAIN_AVAILABLE, retainAvailable));
		return this;
	}

	/**
	 * 获取最大数据包大小
	 *
	 * @return 最大数据包大小，如果未设置则返回null
	 */
	public Integer getMaximumPacketSize() {
		return properties.getPropertyValue(MqttPropertyType.MAXIMUM_PACKET_SIZE);
	}

	/**
	 * 设置最大数据包大小
	 *
	 * @param maximumPacketSize 最大数据包大小
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setMaximumPacketSize(int maximumPacketSize) {
		properties.add(new IntegerProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE, maximumPacketSize));
		return this;
	}

	/**
	 * 获取通配符订阅可用标志
	 *
	 * @return 通配符订阅可用标志，如果未设置则返回null
	 */
	public Boolean getWildcardSubscriptionAvailable() {
		return properties.getPropertyValue(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE);
	}

	/**
	 * 设置通配符订阅可用标志
	 *
	 * @param wildcardSubscriptionAvailable 是否通配符订阅可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setWildcardSubscriptionAvailable(boolean wildcardSubscriptionAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE, wildcardSubscriptionAvailable));
		return this;
	}

	/**
	 * 获取订阅标识符可用标志
	 *
	 * @return 订阅标识符可用标志，如果未设置则返回null
	 */
	public Boolean getSubscriptionIdentifiersAvailable() {
		return properties.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE);
	}

	/**
	 * 设置订阅标识符可用标志
	 *
	 * @param subscriptionIdentifiersAvailable 是否订阅标识符可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setSubscriptionIdentifiersAvailable(boolean subscriptionIdentifiersAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE, subscriptionIdentifiersAvailable));
		return this;
	}

	/**
	 * 获取共享订阅可用标志
	 *
	 * @return 共享订阅可用标志，如果未设置则返回null
	 */
	public Boolean getSharedSubscriptionAvailable() {
		return properties.getPropertyValue(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE);
	}

	/**
	 * 设置共享订阅可用标志
	 *
	 * @param sharedSubscriptionAvailable 是否共享订阅可用
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setSharedSubscriptionAvailable(boolean sharedSubscriptionAvailable) {
		properties.add(new BooleanProperty(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE, sharedSubscriptionAvailable));
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
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties setReasonString(String reasonString) {
		properties.add(new StringProperty(MqttPropertyType.REASON_STRING, reasonString));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttConnAckProperty
	 */
	public MqttConnAckProperties addUserProperty(UserProperty userProperty) {
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
	public MqttConnAckProperties addUserProperty(String key, String value) {
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
