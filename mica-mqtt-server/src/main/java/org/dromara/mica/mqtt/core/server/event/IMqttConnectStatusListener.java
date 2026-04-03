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

package org.dromara.mica.mqtt.core.server.event;

import net.dreamlu.mica.net.core.ChannelContext;

/**
 * mqtt 链接状态事件
 *
 * @author L.cm
 */
public interface IMqttConnectStatusListener {

	/**
	 * 设备上线（连接成功）
	 *
	 * @param context  ChannelContext
	 * @param clientId clientId
	 * @param username username
	 */
	void online(ChannelContext context, String clientId, String username);

	/**
	 * 设备离线
	 *
	 * @param context  ChannelContext
	 * @param clientId clientId
	 * @param username username
	 * @param reason   reason
	 */
	void offline(ChannelContext context, String clientId, String username, String reason);

}
