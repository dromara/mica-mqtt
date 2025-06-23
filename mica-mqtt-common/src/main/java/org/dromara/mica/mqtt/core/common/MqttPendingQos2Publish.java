package org.dromara.mica.mqtt.core.common;

import org.dromara.mica.mqtt.codec.MqttMessage;
import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.utils.timer.TimerTaskService;

import java.util.Objects;

/**
 * MqttPendingPublish，参考于 netty-mqtt-client
 */
public final class MqttPendingQos2Publish {
	private static final Logger logger = LoggerFactory.getLogger(MqttPendingQos2Publish.class);
	private final MqttPublishMessage incomingPublish;
	private final RetryProcessor<MqttMessage> retryProcessor = new RetryProcessor<>();

	public MqttPendingQos2Publish(MqttPublishMessage incomingPublish, MqttMessage originalMessage) {
		this.incomingPublish = incomingPublish;
		this.retryProcessor.setOriginalMessage(originalMessage);
	}

	public MqttPublishMessage getIncomingPublish() {
		return incomingPublish;
	}

	public void startPubRecRetransmitTimer(TimerTaskService taskService, ChannelContext context) {
		this.retryProcessor.setHandle((fixedHeader, originalMessage) -> {
			boolean result = Tio.send(context, new MqttMessage(fixedHeader, originalMessage.variableHeader()));
			if (context.isServer()) {
				logger.info("retry send PubRec msg clientId:{} result:{}", context.getBsId(), result);
			} else {
				logger.info("retry send PubRec msg result:{}", result);
			}
		});
		this.retryProcessor.start(taskService);
	}

	public void onPubRelReceived() {
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
		MqttPendingQos2Publish that = (MqttPendingQos2Publish) o;
		return Objects.equals(incomingPublish, that.incomingPublish);
	}

	@Override
	public int hashCode() {
		return Objects.hash(incomingPublish);
	}

}
