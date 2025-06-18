/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.net.dreamlu.net).
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

import org.tio.core.intf.Packet;

/**
 * mqtt 包
 *
 * @author L.cm
 */
public class MqttPacket extends Packet {
	// Constants for fixed-header only message types with all flags set to 0 (see
	// https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Table_2.2_-)
	private static final MqttMessage PINGREQ = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGREQ, false,
		MqttQoS.QOS0, false, 0));
	private static final MqttMessage PINGRESP = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false,
		MqttQoS.QOS0, false, 0));
	private static final MqttMessage DISCONNECT = new MqttMessage(new MqttFixedHeader(MqttMessageType.DISCONNECT, false,
		MqttQoS.QOS0, false, 0));

	public static final MqttPacket MQTT_PING_REQ = new MqttPacket(PINGREQ);
	public static final MqttPacket MQTT_PING_RSP = new MqttPacket(PINGRESP);
	public static final MqttPacket MQTT_DISCONNECT = new MqttPacket(DISCONNECT);

	private final MqttMessage  mqttMessage;

	public MqttPacket(MqttMessage mqttMessage) {
		this.mqttMessage = mqttMessage;
	}

	public MqttMessage getMqttMessage() {
		return mqttMessage;
	}

	@Override
	public String toString() {
		return "MqttPacket{" +
			"mqttMessage=" + mqttMessage +
			'}';
	}
}
