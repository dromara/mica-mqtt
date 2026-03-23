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

package org.dromara.mica.mqtt.core.server.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认 MQTT 发布消息处理管线实现
 *
 * @author L.cm
 */
public class DefaultMqttPublishPipeline implements IMqttPublishPipeline {
	private static final Logger logger = LoggerFactory.getLogger(DefaultMqttPublishPipeline.class);

	private final List<MqttPublishPipelineHandler> handlers;

	public DefaultMqttPublishPipeline() {
		this.handlers = new ArrayList<>();
	}

	@Override
	public void addHandler(MqttPublishPipelineHandler handler) {
		if (handler != null) {
			this.handlers.add(handler);
			// 按顺序排序
			this.handlers.sort(Comparator.comparingInt(MqttPublishPipelineHandler::getOrder));
		}
	}

	@Override
	public void handle(PublishContext context) {
		if (context == null) {
			return;
		}
		for (MqttPublishPipelineHandler handler : handlers) {
			try {
				boolean continueProcess = handler.handle(context);
				if (!continueProcess) {
					logger.debug("Pipeline handler {} interrupted the process", handler.getClass().getSimpleName());
					break;
				}
			} catch (Throwable e) {
				logger.error("Pipeline handler {} error", handler.getClass().getSimpleName(), e);
				// 如果是关键处理器，异常后中断流程，防止数据不一致
				if (handler.isCritical()) {
					logger.error("Critical pipeline handler {} failed, interrupting subsequent handlers", handler.getClass().getSimpleName());
					break;
				}
				// 非关键处理器继续处理下一个处理器
			}
		}
	}

}
