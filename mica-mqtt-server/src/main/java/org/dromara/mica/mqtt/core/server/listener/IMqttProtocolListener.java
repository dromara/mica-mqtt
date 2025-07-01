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

package org.dromara.mica.mqtt.core.server.listener;

import org.dromara.mica.mqtt.core.server.protocol.MqttProtocol;
import org.tio.core.Node;
import org.tio.core.ssl.SslConfig;

/**
 * mqtt 监听器
 *
 * @author L.cm
 */
public interface IMqttProtocolListener {
	/**
	 * 获取协议
	 *
	 * @return MqttProtocol
	 */
	MqttProtocol getProtocol();

	/**
	 * 获取 ip 断开
	 *
	 * @return ServerNode
	 */
	Node getServerNode();

	/**
	 * ssl 配置
	 *
	 * @return SslConfig
	 */
	SslConfig getSslConfig();



}
