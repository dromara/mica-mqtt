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
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.serializer.DefaultMessageSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Request/response message used to query retained-message shards. */
public class RetainQueryMessage implements ClusterMessage {
	private static final String HEADER_REQUEST_ID = "requestId";
	private static final String HEADER_RESPONSE = "response";
	private static final int MAX_MESSAGES = 100_000;

	private String requestId;
	private String topicFilter;
	private boolean response;
	private List<Message> messages = Collections.emptyList();

	@Override
	public ClusterMessageType getType() {
		return ClusterMessageType.RETAIN_QUERY;
	}

	@Override
	public void toClusterData(Map<String, String> headers) {
		headers.put(HEADER_REQUEST_ID, requestId);
		headers.put(ClusterMessageSerializer.HEADER_TOPIC, topicFilter);
		headers.put(HEADER_RESPONSE, String.valueOf(response));
	}

	@Override
	public byte[] toPayload() {
		if (!response || messages.isEmpty()) {
			return new byte[0];
		}
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(messages.size());
			for (Message message : messages) {
				byte[] encoded = DefaultMessageSerializer.INSTANCE.serialize(message);
				out.writeInt(encoded.length);
				out.write(encoded);
			}
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to encode retain query response", e);
		}
	}

	@Override
	public void fromClusterData(ClusterDataMessage message) {
		requestId = message.getHeader(HEADER_REQUEST_ID);
		topicFilter = message.getHeader(ClusterMessageSerializer.HEADER_TOPIC);
		response = Boolean.parseBoolean(message.getHeader(HEADER_RESPONSE));
		byte[] payload = message.getPayload();
		if (!response || payload == null || payload.length == 0) {
			messages = Collections.emptyList();
			return;
		}
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
			int count = in.readInt();
			if (count < 0 || count > MAX_MESSAGES) {
				throw new IOException("Invalid retain result count: " + count);
			}
			List<Message> decoded = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				int length = in.readInt();
				if (length < 0 || length > in.available()) {
					throw new IOException("Invalid retain message length: " + length);
				}
				byte[] encoded = new byte[length];
				in.readFully(encoded);
				Message retain = DefaultMessageSerializer.INSTANCE.deserialize(encoded);
				if (retain != null) {
					decoded.add(retain);
				}
			}
			messages = decoded;
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid retain query response", e);
		}
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public String getTopicFilter() {
		return topicFilter;
	}

	public void setTopicFilter(String topicFilter) {
		this.topicFilter = topicFilter;
	}

	public boolean isResponse() {
		return response;
	}

	public void setResponse(boolean response) {
		this.response = response;
	}

	public List<Message> getMessages() {
		return messages;
	}

	public void setMessages(List<Message> messages) {
		this.messages = messages == null ? Collections.emptyList() : messages;
	}
}
