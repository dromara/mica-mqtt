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
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainQueryMessageTest {

	@Test
	void requestAndResponseRoundTrip() {
		RetainQueryMessage request = new RetainQueryMessage();
		request.setRequestId("node-1:7");
		request.setTopicFilter("sensors/+/temp");
		RetainQueryMessage decodedRequest = ClusterMessageSerializer.fromClusterData(
			ClusterMessageSerializer.toClusterData(request, "node-1"));
		assertEquals("node-1:7", decodedRequest.getRequestId());
		assertEquals("sensors/+/temp", decodedRequest.getTopicFilter());

		Message retained = new Message();
		retained.setTopic("sensors/a/temp");
		retained.setPayload(new byte[]{1, 2});
		retained.setQos(1);
		retained.setRetain(true);
		retained.setMessageType(MessageType.DOWN_STREAM);
		RetainQueryMessage response = new RetainQueryMessage();
		response.setRequestId("node-1:7");
		response.setTopicFilter("sensors/+/temp");
		response.setResponse(true);
		response.setMessages(Collections.singletonList(retained));

		RetainQueryMessage decodedResponse = ClusterMessageSerializer.fromClusterData(
			ClusterMessageSerializer.toClusterData(response, "node-2"));
		assertTrue(decodedResponse.isResponse());
		assertEquals(1, decodedResponse.getMessages().size());
		assertEquals("sensors/a/temp", decodedResponse.getMessages().get(0).getTopic());
		assertArrayEquals(new byte[]{1, 2}, decodedResponse.getMessages().get(0).getPayload());
	}

	@Test
	void heartbeatRoundTrip() {
		ClusterDataMessage encoded =
			ClusterMessageSerializer.toClusterData(new HeartbeatMessage(), "node-1");
		assertTrue(encoded.getPayload().length > 1);
		assertTrue(encoded.getHeaders().isEmpty());
		ClusterMessage decoded = ClusterMessageSerializer.fromClusterData(encoded);
		assertEquals(ClusterMessageType.HEARTBEAT, decoded.getType());
		assertEquals(21, decoded.getType().getCode());
	}

	@Test
	void legacyTransportHeadersRemainReadable() {
		Map<String, String> headers = new HashMap<>();
		headers.put(ClusterMessageSerializer.HEADER_TYPE,
			String.valueOf(ClusterMessageType.HEARTBEAT.getCode()));
		headers.put(ClusterMessageSerializer.HEADER_SOURCE_NODE, "legacy-node");
		ClusterDataMessage legacy = new ClusterDataMessage(1L, headers, new byte[0]);

		ClusterMessage decoded = ClusterMessageSerializer.fromClusterData(legacy);
		assertEquals(ClusterMessageType.HEARTBEAT, decoded.getType());
		assertEquals("legacy-node", ClusterMessageSerializer.getSourceNode(legacy));
	}

	@Test
	void completeSubscriptionOptionsRoundTrip() {
		Subscribe subscription = new Subscribe(
				"devices/+", "client-5", 2, true, true, 2, 321);
		byte[] encoded = ClusterMessageSerializer.serializeSubscriptions(
			Collections.singletonList(subscription));

		Subscribe decoded =
			ClusterMessageSerializer.deserializeSubscriptions(encoded).get(0);
		assertEquals(2, decoded.getMqttQoS());
		assertTrue(decoded.isNoLocal());
		assertTrue(decoded.isRetainAsPublished());
		assertEquals(2, decoded.getRetainHandling());
		assertEquals(321, decoded.getSubscriptionId());
	}

	@Test
	void legacySubscriptionFormatRemainsReadable() {
		byte[] topic = "legacy/+".getBytes(StandardCharsets.UTF_8);
		byte[] client = "legacy-client".getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(4 + 2 + topic.length + 2 + client.length + 2);
		buffer.putInt(1);
		buffer.putShort((short) topic.length).put(topic);
		buffer.putShort((short) client.length).put(client);
		buffer.put((byte) 1).put((byte) 1);

		Subscribe decoded = ClusterMessageSerializer.deserializeSubscriptions(buffer.array()).get(0);
		assertEquals("legacy/+", decoded.getTopicFilter());
		assertEquals("legacy-client", decoded.getClientId());
		assertEquals(1, decoded.getMqttQoS());
		assertTrue(decoded.isNoLocal());
		assertEquals(0, decoded.getSubscriptionId());
	}
}
