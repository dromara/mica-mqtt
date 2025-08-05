package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttReasonCodeAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

public final class AuthBuilder {

	private MqttProperties properties;
	private byte reasonCode;

	AuthBuilder() {
	}

	public AuthBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public AuthBuilder reasonCode(byte reasonCode) {
		this.reasonCode = reasonCode;
		return this;
	}

	public MqttMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.AUTH, false, MqttQoS.QOS0, false, 0);
		MqttReasonCodeAndPropertiesVariableHeader mqttAuthVariableHeader =
			new MqttReasonCodeAndPropertiesVariableHeader(reasonCode, properties);

		return new MqttMessage(mqttFixedHeader, mqttAuthVariableHeader);
	}
}
