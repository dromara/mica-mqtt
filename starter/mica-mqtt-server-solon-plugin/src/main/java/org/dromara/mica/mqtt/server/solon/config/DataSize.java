package org.dromara.mica.mqtt.server.solon.config;

/**
 * DataSize 兼容 - 重用 mica-mqtt-common 中的实现
 *
 * @author L.cm
 * @deprecated 请直接使用 {@link org.dromara.mica.mqtt.core.util.DataSize}
 */
@Deprecated
class DataSize extends org.dromara.mica.mqtt.core.util.DataSize {

	public DataSize(long bytes) {
		super(bytes);
	}

}
