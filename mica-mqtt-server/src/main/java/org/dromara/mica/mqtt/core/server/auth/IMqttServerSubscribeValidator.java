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
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 服务端，认证处理器
 *
 * @author L.cm
 */
public interface IMqttServerSubscribeValidator {
	Logger logger = LoggerFactory.getLogger(IMqttServerSubscribeValidator.class);

	/**
	 * 校验订阅的 topicFilter
	 *
	 * @param context     ChannelContext
	 * @param clientId    clientId
	 * @param topicFilter topicFilter
	 * @param qoS         MqttQoS
	 * @return 是否有权限
	 */
	default boolean verifyTopicFilter(ChannelContext context, String clientId, String topicFilter, MqttQoS qoS) {
		try {
			return isValid(context, clientId, topicFilter, qoS);
		} catch (Throwable e) {
			logger.error("Mqtt client node:{} subscribe error clientId:{} username:{} topicFilter:{} qos:{}", context.getClientNode(), clientId, context.getUserId(), topicFilter, qoS, e);
			return false;
		}
	}

	/**
	 * 是否可以订阅
	 *
	 * @param context     ChannelContext
	 * @param clientId    客户端 id
	 * @param topicFilter 订阅 topic
	 * @param qoS         MqttQoS
	 * @return 是否可以订阅
	 */
	boolean isValid(ChannelContext context, String clientId, String topicFilter, MqttQoS qoS);

}
