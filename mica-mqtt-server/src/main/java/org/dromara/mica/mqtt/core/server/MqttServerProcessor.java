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

package org.dromara.mica.mqtt.core.server;

import org.dromara.mica.mqtt.codec.message.MqttConnectMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.MqttSubscribeMessage;
import org.dromara.mica.mqtt.codec.message.MqttUnsubscribeMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttMessageIdVariableHeader;
import org.tio.core.ChannelContext;

/**
 * mqtt broker 处理器
 *
 * @author L.cm
 */
public interface MqttServerProcessor {

	/**
	 * 处理链接
	 *
	 * @param context ChannelContext
	 * @param message MqttConnectMessage
	 */
	void processConnect(ChannelContext context, MqttConnectMessage message);

	/**
	 * Publish
	 *
	 * @param context ChannelContext
	 * @param message MqttPublishMessage
	 */
	void processPublish(ChannelContext context, MqttPublishMessage message);

	/**
	 * PubAck
	 *
	 * @param context        ChannelContext
	 * @param variableHeader MqttMessageIdVariableHeader
	 */
	void processPubAck(ChannelContext context, MqttMessageIdVariableHeader variableHeader);

	/**
	 * PubRec
	 *
	 * @param context        ChannelContext
	 * @param variableHeader MqttMessageIdVariableHeader
	 */
	void processPubRec(ChannelContext context, MqttMessageIdVariableHeader variableHeader);

	/**
	 * PubRel
	 *
	 * @param context        ChannelContext
	 * @param variableHeader MqttMessageIdVariableHeader
	 */
	void processPubRel(ChannelContext context, MqttMessageIdVariableHeader variableHeader);

	/**
	 * PubComp
	 *
	 * @param context        ChannelContext
	 * @param variableHeader MqttMessageIdVariableHeader
	 */
	void processPubComp(ChannelContext context, MqttMessageIdVariableHeader variableHeader);

	/**
	 * 监听
	 *
	 * @param context ChannelContext
	 * @param message MqttSubscribeMessage
	 */
	void processSubscribe(ChannelContext context, MqttSubscribeMessage message);

	/**
	 * 取消监听
	 *
	 * @param context ChannelContext
	 * @param message MqttUnsubscribeMessage
	 */
	void processUnSubscribe(ChannelContext context, MqttUnsubscribeMessage message);

	/**
	 * ping 消息处理
	 *
	 * @param context ChannelContext
	 */
	void processPingReq(ChannelContext context);

	/**
	 * 断开连接
	 *
	 * @param context ChannelContext
	 */
	void processDisConnect(ChannelContext context);

}
