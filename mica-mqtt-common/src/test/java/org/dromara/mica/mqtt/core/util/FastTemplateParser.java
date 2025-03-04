package org.dromara.mica.mqtt.core.util;

import java.util.*;

public class FastTemplateParser {
	/**
	 * 固定文本序列
	 */
	private final List<String> literals;
	/**
	 * 变量名序列
	 */
	private final List<String> variables;

	public FastTemplateParser(String template) {
		this.literals = new ArrayList<>();
		this.variables = new ArrayList<>();
		parseTemplate(template);
	}

	/**
	 * 解析模板结构
	 * 示例输入："abc${name}xx${like}cc"
	 * 输出：
	 * literals = ["abc", "xx", "cc"]
	 * variables = ["name", "like"]
	 */
	private void parseTemplate(String template) {
		int start = 0;
		while (true) {
			int varStart = template.indexOf("${", start);
			if (varStart == -1) {
				break;
			}
			// 记录固定文本
			literals.add(template.substring(start, varStart));
			int varIdx = varStart + 2;
			int varEnd = template.indexOf('}', varIdx);
			if (varEnd == -1) {
				throw new IllegalArgumentException("模板格式错误");
			}
			// 记录变量名
			variables.add(template.substring(varIdx, varEnd));
			start = varEnd + 1;
		}
		literals.add(template.substring(start)); // 添加末尾固定文本
	}

	/**
	 * 提取变量值
	 * 示例输入："abcDreamluxxHellocc"
	 * 输出：{name=Dreamlu, like=Hello}
	 */
	public Map<String, String> parse(String input) {
		Map<String, String> result = new LinkedHashMap<>();
		int currentPos;

		// 校验第一个固定文本
		String firstLiteral = literals.get(0);
		if (!input.startsWith(firstLiteral)) {
			return result;
		}
		currentPos = firstLiteral.length();

		// 遍历后续固定文本来提取变量
		for (int i = 1; i < literals.size(); i++) {
			String literal = literals.get(i);
			int literalPos = input.indexOf(literal, currentPos);

			if (literalPos == -1) {
				return Collections.emptyMap(); // 未找到固定文本
			}

			// 提取变量值
			String varValue = input.substring(currentPos, literalPos);
			result.put(variables.get(i - 1), varValue);

			currentPos = literalPos + literal.length();
		}

		// 校验末尾是否匹配
		return (currentPos == input.length()) ? result : Collections.emptyMap();
	}

	// 测试用例
	public static void main(String[] args) {
		FastTemplateParser parser = new FastTemplateParser("/abc/${name}xx${like}/cc");
		Map<String, String> res = parser.parse("/abc/DreamluxxHello/cc");
		System.out.println(res); // {name=Dreamlu, like=Hello}
		// 异常测试
		Map<String, String> errRes = parser.parse("/abc/ERRORxxHello/cc");
		System.out.println(errRes); // {}
	}

}
