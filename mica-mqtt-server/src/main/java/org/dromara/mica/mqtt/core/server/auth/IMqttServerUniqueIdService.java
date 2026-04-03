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

package org.dromara.mica.mqtt.core.server.auth;

import net.dreamlu.mica.net.core.ChannelContext;

/**
 * mqtt 服务端唯一 id 绑定
 *
 * @author L.cm
 */
public interface IMqttServerUniqueIdService {

	/**
	 * 获取 mqtt 唯一id，用来绑定 mqtt 内的 session 等功能
	 *
	 * @param context  ChannelContext
	 * @param clientId clientId
	 * @param userName userName
	 * @param password password
	 * @return uniqueId
	 */
	String getUniqueId(ChannelContext context, String clientId, String userName, String password);

}
