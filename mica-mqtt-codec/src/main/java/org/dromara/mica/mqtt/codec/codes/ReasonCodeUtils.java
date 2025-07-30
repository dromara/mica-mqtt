/*
 * Copyright 2021 The vertx Project
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

package org.dromara.mica.mqtt.codec.codes;

/**
 * Utilities for MQTT message codes enums
 *
 * @author vertx-mqtt
 */
public class ReasonCodeUtils {

	protected static <C extends MqttReasonCode> void fillValuesByCode(C[] valuesByCode, C[] values) {
		for (C code : values) {
			final int unsignedByte = code.value() & 0xFF;
			valuesByCode[unsignedByte] = code;
		}
	}

	protected static <C> C codeLoopUp(C[] valuesByCode, byte b, String codeType) {
		final int unsignedByte = b & 0xFF;
		C reasonCode = null;
		try {
			reasonCode = valuesByCode[unsignedByte];
		} catch (ArrayIndexOutOfBoundsException ignored) {
			// no op
		}
		if (reasonCode == null) {
			throw new IllegalArgumentException("unknown " + codeType + " reason code: " + unsignedByte);
		}
		return reasonCode;
	}

}
