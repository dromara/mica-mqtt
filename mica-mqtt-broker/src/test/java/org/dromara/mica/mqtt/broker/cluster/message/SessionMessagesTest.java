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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the V3 Session*Message types — verifies that headers and
 * payloads round-trip through the cluster serialization format.
 *
 * @author L.cm
 */
class SessionMessagesTest {

	@Test
	void takeoverRequestRoundTrip() {
		SessionTakeoverRequestMessage request = new SessionTakeoverRequestMessage();
		request.setClientId("client-a");
		request.setAttemptId(42L);
		request.setTimeoutMs(3000L);

		net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage packet =
			ClusterMessageSerializer.toClusterData(request, "node-b");
		ClusterMessage decoded = ClusterMessageSerializer.fromClusterData(packet);

		assertInstanceOf(SessionTakeoverRequestMessage.class, decoded);
		SessionTakeoverRequestMessage rt = (SessionTakeoverRequestMessage) decoded;
		assertEquals("client-a", rt.getClientId());
		assertEquals(42L, rt.getAttemptId());
		assertEquals(3000L, rt.getTimeoutMs());
	}

	@Test
	void takeoverResponseOkCarriesSessionBytes() {
		SessionTakeoverResponseMessage response = new SessionTakeoverResponseMessage();
		response.setClientId("client-b");
		response.setAttemptId(7L);
		response.setStatus(SessionTakeoverResponseMessage.STATUS_OK);
		response.setSessionBytes("payload".getBytes(StandardCharsets.UTF_8));

		net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage packet =
			ClusterMessageSerializer.toClusterData(response, "node-c");
		ClusterMessage decoded = ClusterMessageSerializer.fromClusterData(packet);

		assertInstanceOf(SessionTakeoverResponseMessage.class, decoded);
		SessionTakeoverResponseMessage rt = (SessionTakeoverResponseMessage) decoded;
		assertEquals("client-b", rt.getClientId());
		assertEquals(7L, rt.getAttemptId());
		assertEquals(SessionTakeoverResponseMessage.STATUS_OK, rt.getStatus());
		assertTrue(rt.isOk());
		assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), rt.getSessionBytes());
	}

	@Test
	void takeoverResponseNotFoundHasEmptyPayload() {
		SessionTakeoverResponseMessage response = new SessionTakeoverResponseMessage();
		response.setClientId("client-c");
		response.setAttemptId(1L);
		response.setStatus(SessionTakeoverResponseMessage.STATUS_NOT_FOUND);

		net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage packet =
			ClusterMessageSerializer.toClusterData(response, "node-d");
		ClusterMessage decoded = ClusterMessageSerializer.fromClusterData(packet);

		assertInstanceOf(SessionTakeoverResponseMessage.class, decoded);
		SessionTakeoverResponseMessage rt = (SessionTakeoverResponseMessage) decoded;
		assertEquals(SessionTakeoverResponseMessage.STATUS_NOT_FOUND, rt.getStatus());
		assertFalse(rt.isOk());
		assertNotNull(rt.getSessionBytes());
		assertEquals(0, rt.getSessionBytes().length);
	}

	@Test
	void sessionMigratedNotifyRoundTrip() {
		SessionMigratedNotifyMessage msg = new SessionMigratedNotifyMessage();
		msg.setClientId("client-d");
		msg.setNewOwnerNodeId("node-new");
		msg.setPreviousOwnerNodeId("node-old");

		net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage packet =
			ClusterMessageSerializer.toClusterData(msg, "all");
		ClusterMessage decoded = ClusterMessageSerializer.fromClusterData(packet);

		assertInstanceOf(SessionMigratedNotifyMessage.class, decoded);
		SessionMigratedNotifyMessage rt = (SessionMigratedNotifyMessage) decoded;
		assertEquals("client-d", rt.getClientId());
		assertEquals("node-new", rt.getNewOwnerNodeId());
		assertEquals("node-old", rt.getPreviousOwnerNodeId());
	}

	@Test
	void sessionDeleteNotifyRoundTrip() {
		SessionDeleteNotifyMessage msg = new SessionDeleteNotifyMessage();
		msg.setClientId("client-e");

		net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage packet =
			ClusterMessageSerializer.toClusterData(msg, "all");
		ClusterMessage decoded = ClusterMessageSerializer.fromClusterData(packet);

		assertInstanceOf(SessionDeleteNotifyMessage.class, decoded);
		assertEquals("client-e", ((SessionDeleteNotifyMessage) decoded).getClientId());
	}
}