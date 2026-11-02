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

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;

/**
 * 默认的 mqtt 服务端唯一 id 绑定，使用 clientId
 *
 * @author L.cm
 */
public class DefaultMqttServerUniqueIdServiceImpl implements IMqttServerUniqueIdService {

	/**
	 * 是否在 uniqueId 后面追加来源地址。
	 * <p>
	 * MQTT 规范要求 clientId 唯一，重复时服务端会踢掉旧连接（Session taken over）。
	 * 但同一份代码被多实例部署、固定 clientId 时会形成互踢，开启该开关后，
	 * 相同 clientId 从不同来源地址接入会被视为不同会话，互不影响。
	 * </p>
	 */
	private final boolean appendSourceAddress;

	public DefaultMqttServerUniqueIdServiceImpl() {
		this(false);
	}

	public DefaultMqttServerUniqueIdServiceImpl(boolean appendSourceAddress) {
		this.appendSourceAddress = appendSourceAddress;
	}

	@Override
	public String getUniqueId(ChannelContext context, String clientId, String userName, String password) {
		if (!appendSourceAddress || StrUtil.isBlank(clientId)) {
			return clientId;
		}
		Node clientNode = context.getClientNode();
		if (clientNode == null || StrUtil.isBlank(clientNode.getIp())) {
			return clientId;
		}
		return clientId + "@" + clientNode.getIp();
	}

}
