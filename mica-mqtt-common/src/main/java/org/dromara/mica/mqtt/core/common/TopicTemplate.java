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
	// 优化：预标记哪些位置是变量，避免每次 getVariables() 都调用 startsWith
	private final boolean[] isVariablePart;
	// 优化：预标记哪些位置是通配符，避免字符串比较
	private final boolean[] isWildcardPart;

	public TopicTemplate(String topicTemplate, String topicFilter) {
		int topicPrefixLength = TopicFilterType.getType(topicFilter).getPrefixLength(topicFilter);
		this.topicTemplateParts = getTopicTemplateParts(topicPrefixLength, topicTemplate);
		// 优化：合并变量提取和类型标记为一次遍历，提升构造性能
		this.varIndexMap = new IntObjectHashMap<>();
		this.isVariablePart = new boolean[topicTemplateParts.length];
		this.isWildcardPart = new boolean[topicTemplateParts.length];
		markPartTypesAndExtractVars();
	}

	private static String[] getTopicTemplateParts(int prefixLength, String topicTemplate) {
		if (prefixLength > 0) {
			topicTemplate = topicTemplate.substring(prefixLength);
		}
		return TopicUtil.getTopicParts(topicTemplate);
	}

	/**
	 * 合并变量提取和类型标记为一次遍历，提升构造性能
	 */
	private void markPartTypesAndExtractVars() {
		for (int i = 0; i < topicTemplateParts.length; i++) {
			String part = topicTemplateParts[i];
			if (part.startsWith("${") && part.endsWith("}")) {
				isVariablePart[i] = true;
				// 提取变量名
				int len = part.length();
				if (len > 3) { // 至少 ${x} 的长度
					varIndexMap.put(i, part.substring(2, len - 1));
				}
			} else if (TopicUtil.TOPIC_WILDCARDS_ONE.equals(part) || TopicUtil.TOPIC_WILDCARDS_MORE.equals(part)) {
				isWildcardPart[i] = true;
			}
		}
	}

	/**
	 * 检查最后一位是否是 # 通配符
	 */
	private boolean hasHashWildcard() {
		return topicTemplateParts.length > 0
			&& TopicUtil.TOPIC_WILDCARDS_MORE.equals(topicTemplateParts[topicTemplateParts.length - 1]);
	}

	/**
	 * 检查长度是否不匹配
	 */
	private boolean isLengthNotMatch(String[] topicParts, boolean hasHashWildcard) {
		return hasHashWildcard
			? topicParts.length < topicTemplateParts.length
			: topicParts.length != topicTemplateParts.length;
	}

	/**
	 * 逐级匹配 topic parts（不提取变量）
	 */
	private boolean matchParts(String[] topicParts, int matchLength) {
		for (int i = 0; i < matchLength; i++) {
			String p = topicTemplateParts[i];
			String t = topicParts[i];
			if (isVariablePart[i]) {
				// 变量可以匹配任何值，继续检查下一级
			} else if (isWildcardPart[i]) {
				// 通配符不能匹配空值
				if (t.isEmpty()) {
					return false;
				}
				// + 通配符匹配单个层级，继续检查下一级
			} else if (!p.equals(t)) {
				// 固定部分必须完全匹配
				return false;
			}
		}
		return true;
	}

	/**
	 * 判断 topicFilter 和 topicName 匹配情况
	 *
	 * @param topicName topicName
	 * @return 是否匹配
	 */
	public boolean match(String topicName) {
		String[] topicParts = TopicUtil.getTopicParts(topicName);
		boolean hasHashWildcard = hasHashWildcard();
		// 1. 长度检查
		if (isLengthNotMatch(topicParts, hasHashWildcard)) {
			return false;
		}
		// 2. 逐级匹配（只匹配到 # 通配符之前）
		int matchLength = hasHashWildcard ? topicTemplateParts.length - 1 : topicTemplateParts.length;
		return matchParts(topicParts, matchLength);
	}

	/**
	 * 解析 topic 中的变量
	 *
	 * @param topicName topicName
	 * @return 变量
	 */
	public Map<String, String> getVariables(String topicName) {
		String[] topicParts = TopicUtil.getTopicParts(topicName);
		boolean hasHashWildcard = hasHashWildcard();
		// 1. 长度检查
		if (isLengthNotMatch(topicParts, hasHashWildcard)) {
			return Collections.emptyMap();
		}
		// 优化：如果变量数量已知，可以预设初始容量
		int varCount = varIndexMap.size();
		Map<String, String> result = varCount > 0
			? new HashMap<>((int) (varCount / 0.75f) + 1)
			: new HashMap<>();
		// 2. 逐级匹配并提取变量（只匹配到 # 通配符之前）
		int matchLength = hasHashWildcard ? topicTemplateParts.length - 1 : topicTemplateParts.length;
		for (int i = 0; i < matchLength; i++) {
			String p = topicTemplateParts[i];
			String t = topicParts[i];
			if (isVariablePart[i]) {
				result.put(varIndexMap.get(i), t);
			} else if (isWildcardPart[i]) {
				if (t.isEmpty()) {
					return Collections.emptyMap();
				}
			} else if (!p.equals(t)) {
				return Collections.emptyMap();
			}
		}
		return result;
	}
}
