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

package org.dromara.mica.mqtt.core.server.protocol;

/**
 * mqtt 协议
 *
 * @author L.cm
 */
public enum MqttProtocol {

	/**
	 * mqtt 协议
	 */
	MQTT(1883),
	/**
	 * mqtt ssl 协议
	 */
	MQTT_SSL(8883),
	/**
	 * mqtt websocket 子协议
	 */
	MQTT_WS(8083),
	/**
	 * mqtt websocket ssl 子协议
	 */
	MQTT_WSS(8084),
	/**
	 * mqtt http api 接口
	 */
	MQTT_HTTP_API(18083),
	;

	private final int port;

	MqttProtocol(int port) {
		this.port = port;
	}

	public int getPort() {
		return this.port;
	}
}
