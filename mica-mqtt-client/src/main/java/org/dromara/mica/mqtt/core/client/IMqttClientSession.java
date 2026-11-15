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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.MqttPendingQos2Publish;

import java.util.Collections;
import java.util.List;

/**
 * 客户端 session
 *
 * @author L.cm
 */
public interface IMqttClientSession {

	/**
	 * 获取 packetId
	 *
	 * @return packetId
	 */
	int getPacketId();

	/**
	 * 添加订阅
	 *
	 * @param messageId           messageId
	 * @param pendingSubscription MqttPendingSubscription
	 */
	void addPaddingSubscribe(int messageId, MqttPendingSubscription pendingSubscription);

	/**
	 * 获取过程订阅
	 *
	 * @param messageId messageId
	 * @return MqttPendingSubscription
	 */
	MqttPendingSubscription getPaddingSubscribe(int messageId);

	/**
	 * 移除过程订阅
	 *
	 * @param topicFilters topicFilter 集合
	 */
	void removePaddingSubscribes(List<String> topicFilters);

	/**
	 * 新的订阅意图到达时，撤销对应 topic 的“已取消”标记。
	 * <p>
	 * 避免部分退订后、旧 SUBACK 到达时补发的 UNSUBSCRIBE 误删刚建立的新订阅。
	 * 默认实现为空，便于外部自定义 session 保持兼容。
	 * </p>
	 *
	 * @param topicFilters 重新订阅的 topic 集合
	 */
	default void restoreCancelledSubscriptions(List<String> topicFilters) {
	}

	/**
	 * 删除过程订阅
	 *
	 * @param messageId messageId
	 * @return MqttPendingSubscription
	 */
	MqttPendingSubscription removePaddingSubscribe(int messageId);

	/**
	 * 添加订阅
	 *
	 * @param subscription MqttClientSubscription
	 */
	void addSubscription(MqttClientSubscription subscription);

	/**
	 * 添加启动时的临时订阅
	 *
	 * @param topicFilters    topicFilters
	 * @param qos             MqttQoS
	 * @param messageListener IMqttClientMessageListener
	 */
	default void addSubscriptionList(String[] topicFilters, MqttQoS qos, IMqttClientMessageListener messageListener) {
		addSubscriptionList(topicFilters, MqttSubscriptionOption.from(qos), messageListener);
	}

	/**
	 * 添加启动时的临时订阅
	 *
	 * @param topicFilters    topicFilters
	 * @param option          MqttSubscriptionOption
	 * @param messageListener IMqttClientMessageListener
	 */
	default void addSubscriptionList(String[] topicFilters, MqttSubscriptionOption option, IMqttClientMessageListener messageListener) {
		for (String topicFilter : topicFilters) {
			addSubscription(new MqttClientSubscription(topicFilter, option, messageListener));
		}
	}

	/**
	 * 添加订阅
	 *
	 * @param subscriptionList MqttClientSubscription 集合
	 */
	default void addSubscriptionList(List<MqttClientSubscription> subscriptionList) {
		for (MqttClientSubscription subscription : subscriptionList) {
			addSubscription(subscription);
		}
	}

	/**
	 * 判断是否已经订阅过
	 *
	 * @param clientSubscription MqttClientSubscription
	 * @return 是否已经订阅过
	 */
	boolean isSubscribed(MqttClientSubscription clientSubscription);

	/**
	 * 获取并清除订阅
	 *
	 * @return 订阅集合
	 */
	List<MqttClientSubscription> getSubscriptions();

	/**
	 * 获取匹配的订阅
	 *
	 * @param topicName topicName
	 * @return 订阅信息集合
	 */
	List<MqttClientSubscription> getMatchedSubscription(String topicName);

	/**
	 * 删除订阅过程消息
	 *
	 * @param topicFilters topicFilter 集合
	 */
	void removeSubscriptions(List<String> topicFilters);

	/**
	 * 添加取消订阅过程消息
	 *
	 * @param messageId             messageId
	 * @param pendingUnSubscription MqttPendingUnSubscription
	 */
	void addPaddingUnSubscribe(int messageId, MqttPendingUnSubscription pendingUnSubscription);

	/**
	 * 获取取消订阅过程消息
	 *
	 * @param messageId messageId
	 * @return MqttPendingUnSubscription
	 */
	MqttPendingUnSubscription getPaddingUnSubscribe(int messageId);

	/**
	 * 删除取消订阅过程消息
	 *
	 * @param messageId messageId
	 * @return MqttPendingUnSubscription
	 */
	MqttPendingUnSubscription removePaddingUnSubscribe(int messageId);

	/**
	 * 添加过程消息
	 *
	 * @param messageId      messageId
	 * @param pendingPublish MqttPendingPublish
	 */
	void addPendingPublish(int messageId, MqttPendingPublish pendingPublish);

	/**
	 * 获取过程消息
	 *
	 * @param messageId messageId
	 * @return MqttPendingPublish
	 */
	MqttPendingPublish getPendingPublish(int messageId);

	/**
	 * 删除过程消息
	 *
	 * @param messageId messageId
	 * @return MqttPendingPublish
	 */
	MqttPendingPublish removePendingPublish(int messageId);

	/**
	 * 获取等待确认的发布消息快照，用于连接恢复后重传。
	 * 自定义会话实现应覆盖此方法以支持 inflight 恢复。
	 *
	 * @return 等待确认的发布消息
	 */
	default List<MqttPendingPublish> getPendingPublishes() {
		return Collections.emptyList();
	}

	/**
	 * 添加 qos2 过程消息
	 *
	 * @param messageId          messageId
	 * @param pendingQos2Publish MqttPendingQos2Publish
	 */
	void addPendingQos2Publish(int messageId, MqttPendingQos2Publish pendingQos2Publish);

	/**
	 * 获取 qos2 过程消息
	 *
	 * @param messageId messageId
	 * @return MqttPendingQos2Publish
	 */
	MqttPendingQos2Publish getPendingQos2Publish(int messageId);

	/**
	 * 删除 qos2 过程消息
	 *
	 * @param messageId messageId
	 * @return MqttPendingQos2Publish
	 */
	MqttPendingQos2Publish removePendingQos2Publish(int messageId);

	/**
	 * 添加待发送的发布消息到队列（MQTT 未连接成功时）
	 *
	 * @param message MqttPublishMessage
	 */
	void addPendingPublishMessage(MqttPublishMessage message);

	/**
	 * 尝试将待发送的发布消息加入队列。
	 * 默认实现兼容已有会话实现，调用添加方法后返回 true；自定义有界队列应重写此方法以报告入队失败。
	 *
	 * @param message 待发送的发布消息
	 * @return 是否成功加入队列
	 */
	default boolean offerPendingPublishMessage(MqttPublishMessage message) {
		addPendingPublishMessage(message);
		return true;
	}

	/**
	 * 获取并清除所有待发送的发布消息
	 *
	 * @return 待发送的消息列表
	 */
	List<MqttPublishMessage> getAndClearPendingPublishMessages();

	/**
	 * 获取队列中待发送消息的数量
	 *
	 * @return 消息数量
	 */
	int getPendingPublishMessageCount();

	/**
	 * 初始化待发送消息队列
	 *
	 * @param queueSize 队列大小
	 */
	void initPendingPublishQueue(int queueSize);

	/**
	 * 资源清理
	 */
	void clean();

}
