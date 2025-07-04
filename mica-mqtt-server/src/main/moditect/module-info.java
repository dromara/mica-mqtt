open module org.dromara.mica.mqtt.server {
	requires transitive org.dromara.mica.mqtt.common;
	requires transitive net.dreamlu.mica.net.http;
	requires java.management;

	exports org.dromara.mica.mqtt.core.server;
	exports org.dromara.mica.mqtt.core.server.auth;
	exports org.dromara.mica.mqtt.core.server.broker;
	exports org.dromara.mica.mqtt.core.server.cluster;
	exports org.dromara.mica.mqtt.core.server.dispatcher;
	exports org.dromara.mica.mqtt.core.server.enums;
	exports org.dromara.mica.mqtt.core.server.event;
	exports org.dromara.mica.mqtt.core.server.http.handler;
	exports org.dromara.mica.mqtt.core.server.interceptor;
	exports org.dromara.mica.mqtt.core.server.listener;
	exports org.dromara.mica.mqtt.core.server.model;
	exports org.dromara.mica.mqtt.core.server.protocol;
	exports org.dromara.mica.mqtt.core.server.serializer;
	exports org.dromara.mica.mqtt.core.server.session;
	exports org.dromara.mica.mqtt.core.server.store;
	exports org.dromara.mica.mqtt.core.server.support;
}
