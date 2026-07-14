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

package org.dromara.mica.mqtt.core.server.will;

import net.dreamlu.mica.net.utils.timer.TimerTask;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MQTT 5.0 Will Delay Interval 调度器（spec 3.1.3.5 / 3.1.3.5.4）。
 *
 * <p>维护 {@code clientId -> TimerTask} 映射，支持：
 * <ul>
 *     <li>CONNECT 时为 clientId 注册延迟发送 Will 消息的 {@link TimerTask}；</li>
 *     <li>DISCONNECT（正常断开）、重连、清除时取消对应 TimerTask，避免误发 Will。</li>
 * </ul>
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap}。
 *
 * @author L.cm
 */
public class WillDelayScheduler {

	private final ConcurrentMap<String, TimerTask> tasks = new ConcurrentHashMap<>();

	/**
	 * 注册 clientId 的 Will Delay 任务。
	 * <p>若 clientId 已存在旧任务，会先取消旧任务（避免旧会话的 Will 在新会话覆盖前误发）。
	 *
	 * @param clientId    客户端 ID
	 * @param delayMillis 延迟（毫秒）
	 * @param runnable    任务逻辑
	 * @return 已注册的 TimerTask
	 */
	public TimerTask schedule(String clientId, long delayMillis, Runnable runnable) {
		// 取消旧任务，避免同 clientId 重连后旧任务的 Will 误发
		cancel(clientId);
		TimerTask[] holder = new TimerTask[1];
		TimerTask task = new TimerTask(delayMillis) {
			@Override
			public void run() {
				try {
					runnable.run();
				} finally {
					// 任务结束（无论成功/异常/被取消）后清理引用。
					// 注意：仅当 holder 中的还是本任务时清除，防止覆盖场景。
					TimerTask self = holder[0];
					if (self == this) {
						tasks.remove(clientId, this);
					}
				}
			}
		};
		holder[0] = task;
		tasks.put(clientId, task);
		return task;
	}

	/**
	 * 取消 clientId 的 Will Delay 任务。客户端正常断开、重连或调用方需要放弃 Will 发送时调用。
	 *
	 * @param clientId 客户端 ID
	 * @return 是否真的取消了一个任务
	 */
	public boolean cancel(String clientId) {
		TimerTask task = tasks.remove(clientId);
		if (task == null) {
			return false;
		}
		task.cancel();
		return true;
	}

	/**
	 * 判断 clientId 是否存在未触发的 Will Delay 任务。
	 *
	 * @param clientId 客户端 ID
	 * @return 是否存在
	 */
	public boolean isScheduled(String clientId) {
		return tasks.containsKey(clientId);
	}

	/**
	 * 清空所有调度任务（用于 shutdown）。
	 */
	public void clear() {
		tasks.values().forEach(TimerTask::cancel);
		tasks.clear();
	}
}
