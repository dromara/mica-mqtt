package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttSubAckMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttSubAckPayload;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SubAckBuilder {
	private final List<MqttQoS> grantedQosList;
	private int packetId;
	private MqttProperties properties;

	SubAckBuilder() {
		grantedQosList = new ArrayList<>();
	}

	public SubAckBuilder packetId(int packetId) {
		this.packetId = packetId;
		return this;
	}

	public SubAckBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public SubAckBuilder addGrantedQos(MqttQoS qos) {
		this.grantedQosList.add(qos);
		return this;
	}

	public SubAckBuilder addGrantedQoses(MqttQoS... qoses) {
		this.grantedQosList.addAll(Arrays.asList(qoses));
		return this;
	}

	public SubAckBuilder addGrantedQosList(List<MqttQoS> qosList) {
		this.grantedQosList.addAll(qosList);
		return this;
	}

	public MqttSubAckMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.QOS0, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttSubAckVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(packetId, properties);
		// transform to primitive types
		short[] grantedQosArray = new short[this.grantedQosList.size()];
		int i = 0;
		for (MqttQoS grantedQos : this.grantedQosList) {
			grantedQosArray[i++] = grantedQos.value();
		}
		MqttSubAckPayload subAckPayload = new MqttSubAckPayload(grantedQosArray);
		return new MqttSubAckMessage(mqttFixedHeader, mqttSubAckVariableHeader, subAckPayload);
	}
}
