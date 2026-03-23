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
 * Request for full state synchronization from a newly joined cluster node.
 * <p>
 * When a new node joins the cluster, it sends this request to all existing nodes
 * to obtain the complete cluster state, including client-to-node mappings and
 * all subscription tables.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @see ClusterMessageType#STATE_SYNC_REQUEST
 * @see StateSyncResponseMessage
 * @since 1.0.0
 */
public class StateSyncRequestMessage implements ClusterMessage {

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.STATE_SYNC_REQUEST;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
	}

	@Override
	public byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
	}
}
