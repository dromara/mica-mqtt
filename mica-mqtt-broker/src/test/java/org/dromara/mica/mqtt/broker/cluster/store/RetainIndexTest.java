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

package org.dromara.mica.mqtt.broker.cluster.store;

import org.dromara.mica.mqtt.core.server.model.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RetainIndex}: memory skiplist + H2 durability.
 *
 * @author L.cm
 */
class RetainIndexTest {

	@TempDir
	Path tempDir;

	private H2MvStoreImpl engine;
	private RetainIndex retainIndex;

	@BeforeEach
	void setUp() {
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		retainIndex = new RetainIndex(engine);
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		engine.close();
	}

	private static Message msg(String topic, String payload, int qos) {
		Message m = new Message();
		m.setTopic(topic);
		m.setPayload(payload.getBytes(StandardCharsets.UTF_8));
		m.setQos(qos);
		m.setRetain(true);
		return m;
	}

	@Test
	void putAndExactMatch() {
		assertTrue(retainIndex.put("sensors/room1/temp", msg("sensors/room1/temp", "23.5", 1)));
		List<Message> result = retainIndex.match("sensors/room1/temp");
		assertEquals(1, result.size());
		assertEquals("23.5", new String(result.get(0).getPayload(), StandardCharsets.UTF_8));
	}

	@Test
	void putOverwrites() {
		retainIndex.put("t", msg("t", "v1", 1));
		retainIndex.put("t", msg("t", "v2", 1));
		assertEquals(1, retainIndex.size());
		List<Message> result = retainIndex.match("t");
		assertEquals("v2", new String(result.get(0).getPayload(), StandardCharsets.UTF_8));
	}

	@Test
	void wildcardPlus() {
		retainIndex.put("sensors/room1/temp", msg("sensors/room1/temp", "21", 0));
		retainIndex.put("sensors/room2/temp", msg("sensors/room2/temp", "22", 0));
		retainIndex.put("sensors/room3/humidity", msg("sensors/room3/humidity", "55", 0));
		List<Message> result = retainIndex.match("sensors/+/temp");
		assertEquals(2, result.size());
	}

	@Test
	void wildcardHash() {
		retainIndex.put("a/1", msg("a/1", "x", 0));
		retainIndex.put("a/2/b", msg("a/2/b", "y", 0));
		retainIndex.put("a/2/c/d", msg("a/2/c/d", "z", 0));
		List<Message> result = retainIndex.match("a/#");
		assertEquals(3, result.size());
	}

	@Test
	void remove() {
		retainIndex.put("t", msg("t", "v", 0));
		assertEquals(1, retainIndex.size());
		retainIndex.remove("t");
		assertEquals(0, retainIndex.size());
		assertTrue(retainIndex.match("t").isEmpty());
	}

	@Test
	void survivesRestart() throws InterruptedException {
		retainIndex.put("a/b", msg("a/b", "value", 1));
		engine.close();
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		retainIndex = new RetainIndex(engine);
		retainIndex.loadFromStore();
		assertEquals(1, retainIndex.size());
		assertEquals("value",
			new String(retainIndex.match("a/b").get(0).getPayload(), StandardCharsets.UTF_8));
	}

	@Test
	void rejectsOversizedPayload() {
		Message huge = new Message();
		huge.setTopic("big");
		huge.setPayload(new byte[RetainIndex.DEFAULT_MAX_PAYLOAD_BYTES + 1]);
		huge.setQos(0);
		assertFalse(retainIndex.put("big", huge));
		assertEquals(0, retainIndex.size());
	}

	@Test
	void noMatchReturnsEmpty() {
		retainIndex.put("foo", msg("foo", "v", 0));
		assertTrue(retainIndex.match("bar").isEmpty());
	}

	@Test
	void timedRetainExpiresAndStaysDeletedAfterRestart() throws Exception {
		retainIndex.put("timed/topic", msg("timed/topic", "value", 1), 1);
		assertEquals(1, retainIndex.match("timed/topic").size());
		Thread.sleep(1_100L);
		assertTrue(retainIndex.match("timed/topic").isEmpty());

		engine.close();
		engine = new H2MvStoreImpl();
		engine.open(tempDir);
		retainIndex = new RetainIndex(engine);
		retainIndex.loadFromStore();
		assertTrue(retainIndex.match("timed/topic").isEmpty());
	}
}
