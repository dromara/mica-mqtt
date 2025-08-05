package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.dromara.mica.mqtt.codec.message.MqttConnectMessage;
import org.dromara.mica.mqtt.codec.message.header.MqttConnectVariableHeader;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.dromara.mica.mqtt.codec.message.payload.MqttConnectPayload;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;

public final class ConnectBuilder {

	private MqttVersion version = MqttVersion.MQTT_3_1_1;
	private String clientId;
	private boolean cleanStart;
	private boolean hasUser;
	private boolean hasPassword;
	private int keepAliveSecs;
	private MqttProperties willProperties = MqttProperties.NO_PROPERTIES;
	private boolean willFlag;
	private boolean willRetain;
	private MqttQoS willQos = MqttQoS.QOS0;
	private String willTopic;
	private byte[] willMessage;
	private String username;
	private byte[] password;
	private MqttProperties properties = MqttProperties.NO_PROPERTIES;

	ConnectBuilder() {
	}

	public ConnectBuilder protocolVersion(MqttVersion version) {
		this.version = version;
		return this;
	}

	public ConnectBuilder clientId(String clientId) {
		this.clientId = clientId;
		return this;
	}

	public ConnectBuilder cleanStart(boolean cleanStart) {
		this.cleanStart = cleanStart;
		return this;
	}

	public ConnectBuilder keepAlive(int keepAliveSecs) {
		this.keepAliveSecs = keepAliveSecs;
		return this;
	}

	public ConnectBuilder willFlag(boolean willFlag) {
		this.willFlag = willFlag;
		return this;
	}

	public ConnectBuilder willQoS(MqttQoS willQos) {
		this.willQos = willQos;
		return this;
	}

	public ConnectBuilder willTopic(String willTopic) {
		this.willTopic = willTopic;
		return this;
	}

	public ConnectBuilder willMessage(byte[] willMessage) {
		this.willMessage = willMessage;
		return this;
	}

	public ConnectBuilder willRetain(boolean willRetain) {
		this.willRetain = willRetain;
		return this;
	}

	public ConnectBuilder willProperties(MqttProperties willProperties) {
		this.willProperties = willProperties;
		return this;
	}

	public ConnectBuilder hasUser(boolean value) {
		this.hasUser = value;
		return this;
	}

	public ConnectBuilder hasPassword(boolean value) {
		this.hasPassword = value;
		return this;
	}

	public ConnectBuilder username(String username) {
		this.hasUser = username != null;
		this.username = username;
		return this;
	}

	public ConnectBuilder password(byte[] password) {
		this.hasPassword = password != null;
		this.password = password;
		return this;
	}

	public ConnectBuilder properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttConnectMessage build() {
		MqttFixedHeader mqttFixedHeader =
			new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.QOS0, false, 0);
		MqttConnectVariableHeader mqttConnectVariableHeader =
			new MqttConnectVariableHeader(
				version.protocolName(),
				version.protocolLevel(),
				hasUser,
				hasPassword,
				willRetain,
				willQos.value(),
				willFlag,
				cleanStart,
				keepAliveSecs,
				properties);
		MqttConnectPayload mqttConnectPayload =
			new MqttConnectPayload(clientId, willProperties, willTopic, willMessage, username, password);
		return new MqttConnectMessage(mqttFixedHeader, mqttConnectVariableHeader, mqttConnectPayload);
	}
}
