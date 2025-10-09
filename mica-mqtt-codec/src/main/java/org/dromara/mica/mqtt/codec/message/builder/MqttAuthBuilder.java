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
import org.dromara.mica.mqtt.codec.message.properties.MqttAuthProperties;
import org.dromara.mica.mqtt.codec.properties.*;

import java.util.function.Consumer;

/**
 * MqttAuthMessage builder
 *
 * @author netty, L.cm
 */
public final class MqttAuthBuilder {
	private MqttAuthReasonCode reasonCode;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	public MqttAuthBuilder() {
	}

	public MqttAuthBuilder reasonCode(MqttAuthReasonCode reasonCode) {
		this.reasonCode = reasonCode;
		return this;
	}

	public MqttAuthBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttAuthBuilder properties(Consumer<MqttAuthProperties> consumer) {
		MqttAuthProperties authProperties = new MqttAuthProperties();
		consumer.accept(authProperties);
		return properties(authProperties.getProperties());
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
			new MqttReasonCodeAndPropertiesVariableHeader(reasonCode.value(), properties);
		return new MqttMessage(mqttFixedHeader, mqttAuthVariableHeader);
	}
}
