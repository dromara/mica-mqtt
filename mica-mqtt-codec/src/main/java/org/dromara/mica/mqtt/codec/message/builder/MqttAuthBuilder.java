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

package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttAuthReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttReasonCodeAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.properties.MqttAuthProperty;
import org.dromara.mica.mqtt.codec.properties.*;

/**
 * MqttAuthMessage builder
 *
 * @author netty, L.cm
 */
public final class MqttAuthBuilder {
	private MqttAuthReasonCode reasonCode;
	private final MqttAuthProperty properties = new MqttAuthProperty();

	MqttAuthBuilder() {
	}

	public MqttAuthBuilder reasonCode(MqttAuthReasonCode reasonCode) {
		this.reasonCode = reasonCode;
		return this;
	}

	/**
	 * 设置认证方法
	 *
	 * @param authenticationMethod 认证方法
	 */
	public MqttAuthBuilder authenticationMethod(String authenticationMethod) {
		properties.setAuthenticationMethod(authenticationMethod);
		return this;
	}

	/**
	 * 设置认证数据
	 *
	 * @param authenticationData 认证数据
	 */
	public MqttAuthBuilder authenticationData(byte[] authenticationData) {
		properties.setAuthenticationData(authenticationData);
		return this;
	}

	/**
	 * 设置原因字符串
	 *
	 * @param reasonString 原因字符串
	 */
	public MqttAuthBuilder reasonString(String reasonString) {
		properties.setReasonString(reasonString);
		return this;
	}

	/**
	 * 设置用户属性
	 *
	 * @param userProperty 用户属性
	 */
	public MqttAuthBuilder addUserProperty(UserProperty userProperty) {
		properties.addUserProperty(userProperty);
		return this;
	}

	/**
	 * 添加用户属性
	 *
	 * @param key   key
	 * @param value value
	 */
	public MqttAuthBuilder addUserProperty(String key, String value) {
		properties.addUserProperty(key, value);
		return this;
	}

	/**
	 * 构建 MqttAuthMessage
	 *
	 * @return MqttAuthMessage
	 */
	public MqttMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.AUTH, false, MqttQoS.QOS0, false, 0);
		MqttReasonCodeAndPropertiesVariableHeader mqttAuthVariableHeader =
			new MqttReasonCodeAndPropertiesVariableHeader(reasonCode.value(), properties.getProperties());
		return new MqttMessage(mqttFixedHeader, mqttAuthVariableHeader);
	}
}
