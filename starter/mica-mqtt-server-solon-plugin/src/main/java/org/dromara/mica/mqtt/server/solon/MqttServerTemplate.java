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

package org.dromara.mica.mqtt.server.solon;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.model.ClientInfo;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.tio.core.ChannelContext;
import org.tio.core.stat.vo.StatVo;
import org.tio.utils.page.Page;
import org.tio.utils.timer.TimerTask;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * mqtt Server 模板
 *
 * @author wsq（冷月宫主）
 * @author ChangJin Wei (魏昌进)
 */
@Getter
@RequiredArgsConstructor
public class MqttServerTemplate {
	private final MqttServer mqttServer;

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, Object payload) {
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
	public boolean publish(String clientId, String topic, Object payload, MqttQoS qos) {
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
	public boolean publish(String clientId, String topic, Object payload, boolean retain) {
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
	public boolean publish(String clientId, String topic, Object payload, MqttQoS qos, boolean retain) {
		return mqttServer.publish(clientId, topic, payload, qos, retain);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload) {
		return mqttServer.publishAll(topic, payload);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload, MqttQoS qos) {
		return mqttServer.publishAll(topic, payload, qos);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload, boolean retain) {
		return mqttServer.publishAll(topic, payload, retain);
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
	public boolean publishAll(String topic, Object payload, MqttQoS qos, boolean retain) {
		return mqttServer.publishAll(topic, payload, qos, retain);
	}

	/**
	 * 获取客户端信息
	 *
	 * @param clientId clientId
	 * @return ClientInfo
	 */
	public ClientInfo getClientInfo(String clientId) {
		return mqttServer.getClientInfo(clientId);
	}

	/**
	 * 获取客户端信息
	 *
	 * @param context ChannelContext
	 * @return ClientInfo
	 */
	public ClientInfo getClientInfo(ChannelContext context) {
		return mqttServer.getClientInfo(context);
	}

	/**
	 * 获取所有的客户端
	 *
	 * @return 客户端列表
	 */
	public List<ClientInfo> getClients() {
		return mqttServer.getClients();
	}

	/**
	 * 分页获取所有的客户端
	 *
	 * @param pageIndex pageIndex，默认为 1
	 * @param pageSize  pageSize，默认为所有
	 * @return 分页
	 */
	public Page<ClientInfo> getClients(Integer pageIndex, Integer pageSize) {
		return mqttServer.getClients(pageIndex, pageSize);
	}

	/**
	 * 获取统计数据
	 * @return StatVo
	 */
	public StatVo getStat() {
		return mqttServer.getStat();
	}

	/**
	 * 获取客户端订阅情况
	 *
	 * @param clientId clientId
	 * @return 订阅集合
	 */
	public List<Subscribe> getSubscriptions(String clientId) {
		return mqttServer.getSubscriptions(clientId);
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay) {
		return mqttServer.schedule(command, delay);
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay, Executor executor) {
		return mqttServer.schedule(command, delay, executor);
	}

	/**
	 * 添加定时任务
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay) {
		return mqttServer.scheduleOnce(command, delay);
	}

	/**
	 * 添加定时任务
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay, Executor executor) {
		return mqttServer.scheduleOnce(command, delay, executor);
	}

	/**
	 * 获取 ChannelContext
	 *
	 * @param clientId clientId
	 * @return ChannelContext
	 */
	public ChannelContext getChannelContext(String clientId) {
		return mqttServer.getChannelContext(clientId);
	}

	/**
	 * 服务端主动断开连接
	 *
	 * @param clientId clientId
	 */
	public void close(String clientId) {
		mqttServer.close(clientId);
	}

}
