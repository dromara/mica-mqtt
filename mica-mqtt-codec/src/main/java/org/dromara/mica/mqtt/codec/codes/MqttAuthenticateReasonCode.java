package org.dromara.mica.mqtt.codec.codes;

/**
 * Utilities for MQTT message codes enums
 *
 * @author vertx-mqtt
 */
public enum MqttAuthenticateReasonCode implements MqttReasonCode {

	SUCCESS((byte) 0x0),

	CONTINUE_AUTHENTICATION((byte) 0x18),

	RE_AUTHENTICATE((byte) 0x19);

	private final byte byteValue;

	MqttAuthenticateReasonCode(byte byteValue) {
		this.byteValue = byteValue;
	}

	public static MqttAuthenticateReasonCode valueOf(byte b) {
		if (b == SUCCESS.byteValue) {
			return SUCCESS;
		} else if (b == CONTINUE_AUTHENTICATION.byteValue) {
			return CONTINUE_AUTHENTICATION;
		} else if (b == RE_AUTHENTICATE.byteValue) {
			return RE_AUTHENTICATE;
		} else {
			throw new IllegalArgumentException("unknown AUTHENTICATE reason code: " + b);
		}
	}

	@Override
	public byte value() {
		return byteValue;
	}
}
