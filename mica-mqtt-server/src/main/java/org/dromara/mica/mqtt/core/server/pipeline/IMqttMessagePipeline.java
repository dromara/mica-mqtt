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

package org.dromara.mica.mqtt.core.server.pipeline;

import org.dromara.mica.mqtt.core.server.model.Message;

/**
 * MQTT 消息处理管线接口
 *
 * @author L.cm
 */
public interface IMqttMessagePipeline {

	/**
	 * 添加处理器到所有消息类型
	 *
	 * @param handler 处理器
	 */
	void addHandler(MqttMessageHandler handler);

	/**
	 * 处理消息
	 *
	 * @param message 消息
	 * @return 是否成功
	 */
	boolean handle(Message message);

}
