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
import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttConnAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttConnAckVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.function.Consumer;

/**
 * MqttConnAckMessage builder
 *
 * @author netty, L.cm
 */
public final class MqttConnAckBuilder {
	private MqttConnectReasonCode returnCode;
	private boolean sessionPresent;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	MqttConnAckBuilder() {
	}

	public MqttConnAckBuilder returnCode(MqttConnectReasonCode returnCode) {
		this.returnCode = returnCode;
		return this;
	}

	public MqttConnAckBuilder sessionPresent(boolean sessionPresent) {
		this.sessionPresent = sessionPresent;
		return this;
	}

	public MqttConnAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttConnAckBuilder properties(Consumer<MqttConnAckProperties> consumer) {
		MqttConnAckProperties connAckProperties = new MqttConnAckProperties();
		consumer.accept(connAckProperties);
		return properties(connAckProperties.getProperties());
	}

	public MqttConnAckMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.QOS0, false, 0);
		MqttConnAckVariableHeader mqttConnAckVariableHeader =
			new MqttConnAckVariableHeader(returnCode, sessionPresent, properties);
		return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
	}
}
