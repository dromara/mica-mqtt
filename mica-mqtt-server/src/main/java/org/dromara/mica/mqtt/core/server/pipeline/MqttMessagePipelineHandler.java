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

import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;

/**
 * 服务端内部消息流水线处理器。
 * <p>
 * 与处理 MQTT 协议报文的 {@code server.handler.IMqttMessageHandler} 不同，
 * 本接口处理的是服务端内部 {@link Message}，并按 {@link MessageType} 注册到流水线。
 *
 * @author L.cm
 */
public interface MqttMessagePipelineHandler {

	/**
	 * 声明处理的内部消息类型。
	 *
	 * @return MessageType[]
	 */
	MessageType[] messageTypes();

	/**
	 * 处理内部消息。
	 *
	 * @param message 内部消息
	 * @return 是否继续处理同类型的后续处理器
	 */
	boolean handle(Message message);

	/**
	 * 获取处理器顺序，数字越小越先执行。
	 *
	 * @return 顺序值
	 */
	default int getOrder() {
		return 0;
	}
}
