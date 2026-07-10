package org.dromara.mica.mqtt.codec.test;

import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperty;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.StringProperty;
import org.dromara.mica.mqtt.codec.properties.UserProperty;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MqttPropertyEqualsHashCodeTest {

	@Test
	void equalsAndHashCodeContractForIntegerProperty() {
		MqttProperty<Integer> a = new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60);
		MqttProperty<Integer> b = new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());

		MqttProperty<Integer> c = new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 61);
		assertNotEquals(a, c);
	}

	@Test
	void equalsAndHashCodeContractForUserProperty() {
		MqttProperty<?> a = new UserProperty("k", "v");
		MqttProperty<?> b = new UserProperty("k", "v");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	void hashableInHashMap() {
		HashMap<MqttProperty<?>, String> map = new HashMap<>();
		MqttProperty<Integer> a = new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60);
		map.put(a, "x");
		assertEquals("x", map.get(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60)));
		assertNotEquals("x", map.get(new StringProperty(MqttPropertyType.REASON_STRING, "y")));
	}
}
