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
	 * MQTT 5.0 Receive Maximum 默认值。
	 */
	int MQTT5_DEFAULT_RECEIVE_MAXIMUM = 0xffff;

	/**
	 * 添加订阅存储。
	 * <p>
	 * 委托给带完整 MQTT 5.0 订阅选项的 {@link #addSubscribe(TopicFilter, String, int, boolean, boolean, int)}，
	 * {@code retainAsPublished} 取 {@code false}，{@code retainHandling} 取 {@code 0}，以保持向后兼容。
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    客户端 Id
	 * @param mqttQoS     MqttQoS
	 * @param noLocal     MQTT 5.0 No Local 标志
	 * @return true 表示此前不存在相同 clientId 和 topicFilter 的订阅
	 */
	default boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal) {
		return this.addSubscribe(topicFilter, clientId, mqttQoS, noLocal, false, 0);
	}

	/**
	 * 添加包含完整 MQTT 5.0 订阅选项的订阅存储。
	 * <p>
	 * 默认实现用于兼容已有的自定义 SessionManager；实现方可重写以原子判断新订阅并持久化完整选项。
	 *
	 * @param topicFilter       topicFilter
	 * @param clientId          客户端 Id
	 * @param mqttQoS           MqttQoS
	 * @param noLocal           No Local 标志
	 * @param retainAsPublished Retain As Published 标志
	 * @param retainHandling    Retain Handling，取值 0、1、2
	 * @return true 表示此前不存在相同 clientId 和 topicFilter 的订阅
	 */
	boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal, boolean retainAsPublished, int retainHandling);

	/**
	 * 添加包含完整 MQTT 5.0 订阅选项 + Subscription Identifier 的订阅存储。
	 * <p>
	 * spec 3.8.4：客户端在 SUBSCRIBE 的 Subscription Identifier 属性中携带 varint，
	 * 服务端需要把该 id 关联到该 (clientId, topicFilter) 订阅；当 PUBLISH 命中该订阅时，
	 * 应在 PUBLISH 的 properties 中回带同一个 Subscription Identifier。
	 * <p>
	 * 默认实现走"无 subscriptionId"重载以保持向后兼容；实现方建议重写以持久化该字段。
	 *
	 * @param topicFilter       topicFilter
	 * @param clientId          客户端 Id
	 * @param mqttQoS           MqttQoS
	 * @param noLocal           No Local 标志
	 * @param retainAsPublished Retain As Published 标志
	 * @param retainHandling    Retain Handling，取值 0、1、2
	 * @param subscriptionId    Subscription Identifier（0 表示未设置）
	 * @return true 表示此前不存在相同 clientId 和 topicFilter 的订阅
	 */
	default boolean addSubscribe(TopicFilter topicFilter, String clientId, int mqttQoS, boolean noLocal,
								 boolean retainAsPublished, int retainHandling, int subscriptionId) {
		return this.addSubscribe(topicFilter, clientId, mqttQoS, noLocal, retainAsPublished, retainHandling);
	}

	/**
	 * 添加订阅存储
	 *
	 * @param topicFilter topicFilter
	 * @param clientId    客户端 Id
	 * @param mqttQoS     MqttQoS
	 * @return true 表示此前不存在相同 clientId 和 topicFilter 的订阅
	 */
	default boolean addSubscribe(String topicFilter, String clientId, int mqttQoS) {
		return this.addSubscribe(new TopicFilter(topicFilter), clientId, mqttQoS, false);
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
	 * 在客户端 Receive Maximum 范围内原子地添加发布过程存储。
	 *
	 * @param clientId       clientId
	 * @param messageId      messageId
	 * @param pendingPublish MqttPendingPublish
	 * @return 添加成功返回 {@code true}，已达到 Receive Maximum 返回 {@code false}
	 */
	default boolean tryAddPendingPublish(String clientId, int messageId, MqttPendingPublish pendingPublish) {
		synchronized (this) {
			int receiveMaximum = getClientReceiveMaximum(clientId);
			if (receiveMaximum < 1 || getPendingPublishCount(clientId) >= receiveMaximum) {
				return false;
			}
			addPendingPublish(clientId, messageId, pendingPublish);
			return true;
		}
	}

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
	 * Marks a QoS 2 outbound delivery as having entered the PUBREL phase.
	 * Persistent session managers can override this hook for reconnect recovery.
	 */
	default void markPendingPublishPubRel(String clientId, int messageId) {
	}

	/**
	 * 将 PUBLISH 加入客户端维度的"待发送"队列（PR7 / spec 3.3.4 Receive Maximum）。
	 * <p>
	 * 当客户端当前的 in-flight 数达到 Receive Maximum 上限时，服务端应当缓存后续要发送的 QoS>0
	 * PUBLISH，而不是直接丢弃；待 PUBACK/PUBCOMP 腾出位置后再出队。
	 *
	 * @param clientId clientId
	 * @param entry    待发送快照
	 */
	default void addPendingPublishBacklog(String clientId, org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry entry) {
		// 默认 no-op：自定义 SessionManager 升级后才有意义，InMemoryMqttSessionManager 已重写
	}

	/**
	 * 从客户端维度的"待发送"队列头部取出一条（出队）。
	 *
	 * @param clientId clientId
	 * @return 待发送快照；无则返回 {@code null}
	 */
	default org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry pollPendingPublishBacklog(String clientId) {
		return null;
	}

	/**
	 * 客户端维度的"待发送"队列长度。
	 *
	 * @param clientId clientId
	 * @return backlog 大小
	 */
	default int getPendingPublishBacklogSize(String clientId) {
		return 0;
	}

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
	 * 记录客户端在 CONNECT 中声明的 Receive Maximum。
	 *
	 * @param clientId       clientId
	 * @param receiveMaximum 客户端接收上限，合法范围为 1-65535
	 */
	default void setClientReceiveMaximum(String clientId, int receiveMaximum) {
		// default no-op for backward compatibility
	}

	/**
	 * 获取客户端声明的 Receive Maximum。
	 * <p>
	 * 未声明时返回 MQTT 5.0 规范默认值 65535。
	 *
	 * @param clientId clientId
	 * @return receiveMaximum
	 */
	default int getClientReceiveMaximum(String clientId) {
		return MQTT5_DEFAULT_RECEIVE_MAXIMUM;
	}

	/**
	 * 获取客户端当前 QoS1/QoS2 下行 in-flight 数量。
	 *
	 * @param clientId clientId
	 * @return in-flight 数
	 */
	default int getPendingPublishCount(String clientId) {
		return 0;
	}

	// ----------------- PR9（P2.8）Session Expiry Interval -----------------

	/**
	 * 记录客户端在 CONNECT 中声明的 Session Expiry Interval（秒，spec 3.1.2.11.4）。
	 *
	 * @param clientId             clientId
	 * @param sessionExpirySeconds Session Expiry Interval，0 表示立即过期
	 * @param cleanStart           CONNECT 中的 Clean Start 标志
	 */
	default void setSessionExpiryInterval(String clientId, int sessionExpirySeconds, boolean cleanStart) {
		// 默认 no-op：InMemoryMqttSessionManager 已重写
	}

	/**
	 * 获取 Session Expiry Interval（秒）。
	 *
	 * @param clientId clientId
	 * @return Session Expiry Interval（秒），未设置返回 0
	 */
	default int getSessionExpiryInterval(String clientId) {
		return 0;
	}

	/**
	 * 获取 Clean Start 标志。
	 *
	 * @param clientId clientId
	 * @return Clean Start 标志
	 */
	default boolean isCleanStart(String clientId) {
		return true;
	}

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
