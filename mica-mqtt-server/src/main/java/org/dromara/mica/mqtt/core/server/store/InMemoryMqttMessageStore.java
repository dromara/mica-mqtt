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


import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.tio.utils.cache.TimedCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

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
	 * 保持消息 topic: Message
	 * 带有有效期的保持消息 topic: Message
	 */
	private final ConcurrentMap<String, Message> retainStore = new ConcurrentHashMap<>();
	/**
	 * 带有有效期的保持消息 topic: Message
	 */
	private final TimedCache<String, Message> timedRetainStore = new TimedCache<>(
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
	public Message getWillMessage(String clientId) {
		return willStore.get(clientId);
	}

	@Override
	public boolean addRetainMessage(String topic, long timeout, Message message) {
		if (timeout <= 0) {
			retainStore.put(topic, message);
		} else {
			timedRetainStore.put(topic, message, TimeUnit.SECONDS.toMillis(timeout));
		}
		return true;
	}

	@Override
	public boolean clearRetainMessage(String topic) {
		retainStore.remove(topic);
		timedRetainStore.remove(topic);
		return true;
	}

	@Override
	public List<Message> getRetainMessage(String topicFilter) {
		List<Message> retainMessageList = new ArrayList<>();
		for (String topic : retainStore.keySet()) {
			if (TopicUtil.match(topicFilter, topic)) {
				retainMessageList.add(retainStore.get(topic));
			}
		}
		for (String topic : timedRetainStore.keySet()) {
			if (TopicUtil.match(topicFilter, topic)) {
				retainMessageList.add(timedRetainStore.get(topic));
			}
		}
		return retainMessageList;
	}

	@Override
	public void clean() throws IOException {
		this.willStore.clear();
		this.retainStore.clear();
		this.timedRetainStore.clear();
		this.timedRetainStore.close();
	}

}
