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
 * MQTT5 遗嘱消息属性类，用于存储遗嘱消息相关的属性信息
 *
 * @author L.cm
 */
public class MqttWillPublishProperties {
	private final MqttProperties properties;

	public MqttWillPublishProperties() {
		this(new MqttProperties());
	}

	public MqttWillPublishProperties(MqttProperties properties) {
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
	 * @return MqttWillPublishProperties
	 */
	public MqttWillPublishProperties setPayloadFormatIndicator(int indicator) {
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
	 * @return MqttWillPublishProperty
	 */
	public MqttWillPublishProperties setMessageExpiryInterval(int interval) {
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
	 * @return MqttWillPublishProperty
	 */
	public MqttWillPublishProperties setCorrelationData(byte[] correlationData) {
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
	 * @return MqttWillPublishProperty
	 */
	public MqttWillPublishProperties setContentType(String contentType) {
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
	 * @return MqttWillPublishProperty
	 */
	public MqttWillPublishProperties setResponseTopic(String responseTopic) {
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_TOPIC, responseTopic));
		return this;
	}

	/**
	 * 获取遗嘱延迟时间间隔
	 *
	 * @return 遗嘱延迟时间间隔（秒），如果未设置则返回null
	 */
	public Integer getWillDelayInterval() {
		return properties.getPropertyValue(MqttPropertyType.WILL_DELAY_INTERVAL);
	}

	/**
	 * 设置遗嘱延迟时间间隔
	 *
	 * @param interval 遗嘱延迟时间间隔（秒）
	 * @return MqttWillPublishProperty
	 */
	public MqttWillPublishProperties setWillDelayInterval(int interval) {
		properties.add(new IntegerProperty(MqttPropertyType.WILL_DELAY_INTERVAL, interval));
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 * @return MqttWillPublishProperty
	 */
	public MqttWillPublishProperties addUserProperty(UserProperty userProperty) {
		properties.add(userProperty);
		return this;
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 * @return MqttWillPublishProperty
	 */
	public MqttWillPublishProperties addUserProperty(String key, String value) {
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
