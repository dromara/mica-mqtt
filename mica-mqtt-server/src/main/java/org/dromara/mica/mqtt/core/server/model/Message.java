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

import org.dromara.mica.mqtt.core.server.enums.MessageType;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * 消息模型，用于存储
 *
 * @author L.cm
 */
public class Message implements Serializable {

	/**
	 * 事件触发所在节点
	 */
	private String node;
	/**
	 * MQTT 消息 ID
	 */
	private Integer id;
	/**
	 * 消息来源 客户端 id
	 */
	private String fromClientId;
	/**
	 * 消息来源 用户名
	 */
	private String fromUsername;
	/**
	 * 消息目的 Client ID，主要是在遗嘱消息用
	 */
	private String clientId;
	/**
	 * 消息目的用户名，主要是在遗嘱消息用
	 */
	private String username;
	/**
	 * topic
	 */
	private String topic;
	/**
	 * 消息类型
	 */
	private MessageType messageType;
	/**
	 * 是否重发
	 */
	private boolean dup;
	/**
	 * qos
	 */
	private int qos;
	/**
	 * retain
	 */
	private boolean retain;
	/**
	 * 消息内容
	 */
	private byte[] payload;
	/**
	 * 客户端的 IPAddress
	 */
	private String peerHost;
	/**
	 * 存储时间
	 */
	private long timestamp;
	/**
	 * PUBLISH 消息到达 Broker 的时间 (ms)
	 */
	private Long publishReceivedAt;

	public String getNode() {
		return node;
	}

	public void setNode(String node) {
		this.node = node;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getFromClientId() {
		return fromClientId;
	}

	public void setFromClientId(String fromClientId) {
		this.fromClientId = fromClientId;
	}

	public String getFromUsername() {
		return fromUsername;
	}

	public void setFromUsername(String fromUsername) {
		this.fromUsername = fromUsername;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public void setMessageType(MessageType messageType) {
		this.messageType = messageType;
	}

	public boolean isDup() {
		return dup;
	}

	public void setDup(boolean dup) {
		this.dup = dup;
	}

	public int getQos() {
		return qos;
	}

	public void setQos(int qos) {
		this.qos = qos;
	}

	public boolean isRetain() {
		return retain;
	}

	public void setRetain(boolean retain) {
		this.retain = retain;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	public String getPeerHost() {
		return peerHost;
	}

	public void setPeerHost(String peerHost) {
		this.peerHost = peerHost;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public Long getPublishReceivedAt() {
		return publishReceivedAt;
	}

	public void setPublishReceivedAt(Long publishReceivedAt) {
		this.publishReceivedAt = publishReceivedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Message message = (Message) o;
		return dup == message.dup && qos == message.qos && retain == message.retain && timestamp == message.timestamp && Objects.equals(node, message.node) && Objects.equals(id, message.id) && Objects.equals(fromClientId, message.fromClientId) && Objects.equals(fromUsername, message.fromUsername) && Objects.equals(clientId, message.clientId) && Objects.equals(username, message.username) && Objects.equals(topic, message.topic) && messageType == message.messageType && Arrays.equals(payload, message.payload) && Objects.equals(peerHost, message.peerHost) && Objects.equals(publishReceivedAt, message.publishReceivedAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(node, id, fromClientId, fromUsername, clientId, username, topic, messageType, dup, qos, retain, Arrays.hashCode(payload), peerHost, timestamp, publishReceivedAt);
	}

	@Override
	public String toString() {
		return "Message{" +
			"node='" + node + '\'' +
			", id=" + id +
			", fromClientId='" + fromClientId + '\'' +
			", fromUsername='" + fromUsername + '\'' +
			", clientId='" + clientId + '\'' +
			", username='" + username + '\'' +
			", topic='" + topic + '\'' +
			", messageType=" + messageType +
			", dup=" + dup +
			", qos=" + qos +
			", retain=" + retain +
			", payload=" + payload +
			", peerHost='" + peerHost + '\'' +
			", timestamp=" + timestamp +
			", publishReceivedAt=" + publishReceivedAt +
			'}';
	}

}
