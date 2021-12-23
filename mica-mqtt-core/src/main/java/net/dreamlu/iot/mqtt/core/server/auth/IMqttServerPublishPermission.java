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

package net.dreamlu.iot.mqtt.core.server.auth;

import net.dreamlu.iot.mqtt.codec.MqttQoS;
import org.tio.core.ChannelContext;

/**
 * mqtt 服务端校验客户端是否有发布权限
 *
 * @author L.cm
 */
public interface IMqttServerPublishPermission {

	/**
	 * 否有发布权限
	 *
	 * @param context  ChannelContext
	 * @param clientId 客户端 id
	 * @param topic    topic
	 * @param qoS      MqttQoS
	 * @return 否有发布权限
	 */
	boolean hasPermission(ChannelContext context, String clientId, String topic, MqttQoS qoS);

}
