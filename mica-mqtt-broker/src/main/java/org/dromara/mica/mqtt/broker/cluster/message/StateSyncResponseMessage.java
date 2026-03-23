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

import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.tio.server.cluster.message.ClusterDataMessage;

import java.util.List;
import java.util.Map;

/**
 * Response containing full cluster state for synchronization.
 * <p>
 * This message is sent in response to a {@link StateSyncRequestMessage} and contains
 * the complete cluster state including client-to-node mappings and all subscription tables.
 * Used during new node join to synchronize the full cluster state.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType#STATE_SYNC_RESPONSE
 * @see StateSyncRequestMessage
 * @since 1.0.0
 */
public class StateSyncResponseMessage implements ClusterMessage {
	/**
	 * Mapping of client identifiers to the node identifiers where they are connected.
	 */
	private Map<String, String> clientNodeMap;
	/**
	 * Mapping of client identifiers to their subscription lists.
	 */
	private Map<String, List<Subscribe>> subscriptionMap;

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.STATE_SYNC_RESPONSE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
	}

	@Override
	public byte[] toPayload() {
		return ClusterMessageSerializer.serializeStateSyncData(clientNodeMap, subscriptionMap);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		byte[] payload = message.getPayload();
		ClusterMessageSerializer.StateSyncData syncData = ClusterMessageSerializer.deserializeStateSyncData(payload);
		this.clientNodeMap = syncData.getClientNodeMap();
		this.subscriptionMap = syncData.getSubscriptionMap();
	}

	public Map<String, String> getClientNodeMap() {
		return clientNodeMap;
	}

	public void setClientNodeMap(Map<String, String> clientNodeMap) {
		this.clientNodeMap = clientNodeMap;
	}

	public Map<String, List<Subscribe>> getSubscriptionMap() {
		return subscriptionMap;
	}

	public void setSubscriptionMap(Map<String, List<Subscribe>> subscriptionMap) {
		this.subscriptionMap = subscriptionMap;
	}
}
