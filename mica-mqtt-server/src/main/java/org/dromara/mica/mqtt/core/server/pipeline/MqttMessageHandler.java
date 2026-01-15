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
 * 消息处理器接口
 * 用于扩展消息处理流程的各个阶段
 *
 * @author L.cm
 */
public interface MqttMessageHandler {

	/**
	 * 处理消息
	 *
	 * @param message 消息
	 * @return 是否继续处理后续处理器，true 继续，false 中断
	 */
	boolean handle(Message message);

	/**
	 * 获取处理器顺序，数字越小越先执行
	 *
	 * @return 顺序值
	 */
	default int getOrder() {
		return 0;
	}

}
