package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttConnAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttConnAckVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

public final class MqttConnAckBuilder {
	private MqttConnectReasonCode returnCode;
	private boolean sessionPresent;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;
	private MqttConnAckPropertiesBuilder propsBuilder;

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

	public MqttConnAckBuilder properties(PropertiesInitializer<MqttConnAckPropertiesBuilder> consumer) {
		if (propsBuilder == null) {
			propsBuilder = new MqttConnAckPropertiesBuilder();
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
