package org.dromara.mica.mqtt.core.client;


import org.dromara.mica.mqtt.codec.MqttMessage;
import org.dromara.mica.mqtt.codec.MqttPacket;
import org.dromara.mica.mqtt.codec.MqttSubscribeMessage;
import org.dromara.mica.mqtt.core.common.RetryProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.utils.timer.TimerTaskService;

import java.util.List;
import java.util.Objects;

/**
 * MqttPendingSubscription，参考于 netty-mqtt-client
 */
final class MqttPendingSubscription {
	private static final Logger logger = LoggerFactory.getLogger(MqttPendingSubscription.class);
	private final List<MqttClientSubscription> subscriptionList;
	private final RetryProcessor<MqttSubscribeMessage> retryProcessor = new RetryProcessor<>();

	MqttPendingSubscription(List<MqttClientSubscription> subscriptionList, MqttSubscribeMessage message) {
		this.subscriptionList = subscriptionList;
		this.retryProcessor.setOriginalMessage(message);
	}

	public List<MqttClientSubscription> getSubscriptionList() {
		return subscriptionList;
	}

	void startRetransmitTimer(TimerTaskService taskService, ChannelContext context) {
		this.retryProcessor.setHandle((fixedHeader, originalMessage) -> {
			MqttMessage message = new MqttSubscribeMessage(fixedHeader, originalMessage.variableHeader(), originalMessage.payload());
			boolean result = Tio.send(context, new MqttPacket(message));
			logger.info("retry send Subscribe topics:{} result:{}", subscriptionList, result);
		});
		this.retryProcessor.start(taskService);
	}

	void onSubAckReceived() {
		this.retryProcessor.stop();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MqttPendingSubscription that = (MqttPendingSubscription) o;
		return Objects.equals(subscriptionList, that.subscriptionList);
	}

	@Override
	public int hashCode() {
		return Objects.hash(subscriptionList);
	}
}
