package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttMessage;
import org.dromara.mica.mqtt.codec.MqttPacket;
import org.dromara.mica.mqtt.codec.MqttUnsubscribeMessage;
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
final class MqttPendingUnSubscription {
	private static final Logger logger = LoggerFactory.getLogger(MqttPendingUnSubscription.class);
	private final List<String> topics;
	private final RetryProcessor<MqttUnsubscribeMessage> retryProcessor = new RetryProcessor<>();

	MqttPendingUnSubscription(List<String> topics, MqttUnsubscribeMessage unSubscribeMessage) {
		this.topics = topics;
		this.retryProcessor.setOriginalMessage(unSubscribeMessage);
	}

	List<String> getTopics() {
		return topics;
	}

	void startRetransmissionTimer(TimerTaskService taskService, ChannelContext context) {
		this.retryProcessor.setHandle((fixedHeader, originalMessage) -> {
			MqttMessage message = new MqttUnsubscribeMessage(fixedHeader, originalMessage.variableHeader(), originalMessage.payload());
			boolean result = Tio.send(context, new MqttPacket(message));
			logger.info("retry send Unsubscribe topics:{} result:{}", topics, result);
		});
		this.retryProcessor.start(taskService);
	}

	void onUnSubAckReceived() {
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
		MqttPendingUnSubscription that = (MqttPendingUnSubscription) o;
		return Objects.equals(topics, that.topics);
	}

	@Override
	public int hashCode() {
		return Objects.hash(topics);
	}
}
