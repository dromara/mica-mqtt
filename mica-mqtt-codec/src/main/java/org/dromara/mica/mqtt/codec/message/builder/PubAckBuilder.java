package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttPubReplyMessageVariableHeader;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

public final class PubAckBuilder {
	private int packetId;
	private byte reasonCode;
	private MqttProperties properties;

	PubAckBuilder() {
	}

	public PubAckBuilder reasonCode(byte reasonCode) {
		this.reasonCode = reasonCode;
		return this;
	}

	public PubAckBuilder packetId(int packetId) {
		this.packetId = packetId;
		return this;
	}

	public PubAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.QOS0, false, 0);
		MqttPubReplyMessageVariableHeader mqttPubAckVariableHeader =
			new MqttPubReplyMessageVariableHeader(packetId, reasonCode, properties);
		return new MqttMessage(mqttFixedHeader, mqttPubAckVariableHeader);
	}
}
