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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * H2 MVStore-backed implementation of {@link SharedSubStore}.
 * <p>
 * Persists each {@link SharedSubStore.SharedSubGroup} under key
 * {@code "shared_sub:<groupName>"} in a dedicated {@link MVMap} inside the
 * shared {@link H2MvStoreImpl} engine.
 * </p>
 * <h3>Value format</h3>
 * <pre>
 *   [2 bytes]  topicFilter length (short)
 *   [N bytes]  topicFilter (UTF-8)
 *   [4 bytes]  member count (int)
 *   for each member:
 *     [2 bytes] clientId length
 *     [N bytes] clientId (UTF-8)
 *   [2 bytes]  ownerNodeId length, or -1 if null
 *   [N bytes]  ownerNodeId (UTF-8)
 *   [2 bytes]  backupNodeId length, or -1 if null
 *   [N bytes]  backupNodeId (UTF-8)
 *   [8 bytes]  version (long)
 *   [8 bytes]  updatedAt (long)
 * </pre>
 *
 * @author L.cm
 * @since 2.6.0
 */
public class H2SharedSubStore implements SharedSubStore {
	private static final Logger logger = LoggerFactory.getLogger(H2SharedSubStore.class);

	static final String MAP_NAME = "mica_mqtt_shared_sub";
	private static final String KEY_PREFIX = "shared_sub:";

	private final LocalKvStore store;

	public H2SharedSubStore(H2MvStoreImpl engine) {
		this((LocalKvStore) engine);
	}

	public H2SharedSubStore(LocalKvStore store) {
		this.store = store;
	}

	@Override
	public void save(SharedSubGroup group) {
		String key = buildKey(group.getGroupName());
		byte[] value = serialize(group);
		store.put(key, value);
	}

	@Override
	public boolean updateIfVersion(SharedSubGroup group, long expectedVersion) {
		String key = buildKey(group.getGroupName());
		SharedSubGroup current = get(group.getGroupName());
		if (current != null && current.getVersion() != expectedVersion) {
			return false;
		}
		save(group);
		return true;
	}

	@Override
	public void delete(String groupName) {
		String key = buildKey(groupName);
		store.delete(key);
	}

	@Override
	public SharedSubGroup get(String groupName) {
		String key = buildKey(groupName);
		byte[] value = store.get(key);
		if (value == null) {
			return null;
		}
		return deserialize(groupName, value);
	}

	@Override
	public List<SharedSubGroup> listAll() {
		List<LocalKvStore.KeyValue> entries = store.scan(KEY_PREFIX);
		List<SharedSubGroup> groups = new ArrayList<>(entries.size());
		for (LocalKvStore.KeyValue kv : entries) {
			String name = kv.getKey().substring(KEY_PREFIX.length());
			groups.add(deserialize(name, kv.getValue()));
		}
		return groups;
	}

	static String buildKey(String groupName) {
		return KEY_PREFIX + groupName;
	}

	static byte[] serialize(SharedSubGroup group) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 DataOutputStream dos = new DataOutputStream(baos)) {
			writeNullableString(dos, group.getTopicFilter());
			List<String> members = group.getMembers() == null ? java.util.Collections.emptyList() : group.getMembers();
			dos.writeInt(members.size());
			for (String m : members) {
				writeNullableString(dos, m);
			}
			writeNullableString(dos, group.getOwnerNodeId());
			writeNullableString(dos, group.getBackupNodeId());
			dos.writeLong(group.getVersion());
			dos.writeLong(group.getUpdatedAt());
			dos.flush();
			return baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize SharedSubGroup: " + group.getGroupName(), e);
		}
	}

	static SharedSubGroup deserialize(String groupName, byte[] value) {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(value);
			 DataInputStream dis = new DataInputStream(bais)) {
			SharedSubGroup group = new SharedSubGroup();
			group.setGroupName(groupName);
			group.setTopicFilter(readNullableString(dis));
			int memberCount = dis.readInt();
			List<String> members = new ArrayList<>(memberCount);
			for (int i = 0; i < memberCount; i++) {
				members.add(readNullableString(dis));
			}
			group.setMembers(members);
			group.setOwnerNodeId(readNullableString(dis));
			group.setBackupNodeId(readNullableString(dis));
			group.setVersion(dis.readLong());
			group.setUpdatedAt(dis.readLong());
			return group;
		} catch (IOException e) {
			logger.warn("[SharedSubStore] Failed to deserialize group: {}", groupName, e);
			return null;
		}
	}

	private static void writeNullableString(DataOutputStream dos, String s) throws IOException {
		if (s == null) {
			dos.writeShort(-1);
			return;
		}
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		dos.writeShort(bytes.length);
		dos.write(bytes);
	}

	private static String readNullableString(DataInputStream dis) throws IOException {
		short len = dis.readShort();
		if (len == -1) {
			return null;
		}
		byte[] bytes = new byte[len];
		dis.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}
}