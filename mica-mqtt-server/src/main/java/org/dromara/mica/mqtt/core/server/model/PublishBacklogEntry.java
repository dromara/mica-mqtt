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

package org.dromara.mica.mqtt.core.server.model;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

/**
 * 服务端 PUBLISH 在客户端 Receive Maximum 限流时的"待发送"快照（PR7 / P1.7）。
 * <p>
 * 该对象持有重新构造 PUBLISH 报文所需的最小信息（topic / payload / qos / subMqttQoS / retain / properties），
 * 出队时由 {@code MqttServer.drainPublishBacklog} 重新走一次 {@code publish()} 路径以正确分配 packetId。
 *
 * @author L.cm
 */
public final class PublishBacklogEntry {
	private final String topic;
	private final byte[] payload;
	private final MqttQoS qos;
	private final int subMqttQoS;
	private final boolean retain;
	private final MqttProperties properties;
	private final long enqueuedAt;

	public PublishBacklogEntry(String topic, byte[] payload, MqttQoS qos, int subMqttQoS, boolean retain, MqttProperties properties) {
		this.topic = topic;
		this.payload = payload;
		this.qos = qos;
		this.subMqttQoS = subMqttQoS;
		this.retain = retain;
		this.properties = properties;
		this.enqueuedAt = System.currentTimeMillis();
	}

	public String getTopic() {
		return topic;
	}

	public byte[] getPayload() {
		return payload;
	}

	public MqttQoS getQos() {
		return qos;
	}

	public int getSubMqttQoS() {
		return subMqttQoS;
	}

	public boolean isRetain() {
		return retain;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	public long getEnqueuedAt() {
		return enqueuedAt;
	}
}
