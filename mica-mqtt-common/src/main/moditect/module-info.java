open module org.dromara.mica.mqtt.common {
	requires transitive net.dreamlu.mica.net.core;
	requires transitive org.dromara.mica.mqtt.codec;
	exports org.dromara.mica.mqtt.core.common;
	exports org.dromara.mica.mqtt.core.util;
	exports org.dromara.mica.mqtt.core.util.timer;
}
