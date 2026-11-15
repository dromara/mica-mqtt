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

package org.dromara.mica.mqtt.core.server.store;


import net.dreamlu.mica.net.utils.cache.TimedCache;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.util.TopicUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * message store
 *
 * @author L.cm
 */
public class InMemoryMqttMessageStore implements IMqttMessageStore {
	/**
	 * 遗嘱消息 clientId: Message
	 */
	private final ConcurrentMap<String, Message> willStore = new ConcurrentHashMap<>();
	/**
	 * 保留消息 topic: Message。
	 * <p>
	 * 永久保留消息和带 TTL 的保留消息共用同一个 TimedCache：单 map 的 put/remove
	 * 是原子的，避免此前「先删另一个 map 再写当前 map」在并发更新同一 topic 时
	 * 两个 map 各留一份、订阅者收到重复 retained 的问题。timeout=0 的条目永不过期。
	 * </p>
	 */
	private final TimedCache<String, Message> retainStore = new TimedCache<>(
		TimeUnit.HOURS.toMillis(2),   // 默认 2 小时缓存
		TimeUnit.SECONDS.toMillis(1), // 定时 1s 清理一次缓存
		new ConcurrentHashMap<>()
	);

	@Override
	public boolean addWillMessage(String clientId, Message message) {
		willStore.put(clientId, message);
		return true;
	}

	@Override
	public boolean clearWillMessage(String clientId) {
		willStore.remove(clientId);
		return true;
	}

	@Override
	public boolean clearWillMessage(String clientId, Message expected) {
		if (expected == null) {
			return false;
		}
		AtomicBoolean removed = new AtomicBoolean();
		willStore.computeIfPresent(clientId, (key, current) -> {
			if (current == expected) {
				removed.set(true);
				return null;
			}
			return current;
		});
		return removed.get();
	}

	@Override
	public Message getWillMessage(String clientId) {
		return willStore.get(clientId);
	}

	@Override
	public boolean addRetainMessage(String topic, int timeout, Message message) {
		if (topic == null || message == null) {
			return false;
		}
		// timeout <= 0 表示永久保留（CacheObj ttl=0 永不过期）
		long ttl = timeout > 0 ? TimeUnit.SECONDS.toMillis(timeout) : 0;
		retainStore.put(topic, message, ttl);
		return true;
	}

	@Override
	public boolean clearRetainMessage(String topic) {
		retainStore.remove(topic);
		return true;
	}

	@Override
	public List<Message> getRetainMessage(String topicFilter) {
		List<Message> retainMessageList = new ArrayList<>();
		for (String topic : retainStore.keySet()) {
			if (TopicUtil.match(topicFilter, topic)) {
				Message message = retainStore.get(topic);
				// keySet 与 get 之间可能刚好过期
				if (message != null) {
					retainMessageList.add(message);
				}
			}
		}
		return retainMessageList;
	}

	@Override
	public void clean() throws IOException {
		this.willStore.clear();
		this.retainStore.clear();
		this.retainStore.close();
	}

}
