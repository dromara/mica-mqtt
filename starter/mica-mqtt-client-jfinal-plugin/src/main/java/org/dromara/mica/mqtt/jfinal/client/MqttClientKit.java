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

package org.dromara.mica.mqtt.jfinal.client;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.builder.MqttPublishMessageBuilder;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.dromara.mica.mqtt.core.client.MqttClientSubscription;
import org.tio.client.ClientChannelContext;
import org.tio.client.TioClient;
import org.tio.client.TioClientConfig;

import java.util.List;
import java.util.function.Consumer;

/**
 * mica mqtt client kit
 *
 * @author L.cm
 * @author ChangJin Wei (魏昌进)
 */
public class MqttClientKit {
	private static MqttClient client;

	/**
	 * 初始化
	 *
	 * @param client        MqttClient
	 */
	static void init(MqttClient client) {
		MqttClientKit.client = client;
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subQos0(String topicFilter, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, MqttQoS.QOS0, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param properties  MqttProperties
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subQos0(String topicFilter, MqttProperties properties, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, MqttQoS.QOS0, listener, properties);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subQos1(String topicFilter, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, MqttQoS.QOS1, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param properties  MqttProperties
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subQos1(String topicFilter, MqttProperties properties, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, MqttQoS.QOS1, listener, properties);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subQos2(String topicFilter, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, MqttQoS.QOS2, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param properties  MqttProperties
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subQos2(String topicFilter, MqttProperties properties, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, MqttQoS.QOS2, listener, properties);
	}

	/**
	 * 订阅
	 *
	 * @param mqttQoS     MqttQoS
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subscribe(MqttQoS mqttQoS, String topicFilter, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, mqttQoS, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param mqttQoS     MqttQoS
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subscribe(String topicFilter, MqttQoS mqttQoS, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilter, mqttQoS, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param mqttQoS     MqttQoS
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @param properties  MqttProperties
	 * @return MqttClient
	 */
	public static MqttClient subscribe(String topicFilter, MqttQoS mqttQoS, IMqttClientMessageListener listener, MqttProperties properties) {
		return client.subscribe(topicFilter, mqttQoS, listener, properties);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilters topicFilter 数组
	 * @param mqttQoS      MqttQoS
	 * @param listener     MqttMessageListener
	 * @return MqttClient
	 */
	public static MqttClient subscribe(String[] topicFilters, MqttQoS mqttQoS, IMqttClientMessageListener listener) {
		return client.subscribe(topicFilters, mqttQoS, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilters topicFilter 数组
	 * @param mqttQoS      MqttQoS
	 * @param listener     MqttMessageListener
	 * @param properties   MqttProperties
	 * @return MqttClient
	 */
	public static MqttClient subscribe(String[] topicFilters, MqttQoS mqttQoS, IMqttClientMessageListener listener, MqttProperties properties) {
		return client.subscribe(topicFilters, mqttQoS, listener, properties);
	}

	/**
	 * 批量订阅
	 *
	 * @param subscriptionList 订阅集合
	 * @return MqttClient
	 */
	public static MqttClient subscribe(List<MqttClientSubscription> subscriptionList) {
		return client.subscribe(subscriptionList, null);
	}

	/**
	 * 批量订阅
	 *
	 * @param subscriptionList 订阅集合
	 * @param properties       MqttProperties
	 * @return MqttClient
	 */
	public static MqttClient subscribe(List<MqttClientSubscription> subscriptionList, MqttProperties properties) {
		return client.subscribe(subscriptionList, properties);
	}

	/**
	 * 取消订阅
	 *
	 * @param topicFilters topicFilter 集合
	 * @return MqttClient
	 */
	public static MqttClient unSubscribe(String... topicFilters) {
		return client.unSubscribe(topicFilters);
	}

	/**
	 * 取消订阅
	 *
	 * @param topicFilters topicFilter 集合
	 * @return MqttClient
	 */
	public static MqttClient unSubscribe(List<String> topicFilters) {
		return client.unSubscribe(topicFilters);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息内容
	 * @return 是否发送成功
	 */
	public static boolean publish(String topic, Object payload) {
		return client.publish(topic, payload);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息内容
	 * @param qos     MqttQoS
	 * @return 是否发送成功
	 */
	public static boolean publish(String topic, Object payload, MqttQoS qos) {
		return client.publish(topic, payload, qos);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息内容
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public static boolean publish(String topic, Object payload, boolean retain) {
		return client.publish(topic, payload, retain);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public static boolean publish(String topic, Object payload, MqttQoS qos, boolean retain) {
		return client.publish(topic, payload, qos, retain);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @param builder PublishBuilder
	 * @return 是否发送成功
	 */
	public static boolean publish(String topic, Object payload, MqttQoS qos, Consumer<MqttPublishMessageBuilder> builder) {
		return client.publish(topic, payload, qos, builder);
	}

	/**
	 * 重连
	 */
	public static void reconnect() {
		client.reconnect();
	}

	/**
	 * 断开 mqtt 连接
	 *
	 * @return 是否成功
	 */
	public static boolean disconnect() {
		return client.disconnect();
	}

	/**
	 * 获取 TioClient
	 *
	 * @return TioClient
	 */
	public static TioClient getTioClient() {
		return client.getTioClient();
	}

	/**
	 * 获取配置
	 *
	 * @return MqttClientCreator
	 */
	public static MqttClientCreator getClientCreator() {
		return client.getClientCreator();
	}

	/**
	 * 获取 TioClientConfig
	 *
	 * @return TioClientConfig
	 */
	public static TioClientConfig getClientTioConfig() {
		return client.getClientTioConfig();
	}

	/**
	 * 获取 ClientChannelContext
	 *
	 * @return ClientChannelContext
	 */
	public static ClientChannelContext getContext() {
		return client.getContext();
	}

	/**
	 * 判断客户端跟服务端是否连接
	 *
	 * @return 是否已经连接成功
	 */
	public static boolean isConnected() {
		return client.isConnected();
	}

	/**
	 * 判断客户端跟服务端是否断开连接
	 *
	 * @return 是否断连
	 */
	public static boolean isDisconnected() {
		return client.isDisconnected();
	}

}
