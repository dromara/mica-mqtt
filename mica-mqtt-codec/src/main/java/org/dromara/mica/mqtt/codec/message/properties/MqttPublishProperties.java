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
 * MQTT5 消息发布属性类，用于存储发布消息相关的属性信息
 *
 * @author L.cm
 */
public class MqttPublishProperties {
	private final MqttProperties properties;

	public MqttPublishProperties() {
		this(new MqttProperties());
	}

	public MqttPublishProperties(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 获取负载格式指示器
	 *
	 * @return 负载格式指示器 (0 表示未指定, 1 表示 UTF-8 编码)，如果未设置则返回null
	 */
	public Integer getPayloadFormatIndicator() {
		return properties.getPropertyValue(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR);
	}

	/**
	 * 设置负载格式指示器
	 *
	 * @param indicator 负载格式指示器 (0 表示未指定, 1 表示 UTF-8 编码)
	 * @return MqttPublishProperties
	 */
	public MqttPublishProperties setPayloadFormatIndicator(int indicator) {
		properties.add(new IntegerProperty(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR, indicator));
		return this;
	}

	/**
	 * 获取消息过期时间间隔
	 *
	 * @return 消息过期时间间隔（秒），如果未设置则返回null
	 */
	public Integer getMessageExpiryInterval() {
		return properties.getPropertyValue(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL);
	}

	/**
	 * 设置消息过期时间间隔
	 *
	 * @param interval 消息过期时间间隔（秒）
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties setMessageExpiryInterval(int interval) {
		properties.add(new IntegerProperty(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL, interval));
		return this;
	}

	/**
	 * 获取关联数据
	 *
	 * @return 关联数据，如果未设置则返回null
	 */
	public byte[] getCorrelationData() {
		return properties.getPropertyValue(MqttPropertyType.CORRELATION_DATA);
	}

	/**
	 * 设置关联数据
	 *
	 * @param correlationData 关联数据
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties setCorrelationData(byte[] correlationData) {
		properties.add(new BinaryProperty(MqttPropertyType.CORRELATION_DATA, correlationData));
		return this;
	}

	/**
	 * 获取内容类型
	 *
	 * @return 内容类型，如果未设置则返回null
	 */
	public String getContentType() {
		return properties.getPropertyValue(MqttPropertyType.CONTENT_TYPE);
	}

	/**
	 * 设置内容类型
	 *
	 * @param contentType 内容类型
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties setContentType(String contentType) {
		properties.add(new StringProperty(MqttPropertyType.CONTENT_TYPE, contentType));
		return this;
	}

	/**
	 * 获取响应主题
	 *
	 * @return 响应主题，如果未设置则返回null
	 */
	public String getResponseTopic() {
		return properties.getPropertyValue(MqttPropertyType.RESPONSE_TOPIC);
	}

	/**
	 * 设置响应主题
	 *
	 * @param responseTopic 响应主题
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties setResponseTopic(String responseTopic) {
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_TOPIC, responseTopic));
		return this;
	}

	/**
	 * 获取订阅标识符
	 *
	 * @return 订阅标识符，如果未设置则返回null
	 */
	public Integer getSubscriptionIdentifier() {
		return properties.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER);
	}

	/**
	 * 设置订阅标识符
	 *
	 * @param subscriptionIdentifier 订阅标识符
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties setSubscriptionIdentifier(int subscriptionIdentifier) {
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, subscriptionIdentifier));
		return this;
	}

	/**
	 * 获取主题别名
	 *
	 * @return 主题别名，如果未设置则返回null
	 */
	public Integer getTopicAlias() {
		return properties.getPropertyValue(MqttPropertyType.TOPIC_ALIAS);
	}

	/**
	 * 设置主题别名
	 *
	 * @param topicAlias 主题别名
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties setTopicAlias(int topicAlias) {
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS, topicAlias));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties addUserProperty(UserProperty userProperty) {
		properties.add(userProperty);
		return this;
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperties addUserProperty(String key, String value) {
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
	 * @return 用户属性列表，如果未设置则返回空列表
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
