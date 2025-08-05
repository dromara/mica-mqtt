/*
 * Copyright 2020 The Netty Project
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Payload for MQTT unsuback message as in V5.
 *
 * @author netty
 */
public final class MqttUnsubAckPayload {

	private final List<Short> unsubscribeReasonCodes;

	private static final MqttUnsubAckPayload EMPTY = new MqttUnsubAckPayload();

	public static MqttUnsubAckPayload withEmptyDefaults(MqttUnsubAckPayload payload) {
		if (payload == null) {
			return EMPTY;
		} else {
			return payload;
		}
	}

	public MqttUnsubAckPayload() {
		this.unsubscribeReasonCodes = Collections.emptyList();
	}

	public MqttUnsubAckPayload(short[] unsubscribeReasonCodes) {
		Objects.requireNonNull(unsubscribeReasonCodes, "unsubscribeReasonCodes is null.");
		List<Short> list = new ArrayList<>();
		for (short v : unsubscribeReasonCodes) {
			list.add(v);
		}
		this.unsubscribeReasonCodes = Collections.unmodifiableList(list);
	}

	public MqttUnsubAckPayload(Iterable<Short> unsubscribeReasonCodes) {
		Objects.requireNonNull(unsubscribeReasonCodes, "unsubscribeReasonCodes is null.");
		List<Short> list = new ArrayList<>();
		for (Short v : unsubscribeReasonCodes) {
			Objects.requireNonNull(v, "unsubscribeReasonCode is null.");
			list.add(v);
		}
		this.unsubscribeReasonCodes = Collections.unmodifiableList(list);
	}

	public List<Short> unsubscribeReasonCodes() {
		return unsubscribeReasonCodes;
	}

	@Override
	public String toString() {
		return "MqttUnsubAckPayload[" +
			"unsubscribeReasonCodes=" + unsubscribeReasonCodes +
			']';
	}
}
