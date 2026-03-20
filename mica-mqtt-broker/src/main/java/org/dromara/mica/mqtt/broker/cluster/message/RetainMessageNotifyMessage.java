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

import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.serializer.DefaultMessageSerializer;
import org.tio.server.cluster.message.ClusterDataMessage;

import java.util.Map;

/**
 * 保留消息通知消息
 * <p>
 * 当保留消息发布或清除时，广播通知所有节点同步更新。
 * 用于集群环境下保留消息的一致性。
 * </p>
 *
 * @author L.cm
 */
public class RetainMessageNotifyMessage implements BrokerMessage {
	/**
	 * 主题
	 */
	private String topic;
	/**
	 * 保留超时时间（秒）
	 */
	private int timeout;
	/**
	 * 保留消息
	 */
	private Message retainMessage;

	@Override
	public BrokerMessageType getType() {
		return BrokerMessageType.RETAIN_MESSAGE;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(BrokerMessageConverter.HEADER_TOPIC, topic);
		headers.put(BrokerMessageConverter.HEADER_TIMEOUT, String.valueOf(timeout));
	}

	@Override
	public byte[] toPayload() {
		if (retainMessage == null) {
			return new byte[0];
		}
		return DefaultMessageSerializer.INSTANCE.serialize(retainMessage);
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		this.topic = message.getHeader(BrokerMessageConverter.HEADER_TOPIC);
		String timeoutStr = message.getHeader(BrokerMessageConverter.HEADER_TIMEOUT);
		this.timeout = timeoutStr != null ? Integer.parseInt(timeoutStr) : 0;
		byte[] payload = message.getPayload();
		if (payload != null && payload.length > 0) {
			this.retainMessage = DefaultMessageSerializer.INSTANCE.deserialize(payload);
		}
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public Message getRetainMessage() {
		return retainMessage;
	}

	public void setRetainMessage(Message retainMessage) {
		this.retainMessage = retainMessage;
	}
}