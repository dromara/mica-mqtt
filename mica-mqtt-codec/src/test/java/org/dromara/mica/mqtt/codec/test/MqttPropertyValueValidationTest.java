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

package org.dromara.mica.mqtt.codec.test;

import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.codec.message.properties.MqttConnectProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MqttPropertyValueValidationTest {

	@Test
	void receiveMaximumMustBeNonZeroTwoByteInteger() {
		MqttConnectProperties connect = new MqttConnectProperties();
		MqttConnAckProperties connAck = new MqttConnAckProperties();

		connect.setReceiveMaximum(1).setReceiveMaximum(0xFFFF);
		connAck.setReceiveMaximum(1).setReceiveMaximum(0xFFFF);
		Assertions.assertThrows(IllegalArgumentException.class, () -> connect.setReceiveMaximum(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connect.setReceiveMaximum(0x10000));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connAck.setReceiveMaximum(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connAck.setReceiveMaximum(0x10000));
	}

	@Test
	void topicAliasMaximumMustFitTwoByteInteger() {
		MqttConnectProperties connect = new MqttConnectProperties();
		MqttConnAckProperties connAck = new MqttConnAckProperties();

		connect.setTopicAliasMaximum(0).setTopicAliasMaximum(0xFFFF);
		connAck.setTopicAliasMaximum(0).setTopicAliasMaximum(0xFFFF);
		Assertions.assertThrows(IllegalArgumentException.class, () -> connect.setTopicAliasMaximum(-1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connect.setTopicAliasMaximum(0x10000));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connAck.setTopicAliasMaximum(-1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connAck.setTopicAliasMaximum(0x10000));
	}

	@Test
	void maximumPacketSizeMustNotBeZero() {
		MqttConnectProperties connect = new MqttConnectProperties();
		MqttConnAckProperties connAck = new MqttConnAckProperties();

		connect.setMaximumPacketSize(1).setMaximumPacketSize(-1);
		connAck.setMaximumPacketSize(1).setMaximumPacketSize(-1);
		Assertions.assertThrows(IllegalArgumentException.class, () -> connect.setMaximumPacketSize(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connAck.setMaximumPacketSize(0));
	}

	@Test
	void serverKeepAliveMustFitTwoByteInteger() {
		MqttConnAckProperties connAck = new MqttConnAckProperties();

		connAck.setServerKeepAlive(0).setServerKeepAlive(0xFFFF);
		Assertions.assertThrows(IllegalArgumentException.class, () -> connAck.setServerKeepAlive(-1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> connAck.setServerKeepAlive(0x10000));
	}
}
