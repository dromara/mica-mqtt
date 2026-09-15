open module org.dromara.mica.mqtt.broker {
	requires transitive org.dromara.mica.mqtt.server;
	requires transitive org.dromara.mica.mqtt.codec;
	requires transitive net.dreamlu.mica.net.core;
	requires com.h2database.mvstore;

	exports org.dromara.mica.mqtt.broker.cluster;
	exports org.dromara.mica.mqtt.broker.cluster.config;
	exports org.dromara.mica.mqtt.broker.cluster.core;
	exports org.dromara.mica.mqtt.broker.cluster.message;
	exports org.dromara.mica.mqtt.broker.cluster.metrics;
	exports org.dromara.mica.mqtt.broker.cluster.pipeline;
	exports org.dromara.mica.mqtt.broker.cluster.pipeline.strategy;
	exports org.dromara.mica.mqtt.broker.cluster.store;
	exports org.dromara.mica.mqtt.broker.rule;
	exports org.dromara.mica.mqtt.broker.rule.codec;
	exports org.dromara.mica.mqtt.broker.rule.loader;
	exports org.dromara.mica.mqtt.broker.rule.matcher;
	exports org.dromara.mica.mqtt.broker.rule.metrics;
	exports org.dromara.mica.mqtt.broker.rule.action;
	exports org.dromara.mica.mqtt.broker.rule.store;
}
