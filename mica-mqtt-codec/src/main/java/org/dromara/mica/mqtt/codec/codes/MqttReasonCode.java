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
 * Common interface for MQTT messages reason codes enums
 *
 * @author vertx-mqtt
 */
public interface MqttReasonCode {

	/**
	 * byteValue
	 *
	 * @return byteValue
	 */
	byte value();

	/**
	 * isError
	 *
	 * @return boolean
	 */
	default boolean isError() {
		return (value() & 0x80) != 0;
	}

}
