package org.dromara.mica.mqtt.core.common;


import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.core.util.timer.AckTimerTask;
import org.tio.utils.timer.TimerTaskService;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 重试处理器，参考于 netty-mqtt-client
 *
 * @param <T> MqttMessage
 */
public final class RetryProcessor<T extends MqttMessage> {

	private AckTimerTask ackTimerTask;
	private BiConsumer<MqttFixedHeader, T> handler;
	private T originalMessage;

	public void start(TimerTaskService taskService) {
		Objects.requireNonNull(this.handler, "RetryProcessor handler is null.");
		this.startTimer(Objects.requireNonNull(taskService, "RetryProcessor taskService is null."));
	}

	private void startTimer(TimerTaskService taskService) {
		this.ackTimerTask = taskService.addTask((systemTimer) -> {
			return new AckTimerTask(systemTimer, () -> {
				MqttFixedHeader fixedHeader = new MqttFixedHeader(this.originalMessage.fixedHeader().messageType(), true, this.originalMessage.fixedHeader().qosLevel(), this.originalMessage.fixedHeader().isRetain(), this.originalMessage.fixedHeader().remainingLength());
				handler.accept(fixedHeader, originalMessage);
			}, 5, 10);
		});
	}

	public void stop() {
		if (this.ackTimerTask != null) {
			this.ackTimerTask.cancel();
		}
	}

	public void setHandle(BiConsumer<MqttFixedHeader, T> runnable) {
		this.handler = runnable;
	}

	public void setOriginalMessage(T originalMessage) {
		this.originalMessage = originalMessage;
	}

}
