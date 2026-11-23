open module org.dromara.mica.mqtt.broker {
	requires transitive org.dromara.mica.mqtt.server;
	requires transitive org.dromara.mica.mqtt.codec;
	requires transitive net.dreamlu.mica.net.core;
	requires com.h2database.mvstore;
	// 条件表达式求值（AviatorScript）
	requires aviator;
	// 规则匹配缓存
	requires com.github.benmanes.caffeine;
	// YAML 规则加载是可选能力：编译期需要，运行期由使用方按需提供
	requires static org.yaml.snakeyaml;

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
	exports org.dromara.mica.mqtt.broker.rule.ext.aviator;
	exports org.dromara.mica.mqtt.broker.rule.ext.template;
	exports org.dromara.mica.mqtt.broker.rule.loader;
	exports org.dromara.mica.mqtt.broker.rule.matcher;
	exports org.dromara.mica.mqtt.broker.rule.metrics;
	exports org.dromara.mica.mqtt.broker.rule.action;
	exports org.dromara.mica.mqtt.broker.rule.store;
}
