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

import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.StringProperty;
import org.dromara.mica.mqtt.codec.properties.UserProperty;

/**
 * MQTT5 DISCONNECT 属性类，用于存储断开连接相关的属性信息
 *
 * @author L.cm
 */
public class MqttDisconnectProperty {
	private final MqttProperties properties;

	public MqttDisconnectProperty() {
		this(new MqttProperties());
	}

	public MqttDisconnectProperty(MqttProperties properties) {
		this.properties = properties;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	/**
	 * 设置会话过期间隔
	 *
	 * @param interval 会话过期间隔
	 */
	public void setSessionExpiryInterval(int interval) {
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, interval));
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
