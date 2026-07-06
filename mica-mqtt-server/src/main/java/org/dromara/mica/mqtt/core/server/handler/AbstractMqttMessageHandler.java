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

package org.dromara.mica.mqtt.core.server.handler;

import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;

import java.util.concurrent.ExecutorService;

/**
 * mqtt server 消息处理器抽象基类，提供 serverCreator / executor / taskService
 * 等通用依赖注入。子类按 mqtt 消息类型拆解业务逻辑。
 *
 * @author L.cm
 */
public abstract class AbstractMqttMessageHandler implements IMqttMessageHandler {
	protected final MqttServerCreator serverCreator;
	protected final ExecutorService executor;
	protected final TimerTaskService taskService;

	protected AbstractMqttMessageHandler(MqttServerCreator serverCreator,
										ExecutorService executor,
										TimerTaskService taskService) {
		this.serverCreator = serverCreator;
		this.executor = executor;
		this.taskService = taskService;
	}
}
