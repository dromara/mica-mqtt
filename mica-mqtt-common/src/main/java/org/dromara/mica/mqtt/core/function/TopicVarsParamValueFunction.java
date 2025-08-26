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

package org.dromara.mica.mqtt.core.function;

import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.common.TopicTemplate;
import org.tio.core.ChannelContext;

import java.util.Collections;

/**
 * topic 参数函数
 *
 * @author L.cm
 */
public class TopicVarsParamValueFunction implements ParamValueFunction {
	private final TopicTemplate[] topicTemplates;

	public TopicVarsParamValueFunction(String[] topicTemplates, String[] topicFilters) {
		this.topicTemplates = getTopicTemplates(topicTemplates, topicFilters);
	}

	/**
	 * 获取 topic 模板列表
	 *
	 * @param topicTemplates topicTemplates
	 * @param topicFilters   topicFilters
	 * @return TopicTemplate array
	 */
	private static TopicTemplate[] getTopicTemplates(String[] topicTemplates, String[] topicFilters) {
		TopicTemplate[] templates = new TopicTemplate[topicTemplates.length];
		for (int i = 0; i < templates.length; i++) {
			templates[i] = new TopicTemplate(topicTemplates[i], topicFilters[i]);
		}
		return templates;
	}

	@Override
	public Object getValue(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		for (TopicTemplate topicTemplate : topicTemplates) {
			if (topicTemplate.match(topic)) {
				return topicTemplate.getVariables(topic);
			}
		}
		return Collections.emptyMap();
	}

}
