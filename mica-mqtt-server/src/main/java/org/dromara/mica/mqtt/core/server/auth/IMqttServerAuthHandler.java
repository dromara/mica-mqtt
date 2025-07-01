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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;

/**
 * mqtt 服务端，认证处理器
 *
 * @author L.cm
 */
@FunctionalInterface
public interface IMqttServerAuthHandler {
	Logger logger = LoggerFactory.getLogger(IMqttServerAuthHandler.class);

	/**
	 * 认证
	 *
	 * @param context  ChannelContext
	 * @param uniqueId mqtt 内唯一id，默认和 clientId 相同
	 * @param clientId 客户端 ID
	 * @param username 用户名
	 * @param password 密码
	 * @return 是否认证成功
	 */
	default boolean verifyAuthenticate(ChannelContext context, String uniqueId, String clientId, String username, String password) {
		try {
			return authenticate(context, uniqueId, clientId, username, password);
		} catch (Throwable e) {
			logger.error("Mqtt client node:{} authenticate error uniqueId:{} clientId:{} username:{} password:{}", context.getClientNode(), uniqueId, clientId, username, password, e);
			return false;
		}
	}

	/**
	 * 认证
	 *
	 * @param context  ChannelContext
	 * @param uniqueId mqtt 内唯一id，默认和 clientId 相同
	 * @param clientId 客户端 ID
	 * @param username 用户名
	 * @param password 密码
	 * @return 是否认证成功
	 */
	boolean authenticate(ChannelContext context, String uniqueId, String clientId, String username, String password);

}
