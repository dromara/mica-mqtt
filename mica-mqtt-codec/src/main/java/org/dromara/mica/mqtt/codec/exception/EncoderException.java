/*
 * Copyright 2012 The Netty Project
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
 * An {@link RuntimeException} which is thrown by an encoder.
 *
 * @author netty
 */
public class EncoderException extends RuntimeException {

	private static final long serialVersionUID = -5086121160476476774L;

	/**
	 * Creates a new instance.
	 */
	public EncoderException() {
	}

	/**
	 * Creates a new instance.
	 *
	 * @param message message
	 * @param cause   Throwable
	 */
	public EncoderException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param message message
	 */
	public EncoderException(String message) {
		super(message);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param cause Throwable
	 */
	public EncoderException(Throwable cause) {
		super(cause);
	}
}
