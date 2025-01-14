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

package org.dromara.mica.mqtt.client;

import org.dromara.mica.mqtt.core.client.IMqttClientConnectListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;

/**
 * 客户端连接状态监听
 *
 * @author L.cm
 */
public class MqttClientConnectListener implements IMqttClientConnectListener {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientConnectListener.class);

	@Override
	public void onConnected(ChannelContext context, boolean isReconnect) {
		String clientId = context.getId();
		if (isReconnect) {
			logger.info("重连 mqtt 服务器重连成功... clientId:{}", clientId);
		} else {
			logger.info("连接 mqtt 服务器成功... clientId:{}", clientId);
		}
	}

	@Override
	public void onDisconnect(ChannelContext context, Throwable throwable, String remark, boolean isRemove) {
		String clientId = context.getId();
		logger.error("mqtt 链接断开 remark:{} isRemove:{} clientId:{}", remark, isRemove, clientId, throwable);
	}

}
