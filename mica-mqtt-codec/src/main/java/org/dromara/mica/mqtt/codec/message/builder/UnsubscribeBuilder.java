package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttUnsubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttUnsubscribePayload;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class UnsubscribeBuilder {
	private final List<String> topicFilters;
	private int messageId;
	private MqttProperties properties;

	UnsubscribeBuilder() {
		topicFilters = new ArrayList<>(5);
	}

	public UnsubscribeBuilder addTopicFilter(String topic) {
		topicFilters.add(topic);
		return this;
	}

	public UnsubscribeBuilder addTopicFilters(Collection<String> topicColl) {
		topicFilters.addAll(topicColl);
		return this;
	}

	public UnsubscribeBuilder messageId(int messageId) {
		this.messageId = messageId;
		return this;
	}

	public UnsubscribeBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttUnsubscribeMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.QOS1, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(messageId, properties);
		MqttUnsubscribePayload mqttSubscribePayload = new MqttUnsubscribePayload(topicFilters);
		return new MqttUnsubscribeMessage(mqttFixedHeader, mqttVariableHeader, mqttSubscribePayload);
	}
}
