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
 * MQTT5 消息发布属性类，用于存储发布消息相关的属性信息
 *
 * @author L.cm
 */
public class MqttPublishProperty {
	private final MqttProperties properties;

	public MqttPublishProperty() {
		this(new MqttProperties());
	}

	public MqttPublishProperty(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 设置负载格式指示器
	 *
	 * @param indicator 负载格式指示器 (0 表示未指定, 1 表示 UTF-8 编码)
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty setPayloadFormatIndicator(int indicator) {
		properties.add(new IntegerProperty(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR, indicator));
		return this;
	}

	/**
	 * 设置消息过期时间间隔
	 *
	 * @param interval 消息过期时间间隔（秒）
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty setMessageExpiryInterval(int interval) {
		properties.add(new IntegerProperty(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL, interval));
		return this;
	}

	/**
	 * 设置关联数据
	 *
	 * @param correlationData 关联数据
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty setCorrelationData(byte[] correlationData) {
		properties.add(new BinaryProperty(MqttPropertyType.CORRELATION_DATA, correlationData));
		return this;
	}

	/**
	 * 设置内容类型
	 *
	 * @param contentType 内容类型
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty setContentType(String contentType) {
		properties.add(new StringProperty(MqttPropertyType.CONTENT_TYPE, contentType));
		return this;
	}

	/**
	 * 设置响应主题
	 *
	 * @param responseTopic 响应主题
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty setResponseTopic(String responseTopic) {
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_TOPIC, responseTopic));
		return this;
	}

	/**
	 * 设置订阅标识符
	 *
	 * @param subscriptionIdentifier 订阅标识符
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty setSubscriptionIdentifier(int subscriptionIdentifier) {
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, subscriptionIdentifier));
		return this;
	}

	/**
	 * 设置主题别名
	 *
	 * @param topicAlias 主题别名
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty setTopicAlias(int topicAlias) {
		properties.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS, topicAlias));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttPublishProperty
	 */
	public MqttPublishProperty addUserProperty(UserProperty userProperty) {
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
	public MqttPublishProperty addUserProperty(String key, String value) {
		this.addUserProperty(new UserProperty(key, value));
		return this;
	}
}
