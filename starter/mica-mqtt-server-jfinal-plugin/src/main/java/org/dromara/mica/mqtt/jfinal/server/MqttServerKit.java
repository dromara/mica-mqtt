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

package org.dromara.mica.mqtt.jfinal.server;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.tio.core.ChannelContext;

/**
 * mica mqtt server kit
 *
 * @author L.cm
 * @author ChangJin Wei (魏昌进)
 */
public class MqttServerKit {
	private static MqttServer mqttServer;

	/**
	 * 初始化
	 * @param mqttServer MqttServer
	 */
	static void init(MqttServer mqttServer) {
		MqttServerKit.mqttServer = mqttServer;
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @return 是否发送成功
	 */
	public static boolean publish(String clientId, String topic, Object payload) {
		return mqttServer.publish(clientId, topic, payload);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param qos      MqttQoS
	 * @return 是否发送成功
	 */
	public static boolean publish(String clientId, String topic, Object payload, MqttQoS qos) {
		return mqttServer.publish(clientId, topic, payload, qos);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param retain   是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public static boolean publish(String clientId, String topic, Object payload, boolean retain) {
		return mqttServer.publish(clientId, topic, payload, retain);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param qos      MqttQoS
	 * @param retain   是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public static boolean publish(String clientId, String topic, Object payload, MqttQoS qos, boolean retain) {
		return mqttServer.publish(clientId, topic, payload, qos, retain);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @return 是否发送成功
	 */
	public static boolean publishAll(String topic, Object payload) {
		return mqttServer.publishAll(topic, payload, MqttQoS.QOS0, false);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @return 是否发送成功
	 */
	public static boolean publishAll(String topic, Object payload, MqttQoS qos) {
		return mqttServer.publishAll(topic, payload, qos, false);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public static boolean publishAll(String topic, Object payload, boolean retain) {
		return mqttServer.publishAll(topic, payload, MqttQoS.QOS0, retain);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public static boolean publishAll(String topic, Object payload, MqttQoS qos, boolean retain) {
		return mqttServer.publishAll(topic, payload, qos, retain);
	}

	/**
	 * 获取 ChannelContext
	 *
	 * @param clientId clientId
	 * @return ChannelContext
	 */
	public static ChannelContext getChannelContext(String clientId) {
		return mqttServer.getChannelContext(clientId);
	}

	/**
	 * 服务端主动断开连接
	 *
	 * @param clientId clientId
	 */
	public static void close(String clientId) {
		mqttServer.close(clientId);
	}

}
