package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttUnsubAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttUnsubAckPayload;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MqttUnSubAckBuilder {
	private final List<Short> reasonCodes = new ArrayList<>();
	private int packetId;
	private MqttProperties properties;

	MqttUnSubAckBuilder() {
	}

	public MqttUnSubAckBuilder packetId(int packetId) {
		this.packetId = packetId;
		return this;
	}

	public MqttUnSubAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttUnSubAckBuilder addReasonCode(short reasonCode) {
		this.reasonCodes.add(reasonCode);
		return this;
	}

	public MqttUnSubAckBuilder addReasonCodes(Short... reasonCodes) {
		this.reasonCodes.addAll(Arrays.asList(reasonCodes));
		return this;
	}

	public MqttUnsubAckMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.QOS0, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttSubAckVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(packetId, properties);

		MqttUnsubAckPayload subAckPayload = new MqttUnsubAckPayload(reasonCodes);
		return new MqttUnsubAckMessage(mqttFixedHeader, mqttSubAckVariableHeader, subAckPayload);
	}
}
