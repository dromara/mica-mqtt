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

package org.dromara.mica.mqtt.broker.cluster.message;

import org.tio.server.cluster.message.ClusterDataMessage;

import java.util.Map;

/**
 * 客户端连接通知消息
 * <p>
 * 当客户端成功连接时，本节点向集群广播此消息，
 * 通知其他节点更新该客户端所在节点的映射关系。
 * </p>
 *
 * @author L.cm
 */
public class ClientConnectMessage implements BrokerMessage {
	/**
	 * 客户端 ID
	 */
	private String clientId;

	@Override
	public BrokerMessageType getType() {
		return BrokerMessageType.CLIENT_CONNECT;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(BrokerMessageConverter.HEADER_CLIENT_ID, clientId);
	}

	@Override
	public byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.clientId = message.getHeader(BrokerMessageConverter.HEADER_CLIENT_ID);
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
}
