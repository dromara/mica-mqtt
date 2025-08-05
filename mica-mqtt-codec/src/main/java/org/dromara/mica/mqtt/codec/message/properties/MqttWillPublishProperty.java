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
 * MQTT5 遗嘱消息属性类，用于存储遗嘱消息相关的属性信息
 *
 * @author L.cm
 */
public class MqttWillPublishProperty {
	private final MqttProperties properties;

	public MqttWillPublishProperty() {
		this(new MqttProperties());
	}

	public MqttWillPublishProperty(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 设置负载格式指示器
	 *
	 * @param indicator 负载格式指示器 (0 表示未指定, 1 表示 UTF-8 编码)
	 */
	public void setPayloadFormatIndicator(int indicator) {
		properties.add(new IntegerProperty(MqttPropertyType.PAYLOAD_FORMAT_INDICATOR, indicator));
	}

	/**
	 * 设置消息过期时间间隔
	 *
	 * @param interval 消息过期时间间隔（秒）
	 */
	public void setMessageExpiryInterval(int interval) {
		properties.add(new IntegerProperty(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL, interval));
	}

	/**
	 * 设置关联数据
	 *
	 * @param correlationData 关联数据
	 */
	public void setCorrelationData(byte[] correlationData) {
		properties.add(new BinaryProperty(MqttPropertyType.CORRELATION_DATA, correlationData));
	}

	/**
	 * 设置内容类型
	 *
	 * @param contentType 内容类型
	 */
	public void setContentType(String contentType) {
		properties.add(new StringProperty(MqttPropertyType.CONTENT_TYPE, contentType));
	}

	/**
	 * 设置响应主题
	 *
	 * @param responseTopic 响应主题
	 */
	public void setResponseTopic(String responseTopic) {
		properties.add(new StringProperty(MqttPropertyType.RESPONSE_TOPIC, responseTopic));
	}

	/**
	 * 设置遗嘱延迟时间间隔
	 *
	 * @param interval 遗嘱延迟时间间隔（秒）
	 */
	public void setWillDelayInterval(int interval) {
		properties.add(new IntegerProperty(MqttPropertyType.WILL_DELAY_INTERVAL, interval));
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
