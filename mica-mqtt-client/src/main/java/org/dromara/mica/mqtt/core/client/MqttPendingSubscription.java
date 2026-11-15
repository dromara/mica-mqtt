package org.dromara.mica.mqtt.core.client;


import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.core.common.RetryProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * MqttPendingSubscription，参考于 netty-mqtt-client
 */
final class MqttPendingSubscription {
	private static final Logger logger = LoggerFactory.getLogger(MqttPendingSubscription.class);
	private final List<MqttClientSubscription> subscriptionList;
	private final Set<String> cancelledTopicFilters = new HashSet<>();
	private final RetryProcessor<MqttSubscribeMessage> retryProcessor = new RetryProcessor<>();

	MqttPendingSubscription(List<MqttClientSubscription> subscriptionList, MqttSubscribeMessage message) {
		this.subscriptionList = new ArrayList<>(subscriptionList);
		this.retryProcessor.setOriginalMessage(message);
	}

	public List<MqttClientSubscription> getSubscriptionList() {
		return subscriptionList;
	}

	/**
	 * 标记取消的主题，保留原订阅顺序以对应 SUBACK 返回码。
	 *
	 * @param topicFilters 待取消的主题过滤器
	 * @return 是否全部取消
	 */
	boolean cancelSubscriptions(List<String> topicFilters) {
		cancelledTopicFilters.addAll(topicFilters);
		return subscriptionList.stream().allMatch(subscription -> isCancelled(subscription.getTopicFilter()));
	}

	boolean isCancelled(String topicFilter) {
		return cancelledTopicFilters.contains(topicFilter);
	}

	void startRetransmitTimer(TimerTaskService taskService, ChannelContext context) {
		this.retryProcessor.setHandle((fixedHeader, originalMessage) -> {
			MqttMessage message = new MqttSubscribeMessage(fixedHeader, originalMessage.variableHeader(), originalMessage.payload());
			boolean result = Tio.send(context, message);
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
