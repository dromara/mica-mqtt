/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.dromara.mica.mqtt.codec.exception;

/**
 * A {@link MqttUnacceptableProtocolVersionException} which is thrown when
 * a CONNECT request contains unacceptable protocol version.
 *
 * @author netty
 */
public final class MqttUnacceptableProtocolVersionException extends DecoderException {
	private static final long serialVersionUID = 4914652213232455749L;

	/**
	 * Creates a new instance
	 */
	public MqttUnacceptableProtocolVersionException() {
	}

	/**
	 * Creates a new instance
	 *
	 * @param message message
	 * @param cause   Throwable
	 */
	public MqttUnacceptableProtocolVersionException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates a new instance
	 *
	 * @param message message
	 */
	public MqttUnacceptableProtocolVersionException(String message) {
		super(message);
	}

	/**
	 * Creates a new instance
	 *
	 * @param cause Throwable
	 */
	public MqttUnacceptableProtocolVersionException(Throwable cause) {
		super(cause);
	}

}
