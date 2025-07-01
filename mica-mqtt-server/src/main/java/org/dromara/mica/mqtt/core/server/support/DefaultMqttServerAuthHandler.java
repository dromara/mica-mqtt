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

package org.dromara.mica.mqtt.core.server.support;

import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.tio.core.ChannelContext;

import java.util.Objects;

/**
 * 默认的认证处理
 *
 * @author L.cm
 */
public class DefaultMqttServerAuthHandler implements IMqttServerAuthHandler {
	private final String authUsername;
	private final String authPassword;

	public DefaultMqttServerAuthHandler(String authUsername, String authPassword) {
		this.authUsername = Objects.requireNonNull(authUsername, "Mqtt auth enabled but username is null.");
		this.authPassword = Objects.requireNonNull(authPassword, "Mqtt auth enabled but password is null.");
	}

	@Override
	public boolean authenticate(ChannelContext context, String uniqueId, String clientId, String username, String password) {
		return authUsername.equals(username) && authPassword.equals(password);
	}

}
