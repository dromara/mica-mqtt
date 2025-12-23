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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.tio.utils.collection.IntObjectHashMap;
import org.tio.utils.collection.IntObjectMap;

/**
 * topic 模板带 ${var} 变量的模板
 *
 * @author L.cm
 */
public class TopicTemplate {
	private final String[] topicTemplateParts;
	private final IntObjectMap<String> varIndexMap;
	// 优化：缓存去掉前缀后的 topicFilter，避免每次 match() 都创建新字符串
	private final String topicFilterWithoutPrefix;
	// 优化：预标记哪些位置是变量，避免每次 getVariables() 都调用 startsWith
	private final boolean[] isVariablePart;
	// 优化：预标记哪些位置是通配符，避免字符串比较
	private final boolean[] isWildcardPart;

	public TopicTemplate(String topicTemplate, String topicFilter) {
		int topicPrefixLength = TopicFilterType.getType(topicFilter).getPrefixLength(topicFilter);
		this.topicTemplateParts = getTopicTemplateParts(topicPrefixLength, topicTemplate);
		this.varIndexMap = getVarIndexMap(topicTemplateParts);
		// 缓存去掉前缀后的 topicFilter
		this.topicFilterWithoutPrefix = topicPrefixLength > 0
			? topicFilter.substring(topicPrefixLength)
			: topicFilter;
		// 预标记变量和通配符位置
		this.isVariablePart = new boolean[topicTemplateParts.length];
		this.isWildcardPart = new boolean[topicTemplateParts.length];
		markPartTypes();
	}

	private static String[] getTopicTemplateParts(int prefixLength, String topicTemplate) {
		if (prefixLength > 0) {
			topicTemplate = topicTemplate.substring(prefixLength);
		}
		return TopicUtil.getTopicPart(topicTemplate);
	}

	private static IntObjectMap<String> getVarIndexMap(String[] topicTemplateParts) {
		IntObjectMap<String> varIndexMap = new IntObjectHashMap<>();
		// 预提取变量位置
		for (int i = 0; i < topicTemplateParts.length; i++) {
			String part = topicTemplateParts[i];
			if (part.startsWith("${") && part.endsWith("}")) {
				// 优化：避免重复 substring 操作，直接计算长度
				int len = part.length();
				if (len > 3) { // 至少 ${x} 的长度
					varIndexMap.put(i, part.substring(2, len - 1));
				}
			}
		}
		return varIndexMap;
	}

	/**
	 * 预标记变量和通配符位置，避免运行时重复判断
	 */
	private void markPartTypes() {
		for (int i = 0; i < topicTemplateParts.length; i++) {
			String part = topicTemplateParts[i];
			if (part.startsWith("${") && part.endsWith("}")) {
				isVariablePart[i] = true;
			} else if (TopicUtil.TOPIC_WILDCARDS_ONE.equals(part) || TopicUtil.TOPIC_WILDCARDS_MORE.equals(part)) {
				isWildcardPart[i] = true;
			}
		}
	}

	/**
	 * 判断 topicFilter 和 topicName 匹配情况
	 *
	 * @param topicName topicName
	 * @return 是否匹配
	 */
	public boolean match(String topicName) {
		// 优化：使用缓存的字符串，避免每次调用 substring
		return TopicUtil.match(topicFilterWithoutPrefix, topicName);
	}

	/**
	 * 解析 topic 中的变量
	 *
	 * @param topicName topicName
	 * @return 变量
	 */
	public Map<String, String> getVariables(String topicName) {
		String[] topicParts = TopicUtil.getTopicPart(topicName);
		// 优化：如果变量数量已知，可以预设初始容量
		int varCount = varIndexMap.size();
		Map<String, String> result = varCount > 0
			? new HashMap<>((int) (varCount / 0.75f) + 1)
			: new HashMap<>();
		// 1. 长度必须相等
		if (topicParts.length != topicTemplateParts.length) {
			return result;
		}
		// 2. 逐级匹配
		for (int i = 0; i < topicTemplateParts.length; i++) {
			String p = topicTemplateParts[i];
			String t = topicParts[i];
			// 优化：使用预标记的数组，避免重复调用 startsWith
			if (isVariablePart[i]) { // 变量
				result.put(varIndexMap.get(i), t);
			} else if (isWildcardPart[i]) { // 通配符
				if (t.isEmpty()) {
					return Collections.emptyMap();
				}
			} else if (!p.equals(t)) { // 固定部分
				return Collections.emptyMap();
			}
		}
		return result;
	}

}
