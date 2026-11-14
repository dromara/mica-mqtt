package org.dromara.mica.mqtt.core.common;

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private MqttMessage pubRelMessage;
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
		this.pubRelMessage = pubRelMessage;
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

	/**
	 * 重连后按服务端会话状态恢复当前 QoS 发布阶段。
	 *
	 * @param taskService 定时任务服务
	 * @param context 新连接上下文
	 * @param sessionPresent 服务端是否恢复了原会话
	 */
	public void retransmit(TimerTaskService taskService, ChannelContext context, boolean sessionPresent) {
		pubRetryProcessor.stop();
		pubRelRetryProcessor.stop();
		if (sessionPresent && pubRelMessage != null) {
			startPubRelRetransmissionTimer(taskService, context);
			Tio.send(context, pubRelMessage);
		} else {
			pubRelMessage = null;
			message.fixedHeader().setDup(true);
			startPublishRetransmissionTimer(taskService, context);
			Tio.send(context, message);
		}
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
