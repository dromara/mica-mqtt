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

package org.dromara.mica.mqtt.core.server.handler;

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.message.MqttMessage;

/**
 * mqtt server 消息处理器，按 mqtt 消息类型拆分。
 * <p>
 * 每个 handler 负责一种 MQTT 消息类型，由 {@code DefaultMqttServerProcessor}
 * 通过 {@link #messageTypes()} 做路由查找。
 * <p>
 * 所有 handler 统一接收解码器产出的 {@link MqttMessage}，由 handler 自身按
 * 实际类型拆箱成 {@code MqttPublishMessage} / {@code MqttSubscribeMessage}
 * 等具体子类，避免上游做不安全强转。
 *
 * @author L.cm
 */
public interface IMqttMessageHandler {

	/**
	 * 声明本 handler 处理哪些 mqtt 消息类型。
	 *
	 * @return MqttMessageType[]
	 */
	MqttMessageType[] messageTypes();

	/**
	 * 处理消息。
	 *
	 * @param context ChannelContext
	 * @param message 解码器产出的 MqttMessage
	 */
	void handle(ChannelContext context, MqttMessage message);
}
