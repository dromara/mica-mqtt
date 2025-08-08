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

package org.dromara.mica.mqtt.core.server.session;

import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.server.model.Subscribe;

import java.util.List;

/**
 * session 管理，不封装 MqttSession 实体，方便 redis 等集群处理
 *
 * @author L.cm
 */
public interface IMqttSessionManager {

	/**
	 * 添加订阅存储
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    客户端 Id
	 * @param mqttQoS     MqttQoS
	 */
	void addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS);

	/**
	 * 添加订阅存储
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    客户端 Id
	 * @param mqttQoS     MqttQoS
	 */
	default void addSubscribe(String topicFilter, String clientId, int mqttQoS) {
		this.addSubscribe(new TopicFilter(topicFilter), clientId, mqttQoS);
	}

	/**
	 * 删除订阅
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    客户端 Id
	 */
	void removeSubscribe(String topicFilter, String clientId);

	/**
	 * 查找订阅 qos 信息
	 *
	 * @param topicName topicName
	 * @param clientId  客户端 Id
	 * @return 订阅存储列表
	 */
	Byte searchSubscribe(String topicName, String clientId);

	/**
	 * 查找订阅信息
	 *
	 * @param topicName topicName
	 * @return 订阅存储列表
	 */
	List<Subscribe> searchSubscribe(String topicName);

	/**
	 * 获取设备订阅
	 *
	 * @param clientId clientId
	 * @return 订阅列表
	 */
	List<Subscribe> getSubscriptions(String clientId);

	/**
	 * 添加发布过程存储
	 *
	 * @param clientId       clientId
	 * @param messageId      messageId
	 * @param pendingPublish MqttPendingPublish
	 */
	void addPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish);

	/**
	 * 获取发布过程存储
	 *
	 * @param clientId  clientId
	 * @param messageId messageId
	 * @return MqttPendingPublish
	 */
	MqttPendingPublish getPendingPublish(String clientId, int messageId);

	/**
	 * 删除发布过程中的存储
	 *
	 * @param clientId  clientId
	 * @param messageId messageId
	 */
	void removePendingPublish(String clientId, int messageId);

	/**
	 * 添加发布过程存储
	 *
	 * @param clientId           clientId
	 * @param messageId          messageId
	 * @param pendingQos2Publish MqttPendingQos2Publish
	 */
	void addPendingQos2Publish(String clientId, int messageId, MqttPendingQos2Publish pendingQos2Publish);

	/**
	 * 获取发布过程存储
	 *
	 * @param clientId  clientId
	 * @param messageId messageId
	 * @return MqttPendingQos2Publish
	 */
	MqttPendingQos2Publish getPendingQos2Publish(String clientId, int messageId);

	/**
	 * 删除发布过程中的存储
	 *
	 * @param clientId  clientId
	 * @param messageId messageId
	 */
	void removePendingQos2Publish(String clientId, int messageId);

	/**
	 * 生成消息 Id
	 *
	 * @param clientId clientId
	 * @return messageId
	 */
	int getPacketId(String clientId);

	/**
	 * 判断是否存在 session
	 *
	 * @param clientId clientId
	 * @return 是否存在 session
	 */
	boolean hasSession(String clientId);

	/**
	 * 标记 session 超时时间
	 *
	 * @param clientId             clientId
	 * @param sessionExpirySeconds sessionExpirySeconds
	 * @return 是否成功
	 */
	boolean expire(String clientId, int sessionExpirySeconds);

	/**
	 * 激活 session，标记 expire 的 session 为永久
	 *
	 * @param clientId clientId
	 * @return 是否成功
	 */
	boolean active(String clientId);

	/**
	 * 清除 session
	 *
	 * @param clientId clientId
	 */
	void remove(String clientId);

	/**
	 * 清理
	 */
	void clean();

}
