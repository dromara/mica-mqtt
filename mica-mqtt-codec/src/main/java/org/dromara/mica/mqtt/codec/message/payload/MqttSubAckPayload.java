/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.dromara.mica.mqtt.codec.message.payload;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttSubAckMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Payload of the {@link MqttSubAckMessage}
 *
 * @author netty
 */
public class MqttSubAckPayload {
	private final List<Short> reasonCodes;

	public MqttSubAckPayload(short[] reasonCodes) {
		Objects.requireNonNull(reasonCodes, "reasonCodes is null.");
		List<Short> list = new ArrayList<>(reasonCodes.length);
		for (short v : reasonCodes) {
			list.add(v);
		}
		this.reasonCodes = Collections.unmodifiableList(list);
	}

	public MqttSubAckPayload(Iterable<Short> reasonCodes) {
		Objects.requireNonNull(reasonCodes, "reasonCodes is null.");
		List<Short> list = new ArrayList<>();
		for (Short v : reasonCodes) {
			if (v == null) {
				break;
			}
			list.add(v);
		}
		this.reasonCodes = Collections.unmodifiableList(list);
	}

	/**
	 * 兼容 MQTT 3.1/3.1.1 客户端的授权 QoS 列表视图。
	 * <p>
	 * 3.x 时代 SUBACK payload 仅有 0/1/2/0x80（Failure）四种合法值；本方法在 5.0 场景下会
	 * 将 &gt; 2 的 reason code 归一化为 {@link MqttQoS#FAILURE} 的 0x80 旧值。
	 * <p>
	 * <strong>MQTT 5.0 业务代码应直接使用 {@link #reasonCodes()} 获取精确 reason code</strong>
	 * （如 NOT_AUTHORIZED=0x87、TOPIC_FILTER_INVALID=0x8F 等），否则会丢失错误类型。
	 *
	 * @return 3.x 兼容的 QoS 列表
	 */
	public List<Short> grantedQoSLevels() {
		List<Short> qosLevels = new ArrayList<>(reasonCodes.size());
		for (Short code : reasonCodes) {
			if (code > MqttQoS.QOS2.value()) {
				qosLevels.add(MqttQoS.FAILURE.value());
			} else {
				qosLevels.add(code);
			}
		}
		return qosLevels;
	}

	public List<Short> reasonCodes() {
		return reasonCodes;
	}

	@Override
	public String toString() {
		return "MqttSubAckPayload[" +
			"reasonCodes=" + reasonCodes +
			']';
	}
}
