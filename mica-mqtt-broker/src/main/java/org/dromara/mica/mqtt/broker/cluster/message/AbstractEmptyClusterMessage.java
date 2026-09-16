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

import net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage;

import java.util.Map;

/**
 * Base class for cluster messages that carry no headers and no payload.
 * <p>
 * Several cluster messages are pure <em>signals</em> — their mere arrival (together with
 * the source node identity carried by the transport envelope) is the entire information
 * content.  Examples include {@link HeartbeatMessage}, {@link NodeLeaveMessage} and
 * {@link StateSyncRequestMessage}.
 * </p>
 * <p>
 * This class implements the three serialization hooks of {@link ClusterMessage} as
 * {@code final} no-ops so the subclasses only need to declare their message type.
 * </p>
 *
 * @author L.cm
 * @see ClusterMessage
 * @since 2.7.0
 */
public abstract class AbstractEmptyClusterMessage implements ClusterMessage {

	@Override
	public final void toClusterData(Map<String, String> headers) {
		// no headers
	}

	@Override
	public final byte[] toPayload() {
		return new byte[0];
	}

	@Override
	public final void fromClusterData(ClusterDataMessage message) {
		// no state to restore
	}
}
