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
 * 固定头 DUP 标识编码的测试
 *
 * <p>反馈：https://gitee.com/dromara/mica-mqtt/issues/IKH0V8</p>
 *
 * @author L.cm
 */
class RetryProcessorTest {

	@Test
	void publishQos1ShouldEncodeDup() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/dup")
			.payload(new byte[]{1})
			.qos(MqttQoS.QOS1)
			.messageId(1)
			.build();
		// 修复前重传：0x32 -> 0x3A
		Assertions.assertEquals((byte) 0x32, firstByte(message.fixedHeader()));
		retransmit(message.fixedHeader());
		Assertions.assertTrue(message.fixedHeader().isDup());
		Assertions.assertEquals((byte) 0x3A, firstByte(message.fixedHeader()));
	}

	@Test
	void publishQos0ShouldNotEncodeDup() {
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName("/test/dup")
			.payload(new byte[]{1})
			.qos(MqttQoS.QOS0)
			.build();
		retransmit(message.fixedHeader());
		// qos0 的 dup 无意义，编码时必须忽略
		Assertions.assertFalse(message.fixedHeader().isDupEffected());
		Assertions.assertEquals((byte) 0x30, firstByte(message.fixedHeader()));
	}

	@Test
	void subscribeShouldNotEncodeDup() {
		MqttSubscribeMessage message = MqttSubscribeMessage.builder()
			.addSubscription("/test/dup", MqttQoS.QOS1)
			.messageId(1)
			.build();
		retransmit(message.fixedHeader());
		// SUBSCRIBE 的 bit3 是保留位，修复前会编码成 0x8A
		Assertions.assertFalse(message.fixedHeader().isDupEffected());
		Assertions.assertEquals((byte) 0x82, firstByte(message.fixedHeader()));
	}

	@Test
	void unSubscribeShouldNotEncodeDup() {
		MqttUnSubscribeMessage message = MqttUnSubscribeMessage.builder()
			.addTopicFilter("/test/dup")
			.messageId(1)
			.build();
		retransmit(message.fixedHeader());
		// UNSUBSCRIBE 的 bit3 是保留位，修复前会编码成 0xAA，
		// 严格校验的 broker 会判定 malformed 并断开连接
		Assertions.assertFalse(message.fixedHeader().isDupEffected());
		Assertions.assertEquals((byte) 0xA2, firstByte(message.fixedHeader()));
	}

	@Test
	void pubRelShouldNotEncodeDup() {
		MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.QOS1, false, 0);
		retransmit(header);
		// PUBREL 的 bit3 是保留位，修复前会编码成 0x6A
		Assertions.assertEquals((byte) 0x62, firstByte(header));
	}

	@Test
	void pubRecShouldNotEncodeDup() {
		MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.QOS0, false, 0);
		retransmit(header);
		// PUBREC 的 bit3 是保留位，修复前会编码成 0x5A
		Assertions.assertEquals((byte) 0x50, firstByte(header));
	}

	@Test
	void pubAckAndPubCompShouldNotEncodeDup() {
		MqttFixedHeader pubAck = new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.QOS0, false, 0);
		MqttFixedHeader pubComp = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.QOS0, false, 0);
		retransmit(pubAck);
		retransmit(pubComp);
		Assertions.assertEquals((byte) 0x40, firstByte(pubAck));
		Assertions.assertEquals((byte) 0x70, firstByte(pubComp));
	}

	@Test
	void dupOnRawHeaderIsIgnoredForNonPublish() {
		// 三方直接构造 fixedHeader 时误置 dup，编码也不应放行
		MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, true, MqttQoS.QOS1, false, 0);
		Assertions.assertTrue(header.isDup());
		Assertions.assertFalse(header.isDupEffected());
		Assertions.assertEquals((byte) 0xA2, firstByte(header));
	}

	/**
	 * 模拟 {@link RetryProcessor} 重传时对固定头的处理
	 *
	 * @param header MqttFixedHeader
	 */
	private static void retransmit(MqttFixedHeader header) {
		header.setDup(true);
	}

	/**
	 * 按 MQTT 协议计算固定头的第一个字节，和 {@code MqttEncoder} 的编码逻辑保持一致
	 *
	 * @param header MqttFixedHeader
	 * @return 固定头第一个字节
	 */
	private static byte firstByte(MqttFixedHeader header) {
		int ret = 0;
		ret |= header.messageType().value() << 4;
		if (header.isDup() && header.isDupEffected()) {
			ret |= 0x08;
		}
		ret |= header.qosLevel().value() << 1;
		if (header.isRetain()) {
			ret |= 0x01;
		}
		return (byte) ret;
	}

}
