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
	 * 新连接（未携带遗嘱）建立时清理旧遗嘱。
	 * <p>
	 * 集群实现必须仍在本地推进清理版本（压制迟到的旧遗嘱），但可以延迟传播：
	 * 版本屏障随上线通知（CLIENT_CONNECT）一起同步，避免每次无遗嘱连接都额外
	 * 广播一条 WILL_MESSAGE。单机实现等价于 {@link #clearWillMessage(String)}。
	 * </p>
	 *
	 * @param clientId clientId
	 * @return boolean
	 */
	default boolean clearWillMessageOnConnect(String clientId) {
		return clearWillMessage(clientId);
	}

	/**
	 * 仅当当前遗嘱仍属于指定连接时清除。并发存储实现应覆盖此方法以保证原子性。
	 *
	 * @param clientId 客户端标识
	 * @param expected 连接持有的遗嘱消息
	 * @return 是否清除
	 */
	default boolean clearWillMessage(String clientId, Message expected) {
		if (expected == null) {
			return false;
		}
		synchronized (this) {
			if (getWillMessage(clientId) != expected) {
				return false;
			}
			return clearWillMessage(clientId);
		}
	}

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
