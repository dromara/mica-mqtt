package org.dromara.mica.mqtt.core.client;


import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.core.common.RetryProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MqttPendingSubscription，参考于 netty-mqtt-client
 */
final class MqttPendingSubscription {
	private static final Logger logger = LoggerFactory.getLogger(MqttPendingSubscription.class);
	private final List<MqttClientSubscription> subscriptionList;
	private final Set<String> cancelledTopicFilters = ConcurrentHashMap.newKeySet();
	private final RetryProcessor<MqttSubscribeMessage> retryProcessor = new RetryProcessor<>();
	/**
	 * 全部 topic 已被取消、重试已停止、等待在途 SUBACK 到达后补发补偿退订。
	 * <p>
	 * 连接关闭后清理已取消的记录；连接存续时保留 packetId 以等待 SUBACK。
	 * </p>
	 */
	private volatile boolean fullyCancelled;

	MqttPendingSubscription(List<MqttClientSubscription> subscriptionList, MqttSubscribeMessage message) {
		this.subscriptionList = new ArrayList<>(subscriptionList);
		this.retryProcessor.setOriginalMessage(message);
	}

	public List<MqttClientSubscription> getSubscriptionList() {
		return subscriptionList;
	}

	/**
	 * 当前已被取消的 topic 列表。
	 * <p>
	 * 重试仍然发送原始 SUBSCRIBE（保持 reason code 与 topic 顺序一一对应），
	 * 收到 SUBACK 后需要对这部分 topic 补发 UNSUBSCRIBE，抵消重试可能造成的重新订阅。
	 * </p>
	 *
	 * @return 已取消的 topic 列表
	 */
	List<String> getCancelledTopicFilters() {
		return new ArrayList<>(cancelledTopicFilters);
	}

	/**
	 * 标记取消的主题，保留原订阅顺序以对应 SUBACK 返回码。
	 *
	 * @param topicFilters 待取消的主题过滤器
	 * @return 是否全部取消
	 */
	boolean cancelSubscriptions(List<String> topicFilters) {
		for (MqttClientSubscription subscription : subscriptionList) {
			if (topicFilters.contains(subscription.getTopicFilter())) {
				cancelledTopicFilters.add(subscription.getTopicFilter());
			}
		}
		return subscriptionList.stream().allMatch(subscription -> isCancelled(subscription.getTopicFilter()));
	}

	boolean isCancelled(String topicFilter) {
		return cancelledTopicFilters.contains(topicFilter);
	}

	/**
	 * 标记为「全部取消、等待 SUBACK 补偿退订」状态。
	 * 连接关闭后可安全移除该记录。
	 */
	void markFullyCancelled() {
		this.fullyCancelled = true;
	}

	boolean isFullyCancelled() {
		return fullyCancelled;
	}

	/**
	 * 应用层重新订阅了这些 topic，撤销取消标记，避免补偿 UNSUBSCRIBE 误删新订阅。
	 * 恢复后重新计算是否仍然全部取消。
	 *
	 * @param topicFilters 重新订阅的 topic 集合
	 */
	void restoreCancelledSubscriptions(List<String> topicFilters) {
		if (cancelledTopicFilters.removeAll(topicFilters)) {
			this.fullyCancelled = subscriptionList.stream().allMatch(subscription -> isCancelled(subscription.getTopicFilter()));
		}
	}

	void startRetransmitTimer(TimerTaskService taskService, ChannelContext context) {
		this.retryProcessor.setHandle((fixedHeader, originalMessage) -> {
			MqttMessage message = buildRetryMessage(fixedHeader, originalMessage);
			boolean result = Tio.send(context, message);
			logger.info("retry send Subscribe topics:{} result:{}", subscriptionList, result);
		});
		this.retryProcessor.start(taskService);
	}

	/**
	 * 构造重试用的 SUBSCRIBE。
	 * <p>
	 * 必须与第一次发送的 payload 完全一致：MQTT SUBACK 只按 packetId 和 reason code
	 * 顺序响应，不携带 topic。若重试时删掉已取消的 topic，旧 SUBSCRIBE 的 SUBACK
	 * 晚到就会被错误地按新列表解释，导致授权结果错位。因此这里保留完整列表，
	 * 取消的 topic 由 {@link #getCancelledTopicFilters()} 在 SUBACK 后补发退订。
	 * </p>
	 *
	 * @param fixedHeader 固定头（RetryProcessor 已置 DUP）
	 * @param originalMessage 原始订阅消息
	 * @return 重试消息
	 */
	MqttMessage buildRetryMessage(MqttFixedHeader fixedHeader, MqttSubscribeMessage originalMessage) {
		return new MqttSubscribeMessage(fixedHeader, originalMessage.variableHeader(), originalMessage.payload());
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
