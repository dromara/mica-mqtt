package org.dromara.mica.mqtt.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSize 工具类测试
 *
 * @author L.cm
 */
public class DataSizeTest {

	@Test
	public void testBasicFunctionality() {
		// 测试基本功能
		DataSize bytes = DataSize.ofBytes(1024);
		assertEquals(1024, bytes.getBytes());

		DataSize kb = DataSize.ofKilobytes(1);
		assertEquals(1024, kb.getBytes());

		DataSize mb = DataSize.ofMegabytes(1);
		assertEquals(1024 * 1024, mb.getBytes());
	}

	@Test
	public void testParsing() {
		// 测试解析功能
		DataSize parsed = DataSize.parse("10KB");
		assertEquals(10 * 1024, parsed.getBytes());

		DataSize parsedMB = DataSize.parse("5MB");
		assertEquals(5 * 1024 * 1024, parsedMB.getBytes());
	}

	@Test
	public void testDataUnit() {
		// 测试 DataUnit 枚举
		assertEquals("KB", DataSize.DataUnit.KILOBYTES.getSuffix());
		assertEquals("MB", DataSize.DataUnit.MEGABYTES.getSuffix());
		
		DataSize.DataUnit unit = DataSize.DataUnit.fromSuffix("GB");
		assertEquals(DataSize.DataUnit.GIGABYTES, unit);
	}

	@Test
	public void testEquality() {
		// 测试相等性
		DataSize size1 = DataSize.ofBytes(1024);
		DataSize size2 = DataSize.ofKilobytes(1);
		assertEquals(size1, size2);
		assertEquals(size1.hashCode(), size2.hashCode());
	}

}