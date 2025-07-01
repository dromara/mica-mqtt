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

import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.server.TioServer;
import org.tio.server.TioServerConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * mqtt 协议监听器
 *
 * @author L.cm
 */
public class MqttProtocolListeners {
	private static final Logger logger = LoggerFactory.getLogger(MqttProtocolListeners.class);
	private final List<TioServer> servers;

	public MqttProtocolListeners(MqttServerCreator serverCreator,
						  TioServerConfig mqttServerConfig,
						  List<IMqttProtocolListener> listeners) {
		this.servers = getTioServers(serverCreator, mqttServerConfig, listeners);
	}

	private static List<TioServer> getTioServers(MqttServerCreator serverCreator,
												 TioServerConfig mqttServerConfig,
												 List<IMqttProtocolListener> listeners) {
		List<TioServer> servers = new ArrayList<>();
		for (IMqttProtocolListener listener : listeners) {
			servers.add(listener.config(serverCreator, mqttServerConfig));
		}
		return servers;
	}

	/**
	 * 启动监听器
	 */
	public void start() {
		for (TioServer server : servers) {
			try {
				server.start();
			} catch (IOException e) {
				String serverConfigName = server.getServerConfig().getName();
				String message = serverConfigName + ' ' + server.getServerNode() + " start fail.";
				throw new IllegalStateException(message, e);
			}
		}
	}

	/**
	 * 停止监听器
	 *
	 * @return 是否停止
	 */
	public boolean stop() {
		boolean result = true;
		for (TioServer server : servers) {
			result &= server.stop();
			logger.info("{} stop result:{}", server.getServerConfig().getName(), result);
		}
		return result;
	}
}
