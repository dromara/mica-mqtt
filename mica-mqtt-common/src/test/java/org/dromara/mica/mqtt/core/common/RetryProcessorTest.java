package org.dromara.mica.mqtt.core.common;

import net.dreamlu.mica.net.utils.timer.SystemTimer;
import net.dreamlu.mica.net.utils.timer.TimerTask;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.MqttUnSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * RetryProcessor 重传行为的测试
 *
 * <p>重传时 RetryProcessor 只负责复用原固定头并统一置 {@code dup}，
 * {@code dup} 是否真正生效由编码层按报文类型判定，编码字节的断言见
 * {@code org.dromara.mica.mqtt.codec.MqttEncoderFixedHeaderTest}。</p>
 *
 * <p>反馈：<a href="https://gitee.com/dromara/mica-mqtt/issues/IKH0V8">IKH0V8</a></p>
 *
 * @author L.cm
 */
class RetryProcessorTest {

	@Test
	void retransmitReusesFixedHeaderAndSetsDup() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/dup")
			.payload(new byte[]{1})
			.qos(MqttQoS.QOS1)
			.messageId(1)
			.build();
		MqttFixedHeader original = message.fixedHeader();

		List<MqttFixedHeader> received = retransmit(message);

		Assertions.assertEquals(1, received.size());
		// 复用原固定头，不再重新 new，避免丢掉 headLength 等字段
		Assertions.assertSame(original, received.get(0));
		Assertions.assertTrue(original.isDup());
		Assertions.assertTrue(original.isDupEffected());
	}

	@Test
	void retransmitPublishQos0SetsDupIntentButNotEffected() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/dup")
			.payload(new byte[]{1})
			.qos(MqttQoS.QOS0)
			.build();

		List<MqttFixedHeader> received = retransmit(message);

		Assertions.assertSame(message.fixedHeader(), received.get(0));
		Assertions.assertTrue(message.fixedHeader().isDup());
		// qos0 的 dup 无意义，编码时必须忽略
		Assertions.assertFalse(message.fixedHeader().isDupEffected());
	}

	@Test
	void retransmitUnSubscribeSetsDupIntentButNotEffected() {
		MqttUnSubscribeMessage message = MqttUnSubscribeMessage.builder()
			.addTopicFilter("/test/dup")
			.messageId(1)
			.build();

		List<MqttFixedHeader> received = retransmit(message);

		Assertions.assertSame(message.fixedHeader(), received.get(0));
		// UNSUBSCRIBE 的 bit3 是保留位，置了 dup 也不能编码出去
		Assertions.assertTrue(message.fixedHeader().isDup());
		Assertions.assertFalse(message.fixedHeader().isDupEffected());
	}

	@Test
	void startWithoutHandlerFails() {
		RetryProcessor<MqttMessage> processor = new RetryProcessor<>();
		processor.setOriginalMessage(MqttMessage.PINGREQ);
		Assertions.assertThrows(NullPointerException.class,
			() -> processor.start(new CapturingTimerTaskService()));
	}

	@Test
	void startWithoutTaskServiceFails() {
		RetryProcessor<MqttMessage> processor = new RetryProcessor<>();
		processor.setOriginalMessage(MqttMessage.PINGREQ);
		processor.setHandle((fixedHeader, originalMessage) -> {
		});
		Assertions.assertThrows(NullPointerException.class, () -> processor.start(null));
	}

	@Test
	void stopWithoutStartIsNoop() {
		new RetryProcessor<MqttMessage>().stop();
	}

	/**
	 * 驱动一次重传。
	 * <p>
	 * RetryProcessor 的重传间隔是 10s，测试里不等定时器，直接用假的 TimerTaskService
	 * 截获 AckTimerTask 后手动触发一次，保证确定性。
	 * </p>
	 *
	 * @param message MqttMessage
	 * @return 重传时交给 handler 的固定头
	 */
	private static List<MqttFixedHeader> retransmit(MqttMessage message) {
		RetryProcessor<MqttMessage> processor = new RetryProcessor<>();
		List<MqttFixedHeader> received = new ArrayList<>();
		processor.setOriginalMessage(message);
		processor.setHandle((fixedHeader, originalMessage) -> received.add(fixedHeader));
		CapturingTimerTaskService taskService = new CapturingTimerTaskService();
		processor.start(taskService);
		taskService.task.run();
		processor.stop();
		return received;
	}

	/**
	 * 截获 AckTimerTask 的 TimerTaskService，任务不会真正被调度。
	 */
	private static class CapturingTimerTaskService implements TimerTaskService {
		private final SystemTimer systemTimer = new SystemTimer("RetryProcessorTest");
		private TimerTask task;

		@Override
		public <T extends TimerTask> T add(T timerTask) {
			this.task = timerTask;
			return timerTask;
		}

		@Override
		public <T extends TimerTask> T addTask(Function<SystemTimer, T> consumer) {
			T timerTask = consumer.apply(systemTimer);
			this.task = timerTask;
			return timerTask;
		}

		@Override
		public void start() {
		}

		@Override
		public void stop() {
		}
	}

}
