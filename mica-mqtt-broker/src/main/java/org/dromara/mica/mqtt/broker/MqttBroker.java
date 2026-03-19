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

package org.dromara.mica.mqtt.broker;

import org.dromara.mica.mqtt.broker.cluster.MqttClusterBrokerCreator;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;

/**
 * Mqtt Broker
 * <p>
 * Entry point for creating MQTT broker instances, including cluster support.
 * </p>
 *
 * @author L.cm
 * @author opencode
 */
public class MqttBroker {

	/**
	 * Create a new MqttClusterBrokerCreator using an existing MqttServerCreator
	 *
	 * @param serverCreator the underlying MqttServerCreator
	 * @return MqttClusterBrokerCreator
	 */
	public static MqttClusterBrokerCreator create(MqttServerCreator serverCreator) {
		return new MqttClusterBrokerCreator(serverCreator);
	}

	/**
	 * Create a new MqttClusterBrokerCreator using a new MqttServerCreator
	 *
	 * @return MqttClusterBrokerCreator
	 */
	public static MqttClusterBrokerCreator create() {
		return new MqttClusterBrokerCreator(MqttServer.create());
	}

}
