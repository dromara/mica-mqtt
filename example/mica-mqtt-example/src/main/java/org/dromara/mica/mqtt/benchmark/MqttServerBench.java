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

package org.dromara.mica.mqtt.benchmark;

import org.dromara.mica.mqtt.core.server.MqttServer;

/**
 * mqtt 服务端测试
 *
 * @author L.cm
 */
public class MqttServerBench {

	public static void main(String[] args) {
		// 设定日志级别为 error
		System.setProperty("tinylog.writer.level", "error");
		// 启动 mqtt 服务
		MqttServer.create()
			.enableMqtt()
			.statEnable(false)
			.start();
	}

}
