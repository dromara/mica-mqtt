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

package org.dromara.mica.mqtt.core.server.dispatcher;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.tio.core.ChannelContext;

/**
 * mqtt 消息调度器
 *
 * @author L.cm
 */
public interface IMqttMessageDispatcher {

	/**
	 * 发送消息
	 *
	 * @param message 消息
	 * @return 是否成功
	 */
	boolean send(Message message);

	/**
	 * 订阅时下发保留消息，直接发布到订阅的连接
	 *
	 * @param context       ChannelContext
	 * @param clientId      clientId
	 * @param retainMessage retainMessage
	 */
	void sendRetainMessage(ChannelContext context, String clientId, Message retainMessage);
}
