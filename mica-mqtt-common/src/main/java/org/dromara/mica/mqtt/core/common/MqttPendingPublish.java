package org.dromara.mica.mqtt.core.common;

import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.utils.timer.TimerTaskService;

import java.util.Objects;

/**
 * MqttPendingPublish，参考于 netty-mqtt-client
 *
 * @author netty
 */
public final class MqttPendingPublish {
	private static final Logger logger = LoggerFactory.getLogger(MqttPendingPublish.class);
	private final MqttPublishMessage message;
	private final MqttQoS qos;
	private final RetryProcessor<MqttPublishMessage> pubRetryProcessor = new RetryProcessor<>();
	private final RetryProcessor<MqttMessage> pubRelRetryProcessor = new RetryProcessor<>();

	public MqttPendingPublish(MqttPublishMessage message, MqttQoS qos) {
		this.message = message;
		this.qos = qos;
		this.pubRetryProcessor.setOriginalMessage(message);
	}

	public MqttPublishMessage getMessage() {
		return message;
	}

	public MqttQoS getQos() {
		return qos;
	}

	public void startPublishRetransmissionTimer(TimerTaskService taskService, ChannelContext context) {
		this.pubRetryProcessor.setHandle(((fixedHeader, originalMessage) -> {
			MqttPublishMessage publishMessage = new MqttPublishMessage(fixedHeader, originalMessage.variableHeader(), this.message.payload());
			boolean result = Tio.send(context, publishMessage);
			if (context.isServer()) {
				logger.info("retry send Publish msg clientId:{} qos:{} result:{}", context.getBsId(), qos, result);
			} else {
				logger.info("retry send Publish msg qos:{} result:{}", qos, result);
			}
		}));
		this.pubRetryProcessor.start(taskService);
	}

	public void onPubAckReceived() {
		this.pubRetryProcessor.stop();
	}

	public void setPubRelMessage(MqttMessage pubRelMessage) {
		this.pubRelRetryProcessor.setOriginalMessage(pubRelMessage);
	}

	public void startPubRelRetransmissionTimer(TimerTaskService taskService, ChannelContext context) {
		this.pubRelRetryProcessor.setHandle((fixedHeader, originalMessage) -> {
			boolean result = Tio.send(context, new MqttMessage(fixedHeader, originalMessage.variableHeader()));
			if (context.isServer()) {
				logger.info("retry send PubRel msg clientId:{} qos:{} result:{}", context.getBsId(), qos, result);
			} else {
				logger.info("retry send PubRel msg qos:{} result:{}", qos, result);
			}
		});
		this.pubRelRetryProcessor.start(taskService);
	}

	public void onPubCompReceived() {
		this.pubRelRetryProcessor.stop();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MqttPendingPublish that = (MqttPendingPublish) o;
		return Objects.equals(message, that.message) && qos == that.qos;
	}

	@Override
	public int hashCode() {
		return Objects.hash(message, qos);
	}
}
