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

package org.dromara.mica.mqtt.nginx;


import org.dromara.mica.mqtt.core.server.MqttServer;

/**
 * mqtt 服务端测试
 *
 * @author L.cm
 */
public class MqttServerProxyProtocol {

	public static void main(String[] args) {
		MqttServer.create()
			.enableMqtt()
			.enableMqttWs()
			// 开启代理协议
			.proxyProtocolEnable()
			.statEnable(false)
			.debug()
			.start();
	}

}
