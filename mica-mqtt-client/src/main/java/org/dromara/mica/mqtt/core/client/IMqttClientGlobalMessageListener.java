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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.tio.core.ChannelContext;

/**
 * mqtt 全局消息处理
 *
 * @author L.cm
 */
@FunctionalInterface
public interface IMqttClientGlobalMessageListener {

	/**
	 * 监听到消息
	 *
	 * @param context ChannelContext
	 * @param topic   topic
	 * @param message MqttPublishMessage
	 * @param payload payload
	 */
	void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload);

}
