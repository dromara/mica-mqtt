package org.dromara.mica.mqtt.core.common;

import org.tio.utils.mica.StrTemplateParser;

import java.util.Map;

/**
 * topic 模板带 ${var} 变量的模板
 *
 * @author L.cm
 */
public class TopicTemplate {
	private final StrTemplateParser templateParser;
	private final String topicFilter;
	private final TopicFilterType type;

	public TopicTemplate(String topicTemplate, String topicFilter) {
		this.templateParser = new StrTemplateParser(topicTemplate);
		this.topicFilter = topicFilter;
		this.type = TopicFilterType.getType(topicFilter);
	}

	/**
	 * 判断 topicFilter 和 topicName 匹配情况
	 *
	 * @param topicName topicName
	 * @return 是否匹配
	 */
	public boolean match(String topicName) {
		return type.match(this.topicFilter, topicName);
	}

	/**
	 * 解析 topic 中的变量
	 *
	 * @param topicName topicName
	 * @return 变量
	 */
	public Map<String, String> getVariables(String topicName) {
		return templateParser.getVariables(topicName);
	}

}
