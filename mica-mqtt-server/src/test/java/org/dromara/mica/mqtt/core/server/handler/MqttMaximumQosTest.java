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

package org.dromara.mica.mqtt.core.server.handler;

import org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties;
import org.dromara.mica.mqtt.core.server.MqttServerProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MqttMaximumQosTest {

	@Test
	void qosTwoMustBeRepresentedByAbsentProperty() {
		MqttConnAckProperties properties = new MqttConnAckProperties();

		MqttConnectHandler.setMaximumQosProperty(properties, 2);

		Assertions.assertNull(properties.getMaximumQos());
	}

	@Test
	void qosZeroAndOneMustBeIncluded() {
		MqttConnAckProperties qosZero = new MqttConnAckProperties();
		MqttConnAckProperties qosOne = new MqttConnAckProperties();

		MqttConnectHandler.setMaximumQosProperty(qosZero, 0);
		MqttConnectHandler.setMaximumQosProperty(qosOne, 1);

		Assertions.assertEquals(Integer.valueOf(0), qosZero.getMaximumQos());
		Assertions.assertEquals(Integer.valueOf(1), qosOne.getMaximumQos());
	}

	@Test
	void connAckPropertyRejectsIllegalMaximumQos() {
		MqttConnAckProperties properties = new MqttConnAckProperties();

		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.setMaximumQos(2));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.setMaximumQos(-1));
	}

	@Test
	void serverConfigurationAcceptsOnlySupportedQosRange() {
		MqttServerProperties properties = new MqttServerProperties();

		Assertions.assertEquals(2, properties.getMaximumQos());
		Assertions.assertEquals(0, properties.maximumQos(0).getMaximumQos());
		Assertions.assertEquals(1, properties.maximumQos(1).getMaximumQos());
		Assertions.assertEquals(2, properties.maximumQos(2).getMaximumQos());
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.maximumQos(-1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.maximumQos(3));
	}

	@Test
	void serverConfigurationRejectsValuesThatCannotBeEncodedLegally() {
		MqttServerProperties properties = new MqttServerProperties();

		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.receiveMaximum(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.receiveMaximum(0x10000));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.maximumPacketSize(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.topicAliasMaximum(-1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.topicAliasMaximum(0x10000));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.serverKeepAlive(-1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> properties.serverKeepAlive(0x10000));
	}
}
