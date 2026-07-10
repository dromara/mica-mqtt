package org.dromara.mica.mqtt.codec.test;

import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.MqttEncoder;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.codec.properties.UserProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserPropertyRoundTripTest {

	@Test
	void singleUserPropertyOnlyRoundTrip() {
		MqttProperties properties = new MqttProperties();
		properties.add(new UserProperty("k1", "v1"));

		byte[] bytes = MqttEncoder.encodeProperties(properties);
		MqttProperties decoded = MqttDecoder.decodeProperties(bytes);
		assertEquals(1, decoded.getProperties(MqttPropertyType.USER_PROPERTY.value()).size());
	}

	@Test
	void userPropertyWithOtherPropRoundTrip() {
		MqttProperties properties = new MqttProperties();
		properties.add(new UserProperty("k1", "v1"));
		properties.add(new IntegerProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, 60));

		byte[] bytes = MqttEncoder.encodeProperties(properties);
		MqttProperties decoded = MqttDecoder.decodeProperties(bytes);
		assertEquals(1, decoded.getProperties(MqttPropertyType.USER_PROPERTY.value()).size());
		assertEquals(Integer.valueOf(60), decoded.getPropertyValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL));
	}

	@Test
	void multipleUserPropertiesRoundTrip() {
		MqttProperties properties = new MqttProperties();
		properties.add(new UserProperty("k1", "v1"));
		properties.add(new UserProperty("k2", "v2"));
		properties.add(new UserProperty("k3", "v3"));

		byte[] bytes = MqttEncoder.encodeProperties(properties);
		MqttProperties decoded = MqttDecoder.decodeProperties(bytes);
		assertEquals(3, decoded.getProperties(MqttPropertyType.USER_PROPERTY.value()).size());
	}

	@Test
	void userPropertyWithSubscriptionIdentifierRoundTrip() {
		MqttProperties properties = new MqttProperties();
		properties.add(new UserProperty("k1", "v1"));
		properties.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 42));

		byte[] bytes = MqttEncoder.encodeProperties(properties);
		MqttProperties decoded = MqttDecoder.decodeProperties(bytes);
		assertEquals(1, decoded.getProperties(MqttPropertyType.USER_PROPERTY.value()).size());
		assertEquals(Integer.valueOf(42),
			decoded.getPropertyValue(MqttPropertyType.SUBSCRIPTION_IDENTIFIER));
	}
}
