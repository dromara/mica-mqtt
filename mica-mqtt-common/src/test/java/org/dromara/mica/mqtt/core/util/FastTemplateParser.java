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
		// 预估容量：通常一个模板不会有太多变量，预设为4是合理的
		this.literals = new ArrayList<>(4);
		this.variables = new ArrayList<>(4);
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
		if (template == null || template.isEmpty()) {
			literals.add("");
			return;
		}

		int start = 0;
		int templateLength = template.length();
		
		while (start < templateLength) {
			int varStart = template.indexOf("${", start);
			if (varStart == -1) {
				break;
			}
			
			// 记录固定文本
			literals.add(template.substring(start, varStart));
			
			int varIdx = varStart + 2;
			int varEnd = template.indexOf('}', varIdx);
			if (varEnd == -1) {
				throw new IllegalArgumentException("模板格式错误：缺少结束符 '}'");
			}
			
			// 检查变量名是否为空
			if (varEnd == varIdx) {
				throw new IllegalArgumentException("模板格式错误：变量名为空");
			}
			
			// 记录变量名
			variables.add(template.substring(varIdx, varEnd));
			start = varEnd + 1;
		}
		
		// 添加末尾固定文本
		literals.add(template.substring(start));
	}

	/**
	 * 提取变量值
	 * 示例输入："abcDreamluxxHellocc"
	 * 输出：{name=Dreamlu, like=Hello}
	 * 
	 * @param input 输入字符串
	 * @return 变量映射，解析失败时返回空Map
	 */
	public Map<String, String> parse(String input) {
		// 预检查：长度不匹配时快速失败
		if (input == null || literals.isEmpty()) {
			return Collections.emptyMap();
		}

		// 预估容量，减少HashMap扩容
		Map<String, String> result = new LinkedHashMap<>(variables.size());
		int currentPos = 0;

		// 校验第一个固定文本
		String firstLiteral = literals.get(0);
		if (!input.startsWith(firstLiteral)) {
			return Collections.emptyMap();
		}
		currentPos = firstLiteral.length();

		// 优化：缓存literals.size()以避免重复计算
		int literalsSize = literals.size();
		
		// 遍历后续固定文本来提取变量
		for (int i = 1; i < literalsSize; i++) {
			String literal = literals.get(i);
			int literalPos = input.indexOf(literal, currentPos);

			if (literalPos == -1) {
				return Collections.emptyMap(); // 未找到固定文本
			}

			// 提取变量值 - 优化：避免不必要的字符串创建
			if (literalPos > currentPos) {
				String varValue = input.substring(currentPos, literalPos);
				result.put(variables.get(i - 1), varValue);
			} else {
				// 空变量值
				result.put(variables.get(i - 1), "");
			}

			currentPos = literalPos + literal.length();
		}

		// 校验末尾是否匹配
		return (currentPos == input.length()) ? result : Collections.emptyMap();
	}

	/**
	 * 尝试解析并返回 Optional 结果
	 * 
	 * @param input 输入字符串
	 * @return 解析结果的 Optional，解析失败时为空
	 */
	public Optional<Map<String, String>> tryParse(String input) {
		Map<String, String> result = parse(input);
		return result.isEmpty() ? Optional.empty() : Optional.of(result);
	}

	// 测试用例
	public static void main(String[] args) {
		FastTemplateParser parser = new FastTemplateParser("/abc/${name}xx${like}/cc");
		
		// 测试成功解析
		Map<String, String> res = parser.parse("/abc/DreamluxxHello/cc");
		System.out.println("成功解析: " + res); // {name=Dreamlu, like=Hello}
		
		// 测试新的 Optional API
		Optional<Map<String, String>> optResult = parser.tryParse("/abc/DreamluxxHello/cc");
		System.out.println("Optional 解析成功: " + optResult.isPresent()); // true
		
		// 异常测试
		Map<String, String> errRes = parser.parse("/abc/ERRORxxHello/cc");
		System.out.println("解析失败: " + errRes); // {}
		
		Optional<Map<String, String>> optErrRes = parser.tryParse("/abc/ERRORxxHello/cc");
		System.out.println("Optional 解析失败: " + optErrRes.isPresent()); // false
	}

}
