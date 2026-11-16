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
	SUCCESS((byte) 0x0, "成功"),

	/**
	 * Continue Authentication
	 */
	CONTINUE_AUTHENTICATION((byte) 0x18, "继续认证"),

	/**
	 * Re-Authenticate
	 */
	RE_AUTHENTICATE((byte) 0x19, "重新认证");

	private final byte byteValue;
	private final String message;

	MqttAuthReasonCode(byte byteValue, String message) {
		this.byteValue = byteValue;
		this.message = message;
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

	@Override
	public String toString() {
		return "[0x" + String.format("%02X", this.byteValue & 0xFF) + "] " + this.name().toLowerCase().replace('_', ' ') + " (" + this.message + ')';
	}
}
