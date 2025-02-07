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

package org.dromara.mica.mqtt.core.util;

import org.dromara.mica.mqtt.codec.MqttCodecUtil;
import org.tio.utils.hutool.StrUtil;

import java.util.List;
import java.util.StringTokenizer;

/**
 * Mqtt Topic 工具
 *
 * @author L.cm
 */
public final class TopicUtil {
	public static final String TOPIC_LAYER = "/";

	/**
	 * 校验 topicFilter
	 *
	 * @param topicFilterList topicFilter 集合
	 */
	public static void validateTopicFilter(List<String> topicFilterList) {
		for (String topicFilter : topicFilterList) {
			validateTopicFilter(topicFilter);
		}
	}

	/**
	 * 校验 topicFilter
	 *
	 * @param topicFilter topicFilter
	 */
	public static void validateTopicFilter(String topicFilter) throws IllegalArgumentException {
		if (topicFilter == null || topicFilter.isEmpty()) {
			throw new IllegalArgumentException("TopicFilter is blank:" + topicFilter);
		}
		char[] topicFilterChars = topicFilter.toCharArray();
		int topicFilterLength = topicFilterChars.length;
		int topicFilterIdxEnd = topicFilterLength - 1;
		char ch;
		for (int i = 0; i < topicFilterLength; i++) {
			ch = topicFilterChars[i];
			if (Character.isWhitespace(ch)) {
				throw new IllegalArgumentException("Mqtt subscribe topicFilter has white space:" + topicFilter);
			} else if (ch == MqttCodecUtil.TOPIC_WILDCARDS_MORE) {
				// 校验: # 通配符只能在最后一位
				if (i < topicFilterIdxEnd) {
					throw new IllegalArgumentException("Mqtt subscribe topicFilter illegal:" + topicFilter);
				}
			} else if (ch == MqttCodecUtil.TOPIC_WILDCARDS_ONE) {
				// 校验: 单独 + 是允许的，判断 + 号前一位是否为 /，如果有后一位也必须为 /
				if ((i > 0 && topicFilterChars[i - 1] != '/') || (i < topicFilterIdxEnd && topicFilterChars[i + 1] != '/')) {
					throw new IllegalArgumentException("Mqtt subscribe topicFilter illegal:" + topicFilter);
				}
			}
		}
	}

	/**
	 * 校验 topicName
	 *
	 * @param topicName topicName
	 */
	public static void validateTopicName(String topicName) throws IllegalArgumentException {
		if (topicName == null || topicName.isEmpty()) {
			throw new IllegalArgumentException("Topic is blank:" + topicName);
		}
		if (MqttCodecUtil.isTopicFilter(topicName)) {
			throw new IllegalArgumentException("Topic has wildcards char [+] or [#], topicName:" + topicName);
		}
	}

	/**
	 * 判断 topicFilter topicName 是否匹配
	 *
	 * @param topicFilter topicFilter
	 * @param topicName   topicName
	 * @return 是否匹配
	 */
	public static boolean match(String topicFilter, String topicName) {
		char[] topicFilterChars = topicFilter.toCharArray();
		char[] topicNameChars = topicName.toCharArray();
		int topicFilterLength = topicFilterChars.length;
		int topicNameLength = topicNameChars.length;
		int topicFilterIdxEnd = topicFilterLength - 1;
		int topicNameIdxEnd = topicNameLength - 1;
		char ch;
		// 是否进入 + 号层级通配符
		boolean inLayerWildcard = false;
		int wildcardCharLen = 0;
		topicFilterLoop: for (int i = 0; i < topicFilterLength; i++) {
			ch = topicFilterChars[i];
			if (ch == MqttCodecUtil.TOPIC_WILDCARDS_MORE) {
				// 校验: # 通配符只能在最后一位
				if (i < topicFilterIdxEnd) {
					throw new IllegalArgumentException("Mqtt subscribe topicFilter illegal:" + topicFilter);
				}
				return true;
			} else if (ch == MqttCodecUtil.TOPIC_WILDCARDS_ONE) {
				// 校验: 单独 + 是允许的，判断 + 号前一位是否为 /，如果有后一位也必须为 /
				if ((i > 0 && topicFilterChars[i - 1] != '/') || (i < topicFilterIdxEnd && topicFilterChars[i + 1] != '/')) {
					throw new IllegalArgumentException("Mqtt subscribe topicFilter illegal:" + topicFilter);
				}
				// 如果 + 是最后一位，判断 topicName 中是否还存在层级 /
				// topicName index
				int topicNameIdx = i + wildcardCharLen;
				if (i == topicFilterIdxEnd && topicNameLength > topicNameIdx) {
					for (int j = topicNameIdx; j < topicNameLength; j++) {
						if (topicNameChars[j] == '/') {
							return false;
						}
					}
					return true;
				}
				inLayerWildcard = true;
			} else if (ch == '/') {
				if (inLayerWildcard) {
					inLayerWildcard = false;
				}
				// 预读下一位，如果是 #，并且 topicName 位数已经不足
				int next = i + 1;
				if ((topicFilterLength > next) && topicFilterChars[next] == '#' && topicNameLength < next) {
					return true;
				}
			}
			// topicName 长度不够了
			if (topicNameIdxEnd < i) {
				return false;
			}
			// 进入通配符
			if (inLayerWildcard) {
				for (int j = i + wildcardCharLen; j < topicNameLength; j++) {
					if (topicNameChars[j] == '/') {
						wildcardCharLen--;
						continue topicFilterLoop;
					} else {
						wildcardCharLen++;
					}
				}
			}
			// topicName index
			int topicNameIdx = i + wildcardCharLen;
			// topic 已经完成，topicName 还有数据
			if (topicNameIdx > topicNameIdxEnd) {
				return false;
			}
			if (ch != topicNameChars[topicNameIdx]) {
				return false;
			}
		}
		// 判断 topicName 是否还有数据
		return topicFilterLength + wildcardCharLen + 1 > topicNameLength;
	}

	/**
	 * 获取处理完成之后的 topic，需要考虑 test/${abc}123 也要替换成 test/+ 而非 test/+123
	 *
	 * @param topicTemplate topic 模板
	 * @return 获取处理完成之后的 topic
	 */
	public static String getTopicFilter(String topicTemplate) {
		// 替换 ${name} 为 +
		StringTokenizer tokenizer = new StringTokenizer(topicTemplate, TOPIC_LAYER, true);
		String token;
		StringBuilder topicFilterBuilder = new StringBuilder();
		while (tokenizer.hasMoreTokens()) {
			token = tokenizer.nextToken();
			if (TOPIC_LAYER.equals(token)) {
				topicFilterBuilder.append(token);
			} else if (hasVariable(token)) {
				topicFilterBuilder.append(MqttCodecUtil.TOPIC_WILDCARDS_ONE);
			} else {
				topicFilterBuilder.append(token);
			}
		}
		return topicFilterBuilder.toString();
	}

	/**
	 * 判断是否含有 ${x} 这样的变量
	 *
	 * @param input input
	 * @return 是否含有变量
	 */
	public static boolean hasVariable(String input) {
		if (StrUtil.isBlank(input)) {
			return false;
		}
		int startIndex = input.indexOf("${");
		int endIndex = input.indexOf("}", startIndex);
		// 检查是否同时存在 "${" 和 "}"，并且 "}" 在 "${" 之后
		return startIndex != -1 && endIndex != -1 && endIndex > startIndex + 2;
	}

}
