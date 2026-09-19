package org.dromara.mica.mqtt.core.common;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.MqttUnSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 重传时 DUP 标识的测试
 *
 * <p>反馈：https://gitee.com/dromara/mica-mqtt/issues/IKH0V8</p>
 *
 * @author L.cm
 */
class RetryProcessorTest {

	@Test
	void publishQos1ShouldSetDup() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/dup")
			.payload(new byte[]{1})
			.qos(MqttQoS.QOS1)
			.messageId(1)
			.build();
		MqttFixedHeader header = message.fixedHeader();
		Assertions.assertFalse(header.isDup());
		// 重传后：0x32 -> 0x3A
		Assertions.assertEquals((byte) 0x32, firstByte(header));
		retransmit(header);
		Assertions.assertTrue(header.isDup());
		Assertions.assertEquals((byte) 0x3A, firstByte(header));
		// 多次重传幂等
		retransmit(header);
		Assertions.assertEquals((byte) 0x3A, firstByte(header));
	}

	@Test
	void publishQos0ShouldKeepDupFalse() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/dup")
			.payload(new byte[]{1})
			.qos(MqttQoS.QOS0)
			.build();
		MqttFixedHeader header = message.fixedHeader();
		retransmit(header);
		Assertions.assertFalse(header.isDup());
		Assertions.assertEquals((byte) 0x30, firstByte(header));
	}

	@Test
	void subscribeShouldKeepDupFalse() {
		MqttSubscribeMessage message = MqttSubscribeMessage.builder()
			.addSubscription("/test/dup", MqttQoS.QOS1)
			.messageId(1)
			.build();
		MqttFixedHeader header = message.fixedHeader();
		retransmit(header);
		Assertions.assertFalse(header.isDup());
		// 修复前重传会变成 0x8A
		Assertions.assertEquals((byte) 0x82, firstByte(header));
	}

	@Test
	void unSubscribeShouldKeepDupFalse() {
		MqttUnSubscribeMessage message = MqttUnSubscribeMessage.builder()
			.addTopicFilter("/test/dup")
			.messageId(1)
			.build();
		MqttFixedHeader header = message.fixedHeader();
		retransmit(header);
		Assertions.assertFalse(header.isDup());
		// 修复前重传会变成 0xAA，严格校验的 broker 会判定 malformed 并断开连接
		Assertions.assertEquals((byte) 0xA2, firstByte(header));
	}

	@Test
	void pubRelShouldKeepDupFalse() {
		MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.QOS1, false, 0);
		retransmit(header);
		Assertions.assertFalse(header.isDup());
		Assertions.assertEquals((byte) 0x62, firstByte(header));
	}

	@Test
	void pubRecShouldKeepDupFalse() {
		MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.QOS0, false, 0);
		retransmit(header);
		Assertions.assertFalse(header.isDup());
		Assertions.assertEquals((byte) 0x50, firstByte(header));
	}

	/**
	 * 模拟 {@link RetryProcessor} 重传时对固定头的处理
	 *
	 * @param header MqttFixedHeader
	 */
	private static void retransmit(MqttFixedHeader header) {
		if (RetryProcessor.isDupSupported(header)) {
			header.setDup(true);
		}
	}

	/**
	 * 按 MQTT 协议计算固定头的第一个字节，和 {@code MqttEncoder#getFixedHeaderByte1} 保持一致
	 *
	 * @param header MqttFixedHeader
	 * @return 固定头第一个字节
	 */
	private static byte firstByte(MqttFixedHeader header) {
		int ret = 0;
		ret |= header.messageType().value() << 4;
		if (header.isDup()) {
			ret |= 0x08;
		}
		ret |= header.qosLevel().value() << 1;
		if (header.isRetain()) {
			ret |= 0x01;
		}
		return (byte) ret;
	}

}
