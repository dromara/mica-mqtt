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

import net.dreamlu.mica.net.utils.timer.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MQTT 5.0 Session Expiry Interval 调度器（spec 3.1.2.11.4 / 3.2.2.3.5 / P2.8）。
 *
 * <p>在正常 DISCONNECT（reason code 0x00）时调度：
 * <ul>
 *     <li>当客户端在 CONNECT 中声明 {@code Session Expiry Interval > 0} 且 {@code Clean Start = false}，
 *         会话到期之前允许"接管"：服务端保留订阅、messageId 状态、in-flight 配额等；</li>
 *     <li>到期后通过 {@code IMqttSessionManager.remove(clientId)} 释放全部状态。</li>
 * </ul>
 *
 * <p>到期前若客户端用同一 clientId 重新连接并保留 session（Clean Start = false），
 * 应通过 {@link #cancel(String)} 取消待发任务（由调用方在 CONNECT 处理中完成）。
 *
 * @author L.cm
 */
public class SessionExpireScheduler {
	private static final Logger logger = LoggerFactory.getLogger(SessionExpireScheduler.class);
	private final ConcurrentMap<String, TimerTask> tasks = new ConcurrentHashMap<>();
	private final IMqttSessionManager sessionManager;

	public SessionExpireScheduler(IMqttSessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	/**
	 * 注册 clientId 的会话过期任务。
	 *
	 * @param clientId       客户端 ID
	 * @param delaySeconds   延迟（秒）
	 * @return 已注册的 TimerTask
	 */
	public TimerTask scheduleExpire(String clientId, long delaySeconds) {
		// 取消旧任务，避免 clientId 重连覆盖场景
		cancel(clientId);
		TimerTask[] holder = new TimerTask[1];
		long delayMillis = delaySeconds * 1000L;
		TimerTask task = new TimerTask(delayMillis) {
			@Override
			public void run() {
				try {
					onExpire(clientId);
				} finally {
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
	 * 取消 clientId 的过期任务（重连接管时调用）。
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
	 * 判断 clientId 是否存在待触发的会话过期任务。
	 */
	public boolean isScheduled(String clientId) {
		return tasks.containsKey(clientId);
	}

	/**
	 * 清理所有任务。
	 */
	public void clear() {
		tasks.values().forEach(TimerTask::cancel);
		tasks.clear();
	}

	/**
	 * 任务到期回调：通过 {@link IMqttSessionManager#remove(String)} 清理 session。
	 * <p>
	 * 注意：客户端此时已断开连接，不存在 in-flight 流量需要释放（backlog 状态本身
	 * 也随 session 一起被清理）。
	 */
	private void onExpire(String clientId) {
		try {
			// spec 3.1.2.11.4: 过期时清理 session；服务端可"丢包"行为由业务方决定。
			sessionManager.remove(clientId);
			if (logger.isDebugEnabled()) {
				logger.debug("Session expired - clientId:{} session state cleared.", clientId);
			}
		} catch (Throwable e) {
			logger.error("Session expired - clientId:{} cleanup error.", clientId, e);
		}
	}
}
