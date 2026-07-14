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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT 5.0 客户端 Subscription Identifier 自动分配（PR10 / spec 3.3.2.3.6 / 3.4.2.1.1）。
 *
 * <p>Subscription Identifier 是 varint（1 ~ 268,435,455），由客户端在 SUBSCRIBE 时携带。
 * 业务方有两种使用方式：
 * <ol>
 *     <li>显式：调用 {@link #nextId()} 获取下一个可用 ID 并放入 properties；</li>
 *     <li>自动：subscribe 时不带 properties，由 {@code MqttClient} 自动分配并附加。</li>
 * </ol>
 *
 * <p>自动分配采用单调递增策略（从 1 开始），达到上限（0xFFFFFFF = 268,435,455）时抛 IllegalStateException；
 * 业务方也可调用 {@link #reset()} 重新从 1 开始（适用于连接重建后的 session 重置）。
 *
 * @author L.cm
 */
public class MqttSubscriptionIdManager {
	/**
	 * spec 3.3.2.3.6: Subscription Identifier 上限 0xFFFFFFFF = 268,435,455（实际 varint 上限）。
	 */
	public static final int MAX_SUBSCRIPTION_ID = 0xFFFFFFF;
	private final AtomicInteger nextId = new AtomicInteger(1);

	/**
	 * 分配下一个可用的 Subscription Identifier。
	 *
	 * @return 下一个 ID（>= 1）
	 * @throws IllegalStateException 当达到上限
	 */
	public int nextId() {
		int id = nextId.getAndIncrement();
		if (id > MAX_SUBSCRIPTION_ID) {
			// 防御性回收
			nextId.set(MAX_SUBSCRIPTION_ID);
			throw new IllegalStateException("Subscription Identifier exhausted (max=" + MAX_SUBSCRIPTION_ID + ")");
		}
		return id;
	}

	/**
	 * 重置为从 1 开始分配。适用于连接重建或 session 过期后。
	 */
	public void reset() {
		nextId.set(1);
	}

	/**
	 * 当前即将分配的下一个 ID。
	 */
	public int peek() {
		return nextId.get();
	}
}
