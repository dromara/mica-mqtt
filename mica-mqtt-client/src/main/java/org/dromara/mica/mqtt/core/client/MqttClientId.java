package org.dromara.mica.mqtt.core.client;

import org.tio.core.intf.TioUuid;

/**
 * 将 mqtt clientId 绑定到 context 中
 *
 * @author L.cm
 */
public class MqttClientId implements TioUuid {
	private final MqttClientCreator creator;

	public MqttClientId(MqttClientCreator creator) {
		this.creator = creator;
	}

	@Override
	public String uuid() {
		return creator.getClientId();
	}
}
