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

package org.dromara.mica.mqtt.server;

import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.core.server.interceptor.IMqttMessageInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Node;

/**
 * mqtt 消息拦截器
 *
 * @author L.cm
 */
public class MqttMessageInterceptor implements IMqttMessageInterceptor {
	private static final Logger logger = LoggerFactory.getLogger(MqttMessageInterceptor.class);

	@Override
	public void onAfterReceivedBytes(ChannelContext context, int receivedBytes) throws Exception {
		// 注意：此时 clientId 可能为空
		String clientId = context.getBsId();
		Node clientNode = context.getClientNode();
		// ChannelStat channelStat = context.stat;
		// 自定义规则，超限是可用 Tio.remove(context, "xxx超限"); 断开连接。
		logger.info("===接收 client:{} clientId:{} data:{}b", clientNode, clientId, receivedBytes);
	}

	@Override
	public void onAfterDecoded(ChannelContext context, MqttMessage message, int packetSize) {
		// 注意：此时 clientId 可能为空
		String clientId = context.getBsId();
		Node clientNode = context.getClientNode();
		logger.info("===解码 client:{} clientId:{} message:{}", clientNode, clientId, message);
	}

	@Override
	public void onAfterHandled(ChannelContext context, MqttMessage message, long cost) throws Exception {
		String clientId = context.getBsId();
		Node clientNode = context.getClientNode();
		logger.info("===处理完成 ip:{} clientId:{} message:{} 耗时:{}", clientNode, clientId, message, cost);
	}
}
