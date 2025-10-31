/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
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

package org.dromara.mica.mqtt.benchmark;

import org.dromara.mica.mqtt.core.client.MqttClient;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.thread.ThreadUtils;
import org.tio.utils.thread.pool.SynThreadPoolExecutor;
import org.tio.utils.timer.DefaultTimerTaskService;
import org.tio.utils.timer.TimerTaskService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * mqtt 压力测试
 *
 * @author L.cm
 */
public class MqttBenchmark {

	public static void main(String[] args) {
		// 注意： windows 上需要修改最大的 Tcp 连接数，不然超不过 2W。
		// 《修改Windows服务器最大的Tcp连接数》：https://www.jianshu.com/p/00136a97d2d8
		int connCount = 5_0000;
		String ip = "127.0.0.1";
		// 优化：使用ArrayList+预分配容量，性能比CopyOnWriteArrayList好很多
		// 对于压测场景，不需要线程安全的写操作（因为是单线程创建客户端）
		final List<MqttClient> clientList = new ArrayList<>(connCount);
		SynThreadPoolExecutor tioExecutor = ThreadUtils.getTioExecutor();
		ExecutorService groupExecutor = ThreadUtils.getGroupExecutor();
		// 自定义全局 taskService，避免每个 client new，创建过多线程
		TimerTaskService taskService = new DefaultTimerTaskService(200L, 60);
		for (int i = 0; i < connCount; i++) {
			newClient(ip, i, clientList, tioExecutor, groupExecutor, taskService);
		}
	}

	private static void newClient(String ip, int i, final List<MqttClient> clientList,
								  SynThreadPoolExecutor tioExecutor,
								  ExecutorService groupExecutor,
								  TimerTaskService taskService) {
		MqttClient client = MqttClient.create()
			.ip(ip)
			.clientId(StrUtil.getNanoId() + i)
			.readBufferSize(128)
			// 取消自动重连
			.reconnect(false)
			.tioExecutor(tioExecutor)
			.groupExecutor(groupExecutor)
			.mqttExecutor(tioExecutor)
			.taskService(taskService)
			.connect();
		clientList.add(client);
	}

}
