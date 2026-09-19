package org.dromara.mica.mqtt.core.common;


import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.core.util.timer.AckTimerTask;

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
				MqttFixedHeader fixedHeader = this.originalMessage.fixedHeader();
				if (isDupSupported(fixedHeader)) {
					fixedHeader.setDup(true);
				}
				handler.accept(fixedHeader, originalMessage);
			}, 5, 10);
		});
	}

	/**
	 * 是否支持 {@code DUP} 标识，仅 PUBLISH 且 qos 大于 0 支持。
	 * <p>
	 * MQTT 3.1.1 / 5.0 规定 SUBSCRIBE、UNSUBSCRIBE、PUBREL 的 bit3 均为保留位必须置 0，
	 * 置 1 会被严格校验的 broker 视为 malformed 并断开连接。
	 * <a href="https://gitee.com/dromara/mica-mqtt/issues/IKH0V8">IKH0V8</a>
	 *
	 * @param fixedHeader MqttFixedHeader
	 * @return 是否支持 dup
	 */
	static boolean isDupSupported(MqttFixedHeader fixedHeader) {
		if (MqttMessageType.PUBLISH != fixedHeader.messageType()) {
			return false;
		}
		MqttQoS qosLevel = fixedHeader.qosLevel();
		return qosLevel != null && qosLevel.value() > 0;
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
