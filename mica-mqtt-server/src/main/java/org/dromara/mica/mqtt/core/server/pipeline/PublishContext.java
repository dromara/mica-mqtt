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

package org.dromara.mica.mqtt.core.server.pipeline;

import java.io.Serializable;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.tio.core.ChannelContext;

/**
 * 发布消息上下文，承载完整的发布消息信息（包括 MQTT5 properties）
 *
 * @author L.cm
 */
public class PublishContext implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 原始 MQTT 发布消息
	 */
	private final MqttPublishMessage publishMessage;
	/**
	 * ChannelContext
	 */
	private final ChannelContext context;
	/**
	 * 客户端 ID
	 */
	private final String clientId;
	/**
	 * 用户名
	 */
	private final String username;
	/**
	 * Topic
	 */
	private final String topic;
	/**
	 * QoS
	 */
	private final MqttQoS qos;
	/**
	 * 是否重发
	 */
	private final boolean dup;
	/**
	 * 是否保留
	 */
	private final boolean retain;
	/**
	 * 消息内容
	 */
	private final byte[] payload;
	/**
	 * 消息 ID
	 */
	private final Integer messageId;
	/**
	 * MQTT5 属性
	 */
	private final MqttProperties properties;
	/**
	 * 客户端 IP
	 */
	private final String peerHost;
	/**
	 * 节点名称
	 */
	private final String nodeName;
	/**
	 * 消息到达时间戳
	 */
	private final long timestamp;
	/**
	 * PUBLISH 消息到达 Broker 的时间 (ms)
	 */
	private final Long publishReceivedAt;

	public PublishContext(MqttPublishMessage publishMessage,
						  ChannelContext context,
						  String clientId,
						  String username,
						  String topic,
						  MqttQoS qos,
						  boolean dup,
						  boolean retain,
						  byte[] payload,
						  Integer messageId,
						  MqttProperties properties,
						  String peerHost,
						  String nodeName,
						  long timestamp,
						  Long publishReceivedAt) {
		this.publishMessage = publishMessage;
		this.context = context;
		this.clientId = clientId;
		this.username = username;
		this.topic = topic;
		this.qos = qos;
		this.dup = dup;
		this.retain = retain;
		this.payload = payload;
		this.messageId = messageId;
		this.properties = properties != null ? properties : MqttProperties.NO_PROPERTIES;
		this.peerHost = peerHost;
		this.nodeName = nodeName;
		this.timestamp = timestamp;
		this.publishReceivedAt = publishReceivedAt;
	}

	public static Builder builder() {
		return new Builder();
	}

	public MqttPublishMessage getPublishMessage() {
		return publishMessage;
	}

	public ChannelContext getContext() {
		return context;
	}

	public String getClientId() {
		return clientId;
	}

	public String getUsername() {
		return username;
	}

	public String getTopic() {
		return topic;
	}

	public MqttQoS getQos() {
		return qos;
	}

	public boolean isDup() {
		return dup;
	}

	public boolean isRetain() {
		return retain;
	}

	public byte[] getPayload() {
		return payload;
	}

	public Integer getMessageId() {
		return messageId;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	public String getPeerHost() {
		return peerHost;
	}

	public String getNodeName() {
		return nodeName;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Long getPublishReceivedAt() {
		return publishReceivedAt;
	}

	/**
	 * Builder
	 */
	public static class Builder {
		private MqttPublishMessage publishMessage;
		private ChannelContext context;
		private String clientId;
		private String username;
		private String topic;
		private MqttQoS qos;
		private boolean dup;
		private boolean retain;
		private byte[] payload;
		private Integer messageId;
		private MqttProperties properties;
		private String peerHost;
		private String nodeName;
		private long timestamp;
		private Long publishReceivedAt;

		public Builder publishMessage(MqttPublishMessage publishMessage) {
			this.publishMessage = publishMessage;
			return this;
		}

		public Builder context(ChannelContext context) {
			this.context = context;
			return this;
		}

		public Builder clientId(String clientId) {
			this.clientId = clientId;
			return this;
		}

		public Builder username(String username) {
			this.username = username;
			return this;
		}

		public Builder topic(String topic) {
			this.topic = topic;
			return this;
		}

		public Builder qos(MqttQoS qos) {
			this.qos = qos;
			return this;
		}

		public Builder dup(boolean dup) {
			this.dup = dup;
			return this;
		}

		public Builder retain(boolean retain) {
			this.retain = retain;
			return this;
		}

		public Builder payload(byte[] payload) {
			this.payload = payload;
			return this;
		}

		public Builder messageId(Integer messageId) {
			this.messageId = messageId;
			return this;
		}

		public Builder properties(MqttProperties properties) {
			this.properties = properties;
			return this;
		}

		public Builder peerHost(String peerHost) {
			this.peerHost = peerHost;
			return this;
		}

		public Builder nodeName(String nodeName) {
			this.nodeName = nodeName;
			return this;
		}

		public Builder timestamp(long timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		public Builder publishReceivedAt(Long publishReceivedAt) {
			this.publishReceivedAt = publishReceivedAt;
			return this;
		}

		public PublishContext build() {
			return new PublishContext(
				publishMessage, context, clientId, username, topic, qos, dup, retain,
				payload, messageId, properties, peerHost, nodeName, timestamp, publishReceivedAt
			);
		}
	}
}
