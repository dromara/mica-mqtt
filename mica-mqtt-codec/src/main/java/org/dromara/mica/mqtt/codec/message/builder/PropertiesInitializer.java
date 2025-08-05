package org.dromara.mica.mqtt.codec.message.builder;

public interface PropertiesInitializer<T> {
	void apply(T builder);
}
