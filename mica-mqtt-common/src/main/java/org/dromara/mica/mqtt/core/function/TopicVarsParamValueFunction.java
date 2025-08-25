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
import org.dromara.mica.mqtt.core.common.TopicFilterType;
import org.tio.core.ChannelContext;

import java.util.Collections;
import java.util.Map;

/**
 * topic 参数函数
 *
 * @author L.cm
 */
public class TopicVarsParamValueFunction implements ParamValueFunction {
	private final String[] topicTemplates;
	private final String[] topicFilters;

	public TopicVarsParamValueFunction(String[] topicTemplates, String[] topicFilters) {
		this.topicTemplates = topicTemplates;
		this.topicFilters = topicFilters;
	}

	@Override
	public Object getValue(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		return getTopicVars(topicTemplates, topicFilters, topic);
	}

	/**
	 * 获取 topic 变量
	 *
	 * @param topicTemplates topicTemplates
	 * @param topicFilters   topicFilters
	 * @param topic          topic
	 * @return 变量集合
	 */
	private static Map<String, String> getTopicVars(String[] topicTemplates, String[] topicFilters, String topic) {
		for (int j = 0; j < topicFilters.length; j++) {
			String topicFilter = topicFilters[j];
			TopicFilterType topicFilterType = TopicFilterType.getType(topicFilter);
			if (topicFilterType.match(topicFilter, topic)) {
				String topicTemplate = topicTemplates[j];
				return topicFilterType.getTopicVars(topicTemplate, topic);
			}
		}
		return Collections.emptyMap();
	}
}
