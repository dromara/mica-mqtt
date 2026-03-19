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

package org.dromara.mica.mqtt.broker.cluster.core;

import org.dromara.mica.mqtt.broker.cluster.message.ClientConnectMessage;
import org.dromara.mica.mqtt.broker.cluster.message.ClientDisconnectMessage;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.tio.core.ChannelContext;

public class ClusterMqttConnectStatusListener implements IMqttConnectStatusListener {

	private final IMqttConnectStatusListener delegate;
	private final MqttClusterManager clusterManager;

	public ClusterMqttConnectStatusListener(IMqttConnectStatusListener delegate, MqttClusterManager clusterManager) {
		this.delegate = delegate;
		this.clusterManager = clusterManager;
	}

	@Override
	public void online(ChannelContext context, String clientId, String username) {
		if (delegate != null) {
			delegate.online(context, clientId, username);
		}

		ClientConnectMessage msg = new ClientConnectMessage();
		msg.setClientId(clientId);
		clusterManager.broadcast(msg);
	}

	@Override
	public void offline(ChannelContext context, String clientId, String username, String reason) {
		if (delegate != null) {
			delegate.offline(context, clientId, username, reason);
		}

		ClientDisconnectMessage msg = new ClientDisconnectMessage();
		msg.setClientId(clientId);
		clusterManager.broadcast(msg);
	}
}
