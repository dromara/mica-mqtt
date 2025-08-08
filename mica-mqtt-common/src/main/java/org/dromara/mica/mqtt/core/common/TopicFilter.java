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

package org.dromara.mica.mqtt.core.common;

/**
 * TopicFilter
 *
 * @author L.cm
 */
public class TopicFilter {

	/**
	 * topicFilter
	 */
	private final String topic;
	/**
	 * topicFilterType
	 */
	private final TopicFilterType type;

	public TopicFilter(String topicFilter) {
		this.topic = topicFilter;
		this.type = TopicFilterType.getType(topicFilter);
	}

	public String getTopic() {
		return topic;
	}

	public TopicFilterType getType() {
		return type;
	}

	/**
	 * 判断 topicFilter 和 topicName 匹配情况
	 *
	 * @param topicName topicName
	 * @return 是否匹配
	 */
	public boolean match(String topicName) {
		return type.match(this.topic, topicName);
	}

	@Override
	public String toString() {
		return "TopicFilter{" +
			"topic='" + topic + '\'' +
			", type=" + type +
			'}';
	}
}
