package org.dromara.mica.mqtt.codec.codes;

/**
 * Utilities for MQTT message codes enums
 *
 * @author vertx-mqtt
 */
public enum MqttAuthReasonCode implements MqttReasonCode {

	/**
	 * Success
	 */
	SUCCESS((byte) 0x0),

	/**
	 * Continue Authentication
	 */
	CONTINUE_AUTHENTICATION((byte) 0x18),

	/**
	 * Re-Authenticate
	 */
	RE_AUTHENTICATE((byte) 0x19);

	private final byte byteValue;

	MqttAuthReasonCode(byte byteValue) {
		this.byteValue = byteValue;
	}

	public static MqttAuthReasonCode valueOf(byte code) {
		if (code == SUCCESS.byteValue) {
			return SUCCESS;
		} else if (code == CONTINUE_AUTHENTICATION.byteValue) {
			return CONTINUE_AUTHENTICATION;
		} else if (code == RE_AUTHENTICATE.byteValue) {
			return RE_AUTHENTICATE;
		} else {
			throw new IllegalArgumentException("unknown AUTHENTICATE reason code: " + code);
		}
	}

	@Override
	public byte value() {
		return byteValue;
	}
}
