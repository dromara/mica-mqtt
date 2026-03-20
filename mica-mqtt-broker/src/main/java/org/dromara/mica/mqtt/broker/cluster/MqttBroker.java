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

package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;

/**
 * Entry point for creating MQTT broker instances with cluster mode support.
 * <p>
 * This class provides static factory methods to create a {@link MqttClusterBrokerCreator}
 * which can then be used to configure and build an MQTT broker that operates in
 * cluster mode, enabling horizontal scalability and high availability.
 * </p>
 *
 * @author L.cm
 * @author opencode
 * @see MqttClusterBrokerCreator
 * @since 1.0.0
 */
public class MqttBroker {

	/**
	 * Creates a new {@link MqttClusterBrokerCreator} using an existing {@link MqttServerCreator}.
	 *
	 * @param serverCreator the underlying server creator to wrap
	 * @return a new broker creator instance
	 */
	public static MqttClusterBrokerCreator create(MqttServerCreator serverCreator) {
		return new MqttClusterBrokerCreator(serverCreator);
	}

	/**
	 * Creates a new {@link MqttClusterBrokerCreator} using a new default {@link MqttServerCreator}.
	 *
	 * @return a new broker creator instance
	 */
	public static MqttClusterBrokerCreator create() {
		return new MqttClusterBrokerCreator(MqttServer.create());
	}

}
