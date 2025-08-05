package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttConnAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttConnAckVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

public final class ConnAckBuilder {
	private MqttConnectReasonCode returnCode;
	private boolean sessionPresent;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;
	private ConnAckPropertiesBuilder propsBuilder;

	ConnAckBuilder() {
	}

	public ConnAckBuilder returnCode(MqttConnectReasonCode returnCode) {
		this.returnCode = returnCode;
		return this;
	}

	public ConnAckBuilder sessionPresent(boolean sessionPresent) {
		this.sessionPresent = sessionPresent;
		return this;
	}

	public ConnAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public ConnAckBuilder properties(PropertiesInitializer<ConnAckPropertiesBuilder> consumer) {
		if (propsBuilder == null) {
			propsBuilder = new ConnAckPropertiesBuilder();
		}
		consumer.apply(propsBuilder);
		return this;
	}

	public MqttConnAckMessage build() {
		if (propsBuilder != null) {
			properties = propsBuilder.build();
		}
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.QOS0, false, 0);
		MqttConnAckVariableHeader mqttConnAckVariableHeader =
			new MqttConnAckVariableHeader(returnCode, sessionPresent, properties);
		return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
	}
}
