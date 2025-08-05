package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttReasonCodeAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

public final class DisconnectBuilder {
	private MqttProperties properties;
	private byte reasonCode;

	DisconnectBuilder() {
	}

	public DisconnectBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public DisconnectBuilder reasonCode(byte reasonCode) {
		this.reasonCode = reasonCode;
		return this;
	}

	public MqttMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.QOS0, false, 0);
		MqttReasonCodeAndPropertiesVariableHeader mqttDisconnectVariableHeader =
			new MqttReasonCodeAndPropertiesVariableHeader(reasonCode, properties);

		return new MqttMessage(mqttFixedHeader, mqttDisconnectVariableHeader);
	}
}
