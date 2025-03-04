package org.dromara.mica.mqtt.core.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TopicFilterType 测试
 *
 * @author L.cm
 */
class TopicFilterTypeTest {

	@Test
	void test1() {
		String topic1 = "$queue/123";
		TopicFilterType type1 = TopicFilterType.getType(topic1);
		Assertions.assertEquals(TopicFilterType.QUEUE, type1);
		Assertions.assertTrue(type1.match(topic1, "123"));
		Assertions.assertFalse(type1.match(topic1, "/123"));

		String topic2 = "$share/test/123";
		TopicFilterType type2 = TopicFilterType.getType(topic2);
		String groupName = TopicFilterType.getShareGroupName(topic2);
		Assertions.assertEquals("test", groupName);
		Assertions.assertEquals(TopicFilterType.SHARE, type2);
		Assertions.assertTrue(type2.match(topic2, "123"));
		Assertions.assertFalse(type2.match(topic2, "/123"));

		String topic3 = "$queue//123";
		TopicFilterType type3 = TopicFilterType.getType(topic3);
		Assertions.assertEquals(TopicFilterType.QUEUE, type3);
		Assertions.assertFalse(type3.match(topic3, "123"));
		Assertions.assertTrue(type3.match(topic3, "/123"));

		String topic4 = "$share/test//123";
		TopicFilterType type4 = TopicFilterType.getType(topic4);
		Assertions.assertEquals(TopicFilterType.SHARE, type4);
		Assertions.assertFalse(type4.match(topic4, "123"));
		Assertions.assertTrue(type4.match(topic4, "/123"));
	}

}
