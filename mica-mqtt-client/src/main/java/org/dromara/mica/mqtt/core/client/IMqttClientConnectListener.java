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

import net.dreamlu.mica.net.core.ChannelContext;

/**
 * mqtt 客户端连接监听
 *
 * @author L.cm
 */
public interface IMqttClientConnectListener {

	/**
	 * 监听到消息
	 *
	 * @param context     ChannelContext
	 * @param isReconnect 是否重连
	 */
	void onConnected(ChannelContext context, boolean isReconnect);

	/**
	 * 连接关闭前触发本方法
	 *
	 * @param context   the ChannelContext
	 * @param throwable the throwable 有可能为空
	 * @param remark    the remark 有可能为空
	 * @param isRemove  is removed
	 */
	void onDisconnect(ChannelContext context, Throwable throwable, String remark, boolean isRemove);

}
