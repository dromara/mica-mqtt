package org.dromara.mica.mqtt.server.test;

import org.tio.utils.json.JsonUtil;

public class JsonUtilTest {

	public static void main(String[] args) {
		String json = "{\"a\":\"1\",\"b\":\"2\"}";
		System.out.println(JsonUtil.isValidJson(json));
		Object value = JsonUtil.readValue(json, Object.class);
		System.out.println(value);
	}

}
