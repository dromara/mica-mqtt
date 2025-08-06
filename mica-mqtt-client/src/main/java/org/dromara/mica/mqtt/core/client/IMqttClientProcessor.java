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

import org.dromara.mica.mqtt.codec.message.*;
import org.tio.core.ChannelContext;

/**
 * mqtt 客户端消息处理器
 *
 * @author L.cm
 */
public interface IMqttClientProcessor {

	/**
	 * 处理服务端链接 ack
	 *
	 * @param context ChannelContext
	 * @param message MqttConnAckMessage
	 */
	void processConAck(ChannelContext context, MqttConnAckMessage message);

	/**
	 * 处理服务端订阅的 ack
	 *
	 * @param message MqttSubAckMessage
	 * @param context ChannelContext
	 */
	void processSubAck(ChannelContext context, MqttSubAckMessage message);

	/**
	 * 处理服务端 publish 的消息
	 *
	 * @param context ChannelContext
	 * @param message MqttPublishMessage
	 */
	void processPublish(ChannelContext context, MqttPublishMessage message);

	/**
	 * 处理服务端解除订阅的 ack
	 *
	 * @param message MqttSubAckMessage
	 */
	void processUnSubAck(MqttUnSubAckMessage message);

	/**
	 * 处理服务端 publish 的 ack
	 *
	 * @param message MqttPubAckMessage
	 */
	void processPubAck(MqttPubAckMessage message);

	/**
	 * 处理服务端 publish rec
	 *
	 * @param context ChannelContext
	 * @param message MqttPubAckMessage
	 */
	void processPubRec(ChannelContext context, MqttMessage message);

	/**
	 * 处理服务端 publish rel
	 *
	 * @param context ChannelContext
	 * @param message MqttPubAckMessage
	 */
	void processPubRel(ChannelContext context, MqttMessage message);

	/**
	 * 处理服务端 publish comp
	 *
	 * @param message MqttPubAckMessage
	 */
	void processPubComp(MqttMessage message);

}
