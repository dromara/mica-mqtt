open module org.dromara.mica.mqtt.client.spring.boot.starter {
	requires lombok;
	requires spring.core;
	requires spring.context;
	requires spring.boot;
	requires spring.boot.autoconfigure;
	requires transitive org.dromara.mica.mqtt.client;
	exports org.dromara.mica.mqtt.spring.client;
	exports org.dromara.mica.mqtt.spring.client.annotation;
	exports org.dromara.mica.mqtt.spring.client.config;
	exports org.dromara.mica.mqtt.spring.client.event;
}
