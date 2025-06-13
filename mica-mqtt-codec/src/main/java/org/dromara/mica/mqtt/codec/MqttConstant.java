/*
 * Copyright 2021 The Netty Project
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

package org.dromara.mica.mqtt.codec;

/**
 * mqtt 常量
 *
 * @author netty
 */
public interface MqttConstant {

	/**
	 * mqtt protocol length
	 */
	int MQTT_PROTOCOL_LENGTH = 2;

	/**
	 * 默认 最大一次读取的 byte 字节数，默认：8k
	 */
	int DEFAULT_MAX_READ_BUFFER_SIZE = 8 * 1024;

	/**
	 * Default max bytes in message，默认：10M
	 */
	int DEFAULT_MAX_BYTES_IN_MESSAGE = 10 * 1024 * 1024;

	/**
	 * min client id length
	 */
	int MIN_CLIENT_ID_LENGTH = 1;

	/**
	 * Default max client id length,In the mqtt3.1 protocol,
	 * the default maximum Client Identifier length is 23，设置成 64，减少问题
	 */
	int DEFAULT_MAX_CLIENT_ID_LENGTH = 64;

}
