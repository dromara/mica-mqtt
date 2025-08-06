package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdAndPropertiesVariableHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttSubscribePayload;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MqttSubscribeBuilder {

	private final List<MqttTopicSubscription> subscriptions;
	private int messageId;
	private MqttProperties properties;

	MqttSubscribeBuilder() {
		subscriptions = new ArrayList<>(5);
	}

	public MqttSubscribeBuilder addSubscription(MqttTopicSubscription subscription) {
		subscriptions.add(subscription);
		return this;
	}

	public MqttSubscribeBuilder addSubscription(MqttQoS qos, String topic) {
		return addSubscription(new MqttTopicSubscription(topic, qos));
	}

	public MqttSubscribeBuilder addSubscription(String topic, MqttSubscriptionOption option) {
		return addSubscription(new MqttTopicSubscription(topic, option));
	}

	public MqttSubscribeBuilder addSubscriptions(Collection<MqttTopicSubscription> subscriptionColl) {
		subscriptions.addAll(subscriptionColl);
		return this;
	}

	public MqttSubscribeBuilder messageId(int messageId) {
		this.messageId = messageId;
		return this;
	}

	public MqttSubscribeBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttSubscribeMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.QOS1, false, 0);
		MqttMessageIdAndPropertiesVariableHeader mqttVariableHeader =
			new MqttMessageIdAndPropertiesVariableHeader(messageId, properties);
		MqttSubscribePayload mqttSubscribePayload = new MqttSubscribePayload(subscriptions);
		return new MqttSubscribeMessage(mqttFixedHeader, mqttVariableHeader, mqttSubscribePayload);
	}
}
