/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.codec;

import org.dromara.mica.mqtt.codec.codes.MqttUnSubAckReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttUnSubAckMessage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * UNSUBACK 编码回归测试。
 */
class MqttUnSubAckEncoderTest {

	@Test
	void mqtt311UnSubAckContainsOnlyPacketIdentifier() {
		byte[] encoded = encode(MqttVersion.MQTT_3_1_1);

		assertArrayEquals(new byte[]{(byte) 0xB0, 0x02, 0x12, 0x34}, encoded);
	}

	@Test
	void mqtt5UnSubAckContainsPropertiesAndReasonCode() {
		byte[] encoded = encode(MqttVersion.MQTT_5);

		assertArrayEquals(new byte[]{(byte) 0xB0, 0x04, 0x12, 0x34, 0x00, 0x00}, encoded);
	}

	private static byte[] encode(MqttVersion version) {
		MqttUnSubAckMessage message = MqttUnSubAckMessage.builder()
			.packetId(0x1234)
			.addReasonCode(MqttUnSubAckReasonCode.SUCCESS)
			.build();
		ByteBuffer buffer = MqttEncoder.encodeUnSubAckMessage(version, message);
		return buffer.array();
	}
}
