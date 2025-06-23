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

import java.io.IOException;
import java.util.List;

/**
 * message store
 *
 * @author L.cm
 */
public interface IMqttMessageStore {

	/**
	 * 存储 clientId 的遗嘱消息
	 *
	 * @param clientId clientId
	 * @param message  message
	 * @return boolean
	 */
	boolean addWillMessage(String clientId, Message message);

	/**
	 * 清理该 clientId 的遗嘱消息
	 *
	 * @param clientId clientId
	 * @return boolean
	 */
	boolean clearWillMessage(String clientId);

	/**
	 * 获取 will 消息
	 *
	 * @param clientId clientId
	 * @return Message
	 */
	Message getWillMessage(String clientId);

	/**
	 * 存储 retain 消息
	 *
	 * @param topic   topic
	 * @param timeout timeout
	 * @param message message
	 * @return boolean
	 */
	boolean addRetainMessage(String topic, int timeout, Message message);

	/**
	 * 清理该 topic 的 retain 消息
	 *
	 * @param topic topic
	 * @return boolean
	 */
	boolean clearRetainMessage(String topic);

	/**
	 * 获取所有 retain 消息
	 *
	 * @param topicFilter topicFilter
	 * @return Message
	 */
	List<Message> getRetainMessage(String topicFilter);

	/**
	 * 清除所有，并释放资源
	 */
	default void clean() throws IOException {

	}
}
